package uk.co.thirdthing.routes
import cats.effect.IO
import cats.syntax.all.*
import org.http4s.circe.*
import org.http4s.{EntityDecoder, HttpRoutes}
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.{PropertyLookupService, ThumbnailService}
import uk.co.thirdthing.utils.Generators.*

import java.nio.file.Paths

class ApiRouteTest extends munit.Http4sHttpRoutesSuite:

  private val thumbnailFromResources = fs2.io.file.Path(getClass.getResource("/simple-house-silhouette.jpg").getPath)

  private given EntityDecoder[IO, PropertyLookupDetails] = jsonOf

  private val fullAddress     = FullAddress("1 Elm Road")
  private val postcode        = Postcode("PL3 34L")
  private val listingSnapshot = listingSnapshotGen.sample.get
  private val transactions    = transactionGen.sample.get
  private val propertyLookupDetails =
    PropertyLookupDetails(fullAddress.some, postcode.some, List(listingSnapshot), List(transactions))

  private val lookupServiceMock = new PropertyLookupService[IO]:
    override def detailsFor(id: ListingId): IO[Option[PropertyLookupDetails]] =
      propertyLookupDetails.some.pure[IO]

  val thumbnailServiceMock = new ThumbnailService[IO]:
    override def thumbnailFor(id: ListingId): fs2.Stream[IO, Byte] =
      fs2.io.file.Files[IO].readAll(thumbnailFromResources)
    override def thumbnailFor(url: ThumbnailUrl): fs2.Stream[IO, Byte] =
      fs2.io.file.Files[IO].readAll(thumbnailFromResources)

  override val routes: HttpRoutes[IO] = ApiRoute.routes(lookupServiceMock, thumbnailServiceMock)

  test(GET(uri"history" / listingSnapshot.listingId.value)).alias("Get history for a listing id") { response =>
    val resp = response.as[PropertyLookupDetails]
    assertIO(resp, propertyLookupDetails)
  }
