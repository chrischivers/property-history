package uk.co.thirdthing.service

import cats.Applicative
import cats.effect.{Clock, IO, ParallelF, Ref}
import cats.syntax.all.*
import uk.co.thirdthing.clients.RightmovePostcodeSearchHtmlClient
import uk.co.thirdthing.clients.RightmovePostcodeSearchHtmlClient.RightmovePostcodeSearchResult
import uk.co.thirdthing.model.Model.*
import uk.co.thirdthing.model.Model.CrawlerJob.LastRunCompleted
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.service.RetrievalService.RetrievalResult
import uk.co.thirdthing.utils.{MockAddressStore, MockJobStore, MockPropertyStore, NoOpMetricsRecorder}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

class UpdateAddressDetailsServiceTest extends munit.CatsEffectSuite:

  private val postcode = Postcode("AR3 1PG")

  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val staticClock = new Clock[IO]:
    override def applicative: Applicative[IO] = implicitly

    override def monotonic: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]

    override def realTime: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]

  test("Run a postcode job successfully when no results returned") {

    for
      addressRecords       <- Ref.of[IO, List[AddressDetails]](List.empty)
      propertyStoreRecords <- Ref.of[IO, Map[(ListingId, LastChange), ListingSnapshot]](Map.empty)
      service              <- buildService(addressRecords, propertyStoreRecords, Set.empty)
      _                    <- service.run(postcode)
      _                    <- assertIO(addressRecords.get, List.empty)
    yield ()
  }

  test("Run a postcode job successfully when results returned, but no listing ID") {
    for
      addressRecords       <- Ref.of[IO, List[AddressDetails]](List.empty)
      propertyStoreRecords <- Ref.of[IO, Map[(ListingId, LastChange), ListingSnapshot]](Map.empty)
      recordsFromClient = Set(RightmovePostcodeSearchResult(FullAddress("Blah"), postcode, None, List.empty))
      service <- buildService(addressRecords, propertyStoreRecords, recordsFromClient)
      _       <- service.run(postcode)
      _       <- assertIO(addressRecords.get, List(AddressDetails(FullAddress("Blah"), postcode, None, List.empty)))
    yield ()
  }

  test("Run a postcode job successfully when results returned, but listingId does not exist in propertyStore") {
    for
      addressRecords       <- Ref.of[IO, List[AddressDetails]](List.empty)
      propertyStoreRecords <- Ref.of[IO, Map[(ListingId, LastChange), ListingSnapshot]](Map.empty)
      recordsFromClient = Set(
        RightmovePostcodeSearchResult(FullAddress("Blah"), postcode, Some(ListingId(1234)), List.empty)
      )
      service <- buildService(addressRecords, propertyStoreRecords, recordsFromClient)
      _       <- service.run(postcode)
      _       <- assertIO(addressRecords.get, List(AddressDetails(FullAddress("Blah"), postcode, None, List.empty)))
    yield ()
  }

  test("Run a postcode job successfully when results returned, and listingId exists in propertyStore") {
    val listingId  = ListingId(1234)
    val propertyId = PropertyId(8764)
    for
      addressRecords <- Ref.of[IO, List[AddressDetails]](List.empty)
      propertyStoreRecords <- Ref.of[IO, Map[(ListingId, LastChange), ListingSnapshot]](
        Map(
          (listingId, LastChange(now)) -> ListingSnapshot(
            listingId,
            LastChange(now),
            propertyId,
            DateAdded(now),
            PropertyDetails.Empty,
            None
          )
        )
      )
      recordsFromClient = Set(RightmovePostcodeSearchResult(FullAddress("Blah"), postcode, Some(listingId), List.empty))
      service <- buildService(addressRecords, propertyStoreRecords, recordsFromClient)
      _       <- service.run(postcode)
      _ <- assertIO(
        addressRecords.get,
        List(AddressDetails(FullAddress("Blah"), postcode, Some(propertyId), List.empty))
      )
    yield ()
  }

  def buildService(
    initialAddressRecords: Ref[IO, List[AddressDetails]],
    initialPropertyStoreRecords: Ref[IO, Map[(ListingId, LastChange), ListingSnapshot]],
    recordsRetrievedFromClient: Set[RightmovePostcodeSearchResult]
  ): IO[UpdateAddressDetailsService[IO]] =

    val mockClient = new RightmovePostcodeSearchHtmlClient[IO]:
      override def scrapeDetails(postcode: Postcode): IO[Set[RightmovePostcodeSearchResult]] =
        recordsRetrievedFromClient.filter(_.postcode == postcode).pure[IO]

    for
      addressStore  <- MockAddressStore(initialAddressRecords)
      propertyStore <- MockPropertyStore(initialPropertyStoreRecords)
    yield UpdateAddressDetailsService.apply(addressStore, propertyStore, mockClient, NoOpMetricsRecorder.apply)(
      implicitly,
      staticClock
    )
