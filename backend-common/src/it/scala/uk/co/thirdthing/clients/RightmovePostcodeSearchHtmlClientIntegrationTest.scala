package uk.co.thirdthing.clients

import cats.effect.IO
import cats.implicits.*
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.clients.RightmovePostcodeSearchHtmlClient.RightmovePostcodeSearchResult

import scala.concurrent.duration.*
import scala.util.Random

class RightmovePostcodeSearchHtmlClientIntegrationTest extends munit.CatsEffectSuite:

  override def munitTimeout: Duration = 5.minutes

  test("Scrape the correct result") {
    val postcode                = Postcode("DE12 8HJ")
    val expectedNumberOfResults = 19
    buildClient(client => assertIO(client.scrapeDetails(postcode).map(_.size), expectedNumberOfResults))
  }

  test("Conduct random bulk test to ensure no parsing issues") {
    val postcodes = List(
      "DN35 8LB",
      "IP12 9SD",
      "DH7 8SW",
      "WA12 9NX",
      "HU18 1HH",
      "TD11 3QR",
      "SY20 9QY",
      "SL4 5SF",
      "GU16 6PS",
      "NG19 0LW"
    ).map(Postcode(_))

    postcodes.traverse { postcode =>
      buildClient(_.scrapeDetails(postcode).flatMap(results => IO.println(s"results returned for ${postcode.value} ${results.size}")))
    }

  }

  def buildClient(f: RightmovePostcodeSearchHtmlClient[IO] => IO[Unit]) =
    BlazeClientBuilder[IO].resource
      .map(client =>
        RightmovePostcodeSearchHtmlClient.apply[IO](client, Uri.unsafeFromString("https://www.rightmove.co.uk"))
      )
      .use(f)
