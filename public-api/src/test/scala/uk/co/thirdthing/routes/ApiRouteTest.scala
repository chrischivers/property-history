package uk.co.thirdthing.routes
import cats.effect.IO
import org.http4s.circe.*
import org.http4s.{EntityDecoder, HttpRoutes}
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.{HistoryService, ThumbnailService}
import uk.co.thirdthing.utils.Generators.listingSnapshotGen

import java.nio.file.Paths

class ApiRouteTest extends munit.Http4sHttpRoutesSuite:

  private val thumbnailFromResources = fs2.io.file.Path(getClass.getResource("/simple-house-silhouette.jpg").getPath)

  implicit val entityDecoder: EntityDecoder[IO, List[ListingSnapshot]] = jsonOf

  val listingSnapshot = listingSnapshotGen.sample.get

  val historyServiceMock = new HistoryService[IO] {
    override def historyFor(id: ListingId): fs2.Stream[IO, ListingSnapshot] = fs2.Stream.emit[IO, ListingSnapshot](listingSnapshot)
  }

  val thumbnailServiceMock = new ThumbnailService[IO] {
    override def thumbnailFor(id: ListingId): fs2.Stream[IO, Byte]     = fs2.io.file.Files[IO].readAll(thumbnailFromResources)
    override def thumbnailFor(url: ThumbnailUrl): fs2.Stream[IO, Byte] = fs2.io.file.Files[IO].readAll(thumbnailFromResources)
  }

  override val routes: HttpRoutes[IO] = ApiRoute.routes(historyServiceMock, thumbnailServiceMock)

  test(GET(uri"history" / listingSnapshot.listingId.value)).alias("Get history for a listing id") { response =>
    val records = response.asJson.flatMap(j => IO.fromEither(j.hcursor.downField("records").as[List[ListingSnapshot]]))
    assertIO(records, List(listingSnapshot))
  }
