package uk.co.thirdthing.service

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import munit.ScalaCheckSuite
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.scalacheck.Prop.forAll
import uk.co.thirdthing.clients.RightmoveApiClient
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.store.PropertyStore
import uk.co.thirdthing.utils.Generators
import uk.co.thirdthing.utils.Generators.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import uk.co.thirdthing.service.ThumbnailService.NoThumbnailAvailable

class ThumbnailServiceTest extends munit.CatsEffectSuite:

  private val returnedData = "Some stuff"

  private def apiClientMock(details: Map[ListingId, ListingDetails]) = new RightmoveApiClient[IO] {
    override def listingDetails(listingId: ListingId): IO[Option[ListingDetails]] =
      details.get(listingId).pure[IO]
  }

  private def httpClientMock(url: String) = Client.fromHttpApp(
    HttpRoutes
      .of[IO] {
        case r @ GET -> _ if r.uri.renderString == url =>
          Ok(returnedData)
      }
      .orNotFound
  )

  test("Thumbnail is successfully retrieved when thumbnailUrl is present in the listing, without calling api client") {
    val thumbnailUrl = Generators.thumbnailUrlGen.sample.get
    val apiClient    = apiClientMock(Map.empty)
    val httpClient   = httpClientMock(thumbnailUrl.value)
    val service      = ThumbnailService[IO](apiClient, httpClient)
    assertIO(service.thumbnailFor(thumbnailUrl).compile.toList, returnedData.getBytes.toList)
  }

  test("Thumbnail is successfully retrieved when thumbnailUrl is not present and api client is called") {
    val thumbnailUrl   = Generators.thumbnailUrlGen.sample.get
    val listingId      = Generators.listingIdGen.sample.get
    val listingDetails = Generators.listingDetailsGen.sample.get.copy(photoThumbnailUrl = thumbnailUrl.some)
    val apiClient      = apiClientMock(Map(listingId -> listingDetails))
    val httpClient     = httpClientMock(thumbnailUrl.value)
    val service        = ThumbnailService[IO](apiClient, httpClient)
    assertIO(service.thumbnailFor(listingId).compile.toList, returnedData.getBytes.toList)
  }

  test("Thumbnail call fails if there is no thumbnail url") {
    val thumbnailUrl = Generators.thumbnailUrlGen.sample.get
    val listingId = Generators.listingIdGen.sample.get
    val apiClient = apiClientMock(Map.empty)
    val httpClient = httpClientMock(thumbnailUrl.value)
    val service = ThumbnailService[IO](apiClient, httpClient)
    assertIO(service.thumbnailFor(listingId).compile.toList.attempt, Left(NoThumbnailAvailable))
  }
