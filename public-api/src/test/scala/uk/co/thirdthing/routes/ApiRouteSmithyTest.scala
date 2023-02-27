package uk.co.thirdthing.routes
import cats.effect.IO
import cats.syntax.all.*
import org.http4s.circe.*
import org.http4s.{EntityDecoder, HttpRoutes}
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.utils.Generators.{listingSnapshotGen, transactionGen}
import smithy4s.http4s.SimpleRestJsonBuilder
import uk.co.thirdthing.service.PropertyLookupService

class ApiRouteSmithyTest extends munit.Http4sHttpRoutesSuite:

  given EntityDecoder[IO, PropertyLookupDetails] = jsonOf

  private val fullAddress     = FullAddress("1 Elm Road")
  private val postcode        = Postcode("PL3 34L")
  private val listingSnapshot = listingSnapshotGen.sample.get
  private val transactions    = transactionGen.sample.get
  private val propertyLookupDetails   = PropertyLookupDetails(fullAddress.some, postcode.some, List(listingSnapshot), List(transactions))

  private val lookupServiceMock = new PropertyLookupService[IO]:
    override def detailsFor(id: ListingId): IO[Option[PropertyLookupDetails]] =
      propertyLookupDetails.some.pure[IO]

  override val routes: HttpRoutes[IO] =
    SimpleRestJsonBuilder.routes(ApiRouteSmithy(lookupServiceMock)).make.getOrElse(fail("bang"))

  test(GET(uri"history" / listingSnapshot.listingId.value)).alias("Get history for a listing id") { response =>
    val resp = response.as[PropertyLookupDetails]
    assertIO(resp, propertyLookupDetails)
  }
