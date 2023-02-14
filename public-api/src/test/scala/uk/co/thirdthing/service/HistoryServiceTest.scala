package uk.co.thirdthing.service

import cats.Applicative
import cats.effect.{Clock, IO}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.RetrievalService.RetrievalResult
import uk.co.thirdthing.store.PropertyStore
import uk.co.thirdthing.utils.Generators.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.*

class HistoryServiceTest extends ScalaCheckSuite:

  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private implicit val staticClock: Clock[IO] = new Clock[IO] {
    override def applicative: Applicative[IO] = implicitly

    override def monotonic: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]

    override def realTime: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]
  }

  private def propertyStoreMock(
    rightmovePropertyId: Option[PropertyId],
    listings: List[ListingSnapshot]
  ): PropertyStore[IO] = new PropertyStore[IO] {
    override def propertyIdFor(listingId: ListingId): IO[Option[PropertyId]] = IO(rightmovePropertyId)

    override def latestListingsFor(propertyId: PropertyId): fs2.Stream[IO, ListingSnapshot] =
      fs2.Stream.emits[IO, ListingSnapshot](listings)

    override def putListingSnapshot(listingSnapshot: ListingSnapshot): IO[Unit] = fail("should not be called")

    override def getMostRecentListing(listingId: ListingId): IO[Option[ListingSnapshot]] = fail("should not be called")
  }

  private def retrievalServiceMock(retrievalResult: Option[RetrievalResult]): RetrievalService[IO] =
    new RetrievalService[IO]:
      override def retrieve(listingId: ListingId): IO[Option[RetrievalResult]] = retrievalResult.pure[IO]

  private def listingSnapshotFrom(result: RetrievalResult): ListingSnapshot =
    ListingSnapshot(result.listingId, LastChange(now), result.propertyId, DateAdded(now), result.propertyDetails)

  property("History is successfully retrieved") {
    forAll {
      (propertyId: Option[PropertyId], listings: List[ListingSnapshot], retrievalResult: Option[RetrievalResult]) =>
        val propertyStore    = propertyStoreMock(propertyId, listings)
        val retrievalService = retrievalServiceMock(retrievalResult)
        val historyService   = HistoryService.apply[IO](propertyStore, retrievalService)
        val results          = historyService.historyFor(ListingId(123)).compile.toList.unsafeRunSync()

        (propertyId, retrievalResult) match {
          case (None, None)         => assertEquals(results, List.empty)
          case (Some(_), _)         => assertEquals(results, listings)
          case (None, Some(result)) => assertEquals(results, listingSnapshotFrom(result) +: listings)
        }
    }
  }
