package uk.co.thirdthing.clients

import cats.effect.IO
import cats.implicits.*
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.clients.RightmoveListingHtmlClient.RightmoveHtmlScrapeResult

import scala.concurrent.duration.*
import scala.util.Random

class RightmoveListingHtmlClientIntegrationTest extends munit.CatsEffectSuite:

  override def munitTimeout: Duration = 5.minutes

  test("Scrape the correct result") {

    val expectedResult = RightmoveHtmlScrapeResult(410, None)
    buildClient(client => assertIO(client.scrapeDetails(ListingId(104510633)), expectedResult))
  }

  test("Conduct random bulk test") {
    val listingIds = (0 to 10).toList.map(_ => Random.nextInt(108238283)).map(ListingId(_))

    buildClient(client => assertIO(listingIds.traverse(client.scrapeDetails).void, ()))
  }

  def buildClient(f: RightmoveListingHtmlClient[IO] => IO[Unit]) =
    BlazeClientBuilder[IO].resource
      .map(client =>
        RightmoveListingHtmlClient.apply[IO](client, Uri.unsafeFromString("https://www.rightmove.co.uk"))
      )
      .use(f)
