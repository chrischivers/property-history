package uk.co.thirdthing.clients

import cats.effect.IO
import cats.implicits._
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.clients.RightmoveHtmlClient.RightmoveHtmlScrapeResult

import scala.concurrent.duration._
import scala.util.Random

class RightmoveHtmlClientIntegrationTest extends munit.CatsEffectSuite {

  override def munitTimeout: Duration = 5.minutes

  test("Scrape the correct result") {

    val expectedResult = RightmoveHtmlScrapeResult(410, Some(PropertyId(81536734)))
    buildClient(client => assertIO(client.scrapeDetails(ListingId(104510633)), expectedResult))
  }

  test("Conduct random bulk test") {
    val listingIds = (0 to 100).toList.map(_ => Random.nextInt(108238283)).map(ListingId(_))

    buildClient(client => assertIO(listingIds.traverse(client.scrapeDetails).void, ()))
  }

  def buildClient(f: RightmoveHtmlClient[IO] => IO[Unit]) =
    BlazeClientBuilder[IO].resource
      .map(client => RightmoveHtmlClient.apply[IO](client, Uri.unsafeFromString("https://www.rightmove.co.uk")))
      .use(f)

}
