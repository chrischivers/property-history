package uk.co.thirdthing.routes
import cats.effect.IO
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId}
import org.http4s.circe._
import org.http4s.{EntityDecoder, HttpRoutes}
import uk.co.thirdthing.model.Model
import uk.co.thirdthing.model.Model.RightmoveListing
import uk.co.thirdthing.service.HistoryService

import java.time.Instant

class ApiRouteTest extends munit.Http4sHttpRoutesSuite {

  implicit val entityDecoder: EntityDecoder[IO, List[RightmoveListing]] = jsonOf

  val listingId = ListingId(12345678)
  val dateAdded  = DateAdded(Instant.ofEpochMilli(1658264481000L))
  val rightmoveListing = RightmoveListing(listingId, "some-url", dateAdded)

  val historyServiceMock = new HistoryService[IO] {
    override def historyFor(id: ListingId): fs2.Stream[IO, Model.RightmoveListing] = fs2.Stream.emit[IO, RightmoveListing](rightmoveListing)
  }

  override val routes: HttpRoutes[IO] = ApiRoute.routes(historyServiceMock)

  test(GET(uri"v1" / "history" / listingId.value)).alias("Get history for a listing id") { response =>
    assertIO(response.as[List[RightmoveListing]], List(rightmoveListing))
  }
}
