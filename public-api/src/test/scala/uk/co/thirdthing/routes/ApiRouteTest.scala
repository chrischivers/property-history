package uk.co.thirdthing.routes
import cats.effect.IO
import org.http4s.circe._
import org.http4s.{EntityDecoder, HttpRoutes}
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.service.HistoryService
import uk.co.thirdthing.utils.Hasher.Hash

import java.time.Instant

class ApiRouteTest extends munit.Http4sHttpRoutesSuite {

  implicit val entityDecoder: EntityDecoder[IO, List[PropertyListing]] = jsonOf

  val listingId       = ListingId(12345678)
  val propertyId      = PropertyId(75830922)
  val dateAdded       = DateAdded(Instant.ofEpochMilli(1658264481000L))
  val propertyListing = PropertyListing(listingId, propertyId, dateAdded, ListingSnapshotId.generate, Hash("blah"))

  val historyServiceMock = new HistoryService[IO] {
    override def historyFor(id: ListingId): fs2.Stream[IO, PropertyListing] = fs2.Stream.emit[IO, PropertyListing](propertyListing)
  }

  override val routes: HttpRoutes[IO] = ApiRoute.routes(historyServiceMock)

  test(GET(uri"v1" / "history" / listingId.value)).alias("Get history for a listing id") { response =>
    assertIO(response.as[List[PropertyListing]], List(propertyListing))
  }
}
