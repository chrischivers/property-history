package uk.co.thirdthing.service

import cats.effect.IO
import cats.syntax.all.*
import fs2.io.file.Path as Fs2Path
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, StaticFile, Uri}
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveListingHtmlClient}
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.PropertyScrapingService.ScrapeResult

import java.time.Instant

class RetrievalServiceTest extends munit.CatsEffectSuite:

  val listingId: ListingId = ListingId(12345678)

  test("Scrape the data from the client successfully") {

    val expectedResult = ScrapeResult(
      listingId = listingId,
      propertyId = PropertyId(72291262),
      dateAdded = DateAdded(Instant.ofEpochMilli(1657875302000L)),
      propertyDetails = PropertyDetails(
        price = Price(315000).some,
        transactionTypeId = TransactionType.Sale.some,
        visible = true.some,
        status = ListingStatus.Unknown.some,
        rentFrequency = None,
        latitude = 53.060074.some,
        longitude = -2.195828.some,
        thumbnailUrl =
          ThumbnailUrl("https://media.rightmove.co.uk/19k/18654/124999760/18654_11600008_IMG_00_0000.jpeg").some
      )
    )

    assertIO(
      service("/rightmove-html-success-response.html", "/rightmove-api-success-response.json").scrape(listingId),
      expectedResult.some
    )
  }

  def service(htmlClientResponse: String, apiClientResponse: String): PropertyScrapingService[IO] =

    val apiClient: RightmoveApiClient[IO] = RightmoveApiClient.apply[IO](
      Client.fromHttpApp[IO](
        HttpRoutes
          .of[IO] { case request @ GET -> Root / "api" / "propertyDetails" =>
            StaticFile
              .fromPath(Fs2Path(getClass.getResource(apiClientResponse).getPath), Some(request))
              .getOrElseF(NotFound())

          }
          .orNotFound
      ),
      Uri.unsafeFromString("/")
    )

    val htmlClient = RightmoveListingHtmlClient.apply[IO](
      Client.fromHttpApp[IO](
        HttpRoutes
          .of[IO] { case request @ GET -> Root / "properties" / _ =>
            StaticFile
              .fromPath(Fs2Path(getClass.getResource(htmlClientResponse).getPath), Some(request))
              .getOrElseF(NotFound())
          }
          .orNotFound
      ),
      Uri.unsafeFromString("/")
    )
    PropertyScrapingService.apply[IO](apiClient, htmlClient)
