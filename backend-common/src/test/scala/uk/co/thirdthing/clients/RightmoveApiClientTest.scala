package uk.co.thirdthing.clients

import cats.effect.IO
import cats.syntax.all._
import fs2.io.file.{Path => Fs2Path}
import uk.co.thirdthing.model.Types._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, QueryParamDecoder, StaticFile, Status, Uri}
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails

class RightmoveApiClientTest extends munit.CatsEffectSuite {

  val listingId: ListingId = ListingId(12345678)

  test("Decode the api success response") {

    val expectedListingDetails = ListingDetails(
      price = Price(315000),
      transactionTypeId = TransactionType.Sale,
      visible = true,
      status = None,
      sortDate = 1657875302000L.some,
      updateDate = 1658237961000L,
      rentFrequency = None,
      publicsiteUrl = Uri.unsafeFromString("https://www.rightmove.co.uk/property-for-sale/property-124999760.html"),
      latitude = 53.060074.some,
      longitude = -2.195828.some,
      photoThumbnailUrl = ThumbnailUrl("https://media.rightmove.co.uk/19k/18654/124999760/18654_11600008_IMG_00_0000.jpeg").some
    )

    assertIO(apiClient("/rightmove-api-success-response.json").listingDetails(listingId), Some(expectedListingDetails))
  }

  test("Decode the api failure response") {
    assertIO(apiClient("/rightmove-api-failure-response.json").listingDetails(listingId), None)
  }

  test("Decode the api 500 response") {
    assertIO(apiClient("/rightmove-api-500-response.json", status = InternalServerError).listingDetails(listingId), None)
  }

  def apiClient(responsePath: String, status: Status = Status.Ok): RightmoveApiClient[IO] = {

    implicit val yearQueryParamDecoder: QueryParamDecoder[ListingId] = QueryParamDecoder[Long].map(ListingId.apply)
    object ListingIdQueryParamMatcher      extends QueryParamDecoderMatcher[ListingId]("propertyId")
    object ApiApplicationQueryParamMatcher extends QueryParamDecoderMatcher[String]("apiApplication")

    RightmoveApiClient.apply[IO](
      Client.fromHttpApp[IO](
        HttpRoutes
          .of[IO] {
            case request @ GET -> Root / "api" / "propertyDetails" :? ListingIdQueryParamMatcher(_) :? ApiApplicationQueryParamMatcher("IPAD") =>
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
