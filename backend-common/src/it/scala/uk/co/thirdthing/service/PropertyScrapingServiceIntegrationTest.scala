package uk.co.thirdthing.service

import cats.effect.IO
import cats.implicits.*
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveListingHtmlClient}
import uk.co.thirdthing.model.Types.*

import java.time.Instant
import scala.concurrent.duration.*
import scala.util.Random
import uk.co.thirdthing.service.PropertyScrapingService.ScrapeResult

class PropertyScrapingServiceIntegrationTest extends munit.CatsEffectSuite:

  override def munitTimeout: Duration = 5.minutes

  test("Scrape the correct result") {

    val listingId = ListingId(124999760)
    val expectedResult = ScrapeResult(
      listingId = listingId,
      propertyId = PropertyId(81536734),
      dateAdded = DateAdded(Instant.ofEpochMilli(1657875302000L)),
      propertyDetails = PropertyDetails(
        price = Price(315000).some,
        transactionTypeId = TransactionType.Sale.some,
        visible = false.some,
        status = ListingStatus.Hidden.some,
        rentFrequency = None,
        latitude = 53.05996.some,
        longitude = -2.195873.some,
        thumbnailUrl =
          ThumbnailUrl("https://media.rightmove.co.uk/19k/18654/124999760/18654_11600008_IMG_00_0000.jpeg").some
      )
    )
    buildService(service => assertIO(service.scrape(listingId), expectedResult.some))
  }

  test("Conduct random bulk test") {
    val listingIds = (0 to 100).toList.map(_ => Random.nextInt(108238283)).map(ListingId(_))

    buildService(service => assertIO(listingIds.traverse(service.scrape).void, ()))
  }

  def buildService(f: PropertyScrapingService[IO] => IO[Unit]) =
    BlazeClientBuilder[IO].resource
      .map { client =>
        val apiClient = RightmoveApiClient.apply[IO](client, Uri.unsafeFromString("https://api.rightmove.co.uk"))
        val htmlClient =
          RightmoveListingHtmlClient.apply[IO](client, Uri.unsafeFromString("https://www.rightmove.co.uk"))
        PropertyScrapingService.apply[IO](apiClient, htmlClient)
      }
      .use(f)
