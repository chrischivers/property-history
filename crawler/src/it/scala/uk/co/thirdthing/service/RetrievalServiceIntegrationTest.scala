package uk.co.thirdthing.service

import cats.effect.IO
import cats.implicits._
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveHtmlClient}
import uk.co.thirdthing.clients.RightmoveHtmlClient.RightmoveHtmlScrapeResult
import uk.co.thirdthing.model.Model.{ListingStatus, Property, PropertyDetails, TransactionType}

import java.time.Instant
import scala.concurrent.duration._
import scala.util.Random

class RetrievalServiceIntegrationTest extends munit.CatsEffectSuite {

  override def munitTimeout: Duration = 5.minutes

  test("Scrape the correct result") {


    val listingId = ListingId(124999760)
    val expectedResult = Property(
      listingId = listingId,
      propertyId = PropertyId(81536734),
      dateAdded = DateAdded(Instant.ofEpochMilli(1657875302000L)),
      details = PropertyDetails(
        price = Price(315000),
        transactionTypeId = TransactionType.Sale,
        visible = false,
        status = ListingStatus.Hidden,
        rentFrequency = None,
        latitude = 53.05996,
        longitude = -2.195873
      )
    )
    buildService(service => assertIO(service.retrieve(listingId), expectedResult.some))
  }

  test("Conduct random bulk test") {
    val listingIds = (0 to 100).toList.map(_ => Random.nextInt(108238283)).map(ListingId(_))

    buildService(service => assertIO(listingIds.traverse(service.retrieve).void, ()))
  }

  def buildService(f: RetrievalService[IO] => IO[Unit]) =
    EmberClientBuilder
      .default[IO]
      .build
      .map{client =>
        val apiClient = RightmoveApiClient.apply[IO](client, Uri.unsafeFromString("https://api.rightmove.co.uk"))
        val htmlClient = RightmoveHtmlClient.apply[IO](client, Uri.unsafeFromString("https://www.rightmove.co.uk"))
        RetrievalService.apply[IO](apiClient, htmlClient)
      }
      .use(f)

}
