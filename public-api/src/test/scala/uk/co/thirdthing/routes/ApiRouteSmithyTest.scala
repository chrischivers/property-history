package uk.co.thirdthing.routes
import cats.effect.IO
import cats.syntax.all._
import org.http4s.circe._
import org.http4s.{EntityDecoder, HttpRoutes}
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.service.HistoryService
import uk.co.thirdthing.utils.Generators.listingSnapshotGen
import smithy4s.http4s.SimpleRestJsonBuilder

class ApiRouteSmithyTest extends munit.Http4sHttpRoutesSuite:

  implicit val entityDecoder: EntityDecoder[IO, List[ListingSnapshot]] = jsonOf

  val listingSnapshot = listingSnapshotGen.sample.get

  val historyServiceMock = new HistoryService[IO] {
    override def historyFor(id: ListingId): fs2.Stream[IO, ListingSnapshot] = fs2.Stream.emit[IO, ListingSnapshot](listingSnapshot)
  }

  override val routes: HttpRoutes[IO] = SimpleRestJsonBuilder.routes(ApiRouteSmithy(historyServiceMock)).make.getOrElse(fail("bang"))

  test(GET(uri"history" / listingSnapshot.listingId.value)).alias("Get history for a listing id") { response =>
   val records = response.asJson.flatMap(j => IO.fromEither(j.hcursor.downField("records").as[List[ListingSnapshot]]))
    assertIO(records, List(listingSnapshot))
  }
