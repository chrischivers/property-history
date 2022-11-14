package uk.co.thirdthing.service

import cats.effect.IO
import cats.syntax.all._
import fs2.io.file.{Path => Fs2Path}
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, StaticFile, Uri}
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveHtmlClient}
import uk.co.thirdthing.model.Model.{ListingStatus, Property, PropertyDetails, TransactionType}
import uk.co.thirdthing.service.RetrievalService.RetrievalResult

import java.time.Instant

class RetrievalServiceTest extends munit.CatsEffectSuite {

  val listingId: ListingId = ListingId(12345678)

  test("Scrape the data from the client successfully") {

    val expectedResult = RetrievalResult(
      listingId = listingId,
      propertyId = PropertyId(72291262),
      dateAdded = DateAdded(Instant.ofEpochMilli(1657875302000L)),
      propertyDetails = PropertyDetails(
        price = Price(315000),
        transactionTypeId = TransactionType.Sale,
        visible = true,
        status = ListingStatus.Unknown,
        rentFrequency = None,
        latitude = 53.060074.some,
        longitude = -2.195828.some
      )
    )

    assertIO(service("/rightmove-html-success-response.html", "/rightmove-api-success-response.json").retrieve(listingId), expectedResult.some)
  }

  def service(htmlClientResponse: String, apiClientResponse: String): RetrievalService[IO] = {

    val apiClient: RightmoveApiClient[IO] = RightmoveApiClient.apply[IO](
      Client.fromHttpApp[IO](
        HttpRoutes
          .of[IO] {
            case request @ GET -> Root / "api" / "propertyDetails" =>
              StaticFile
                .fromPath(Fs2Path(getClass.getResource(apiClientResponse).getPath), Some(request))
                .getOrElseF(NotFound())

          }
          .orNotFound
      ),
      Uri.unsafeFromString("/")
    )

    val htmlClient = RightmoveHtmlClient.apply[IO](
      Client.fromHttpApp[IO](
        HttpRoutes
          .of[IO] {
            case request @ GET -> Root / "properties" / _ =>
              StaticFile
                .fromPath(Fs2Path(getClass.getResource(htmlClientResponse).getPath), Some(request))
                .getOrElseF(NotFound())
          }
          .orNotFound
      ),
      Uri.unsafeFromString("/")
    )
    RetrievalService.apply[IO](apiClient, htmlClient)
  }
}
