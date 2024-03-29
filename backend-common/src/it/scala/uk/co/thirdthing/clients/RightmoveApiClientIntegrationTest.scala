package uk.co.thirdthing.clients

import cats.effect.IO
import uk.co.thirdthing.model.Types.*
import org.http4s.Uri
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import cats.implicits.*
import org.http4s.blaze.client.BlazeClientBuilder

import scala.concurrent.duration.*
import scala.util.Random

class RightmoveApiClientIntegrationTest extends munit.CatsEffectSuite:

  override def munitTimeout: Duration = 5.minutes

  test("Decode the api success response") {

    val expectedListingDetails = ListingDetails(
      price = Price(315000),
      transactionTypeId = TransactionType.Sale,
      visible = false,
      status = None,
      sortDate = 1657875302000L.some,
      updateDate = 1665419375000L,
      rentFrequency = None,
      publicsiteUrl = Uri.unsafeFromString("https://www.rightmove.co.uk/property-for-sale/property-124999760.html"),
      latitude = 53.05996.some,
      longitude = -2.195873.some,
      photoThumbnailUrl =
        ThumbnailUrl("https://media.rightmove.co.uk/19k/18654/124999760/18654_11600008_IMG_00_0000.jpeg").some
    )

    buildClient(client => assertIO(client.listingDetails(ListingId(124999760)), Some(expectedListingDetails)))
  }

  test("Decode the api property not found response") {
    buildClient(client => assertIO(client.listingDetails(ListingId(103894528)), None))
  }

  test("Conduct random bulk test") {
    val listingIds = (0 to 100).toList.map(_ => Random.nextInt(108238283)).map(ListingId(_))

    buildClient(client => assertIO(listingIds.traverse(client.listingDetails).void, ()))
  }

  def buildClient(f: RightmoveApiClient[IO] => IO[Unit]) =
    BlazeClientBuilder[IO].resource
      .map(client => RightmoveApiClient.apply[IO](client, Uri.unsafeFromString("https://api.rightmove.co.uk")))
      .use(f)
