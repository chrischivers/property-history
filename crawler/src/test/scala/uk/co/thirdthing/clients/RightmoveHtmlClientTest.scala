package uk.co.thirdthing.clients

import cats.effect.IO
import fs2.io.file.{Path => Fs2Path}
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, StaticFile, Status, Uri}
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.clients.RightmoveHtmlClient.RightmoveHtmlScrapeResult

class RightmoveHtmlClientTest extends munit.CatsEffectSuite {

  val listingId: ListingId = ListingId(12345678)

  test("Scrape the data from the client successfully") {

    val expectedResult = RightmoveHtmlScrapeResult(200, Some(PropertyId(72291262)))


    assertIO(apiClient("/rightmove-html-success-response.html").scrapeDetails(listingId), expectedResult)
  }

  def apiClient(responsePath: String, status: Status = Status.Ok): RightmoveHtmlClient[IO] = {


    RightmoveHtmlClient.apply[IO](
      Client.fromHttpApp[IO](
        HttpRoutes
          .of[IO] {
            case request @ GET -> Root / "properties" / _ =>
              val response = StaticFile
                .fromPath(Fs2Path(getClass.getResource(responsePath).getPath), Some(request))
                .getOrElseF(NotFound())

              response.map(_.copy(status = status))
          }
          .orNotFound
      ),
      Uri.unsafeFromString("/")
    )
  }

}