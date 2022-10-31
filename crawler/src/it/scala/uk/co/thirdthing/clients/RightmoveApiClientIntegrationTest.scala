package uk.co.thirdthing.clients

import cats.effect.IO
import uk.co.thirdthing.Rightmove.{ListingId, Price}
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import uk.co.thirdthing.model.Model.TransactionType
import cats.implicits._

import scala.concurrent.duration._
import scala.util.Random

class RightmoveApiClientIntegrationTest extends munit.CatsEffectSuite {

  override def munitTimeout: Duration = 5.minutes

  test("Decode the api success response") {

    val expectedListingDetails = ListingDetails(
      price = Price(315000),
      transactionTypeId = TransactionType.Sale,
      visible = false,
      status = None,
      sortDate = 1657875302000L.some,
      updateDate = 1663688404000L,
      rentFrequency = None,
      publicsiteUrl = Uri.unsafeFromString("https://www.rightmove.co.uk/property-for-sale/property-124999760.html"),
      latitude = 53.05996,
      longitude = -2.195873
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
    EmberClientBuilder
      .default[IO]
      .build
      .map(client => RightmoveApiClient.apply[IO](client, Uri.unsafeFromString("https://api.rightmove.co.uk")))
      .use(f)

}
