package uk.co.thirdthing.service

import cats.Applicative
import cats.effect.{Clock, IO, Ref}
import cats.syntax.all._
import uk.co.thirdthing.model.Model.CrawlerJob.LastRunCompleted
import uk.co.thirdthing.model.Model._
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.service.RetrievalService.RetrievalResult
import uk.co.thirdthing.utils.Hasher.Hash
import uk.co.thirdthing.utils.{Hasher, MockJobStore, MockPropertyListingStore}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

class JobRunnerServiceTest extends munit.CatsEffectSuite {

  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val staticClock = new Clock[IO] {
    override def applicative: Applicative[IO] = implicitly

    override def monotonic: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]

    override def realTime: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]
  }
  private val staticListingSnapshotId = ListingSnapshotId("any")

  private val jobId = JobId(98765)
  private val job1 = CrawlerJob(
    jobId = jobId,
    from = ListingId(0),
    to = ListingId(1000),
    state = JobState.Pending,
    lastRunScheduled = None,
    lastRunCompleted = None,
    lastChange = None
  )

  private val listingId  = ListingId(500)
  private val propertyId = PropertyId(3495732)
  private val retrievalResult1 = RetrievalResult(
    listingId,
    propertyId,
    DateAdded(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
    PropertyDetails(Price(100000), TransactionType.Sale, visible = true, ListingStatus.SoldSTC, Some("weekly"), 100.5.some, 90.1.some)
  )
  private val detailsHash = Hasher.hash[IO, PropertyDetails](retrievalResult1.propertyDetails).unsafeRunSync()

  test("Run a job successfully and update the records") {

    testWith(
      jobId = jobId,
      retrievalServiceResults = Set(retrievalResult1),
      initialJobs = Set(job1),
      initialProperties = Set.empty,
      initialListingSnapshots = Set.empty,
      expectedJobs = Set(job1.copy(lastRunCompleted = LastRunCompleted(now).some, lastChange = LastChange(now).some, state = JobState.Completed)),
      expectedProperties =
        Set(PropertyListing(retrievalResult1.listingId, retrievalResult1.propertyId, retrievalResult1.dateAdded, staticListingSnapshotId, detailsHash)),
      expectedListingSnapshots = Set(
        ListingSnapshot(
          retrievalResult1.listingId,
          LastChange(now),
          retrievalResult1.propertyId,
          retrievalResult1.dateAdded,
          staticListingSnapshotId,
          Some(retrievalResult1.propertyDetails)
        )
      )
    )
  }

  test("Run a job successfully update the records for a record already existing where the hash has changed") {

    val existingProperty =
      PropertyListing(retrievalResult1.listingId, retrievalResult1.propertyId, retrievalResult1.dateAdded, staticListingSnapshotId, Hash("Some hash"))
    val existingSnapshot = ListingSnapshot(
      retrievalResult1.listingId,
      LastChange(now.minus(1, ChronoUnit.DAYS)),
      retrievalResult1.propertyId,
      retrievalResult1.dateAdded,
      staticListingSnapshotId,
      Some(retrievalResult1.propertyDetails)
    )

    testWith(
      jobId = jobId,
      retrievalServiceResults = Set(retrievalResult1),
      initialJobs = Set(job1),
      initialProperties = Set(existingProperty),
      initialListingSnapshots = Set(existingSnapshot),
      expectedJobs = Set(job1.copy(lastRunCompleted = LastRunCompleted(now).some, lastChange = LastChange(now).some, state = JobState.Completed)),
      expectedProperties = Set(existingProperty.copy(detailsChecksum = detailsHash)),
      expectedListingSnapshots = Set(existingSnapshot, existingSnapshot.copy(lastChange = LastChange(now)))
    )
  }

  def testWith(
    jobId: JobId,
    retrievalServiceResults: Set[RetrievalResult],
    initialJobs: Set[CrawlerJob],
    initialProperties: Set[PropertyListing],
    initialListingSnapshots: Set[ListingSnapshot],
    expectedJobs: Set[CrawlerJob],
    expectedProperties: Set[PropertyListing],
    expectedListingSnapshots: Set[ListingSnapshot]
  ) = {
    val result = for {
      jobsStoreRef     <- Ref.of[IO, Map[JobId, CrawlerJob]](initialJobs.map(job => job.jobId         -> job).toMap)
      propertyStoreRef <- Ref.of[IO, Map[ListingId, PropertyListing]](initialProperties.map(p => p.listingId -> p).toMap)
      listingHistoryStoreRef <- Ref.of[IO, Map[(ListingId, LastChange), ListingSnapshot]](
                                 initialListingSnapshots.map(ls => (ls.listingId, ls.lastChange) -> ls).toMap
                               )
      jobRunner        <- service(retrievalServiceResults.map(r => r.listingId -> r).toMap, jobsStoreRef, propertyStoreRef, listingHistoryStoreRef)
      _                <- jobRunner.run(jobId)
      jobs             <- jobsStoreRef.get.map(_.view.values.toSet)
      properties       <- propertyStoreRef.get.map(_.view.values.map(_.copy(listingSnapshotId = staticListingSnapshotId)).toSet)
      listingHistories <- listingHistoryStoreRef.get.map(_.view.values.map(_.copy(listingSnapshotId = staticListingSnapshotId)).toSet)
    } yield (jobs, properties, listingHistories)

    assertIO(
      result,
      (expectedJobs, expectedProperties, expectedListingSnapshots)
    )
  }

  def service(
    retrievalServiceResults: Map[ListingId, RetrievalResult],
    jobStoreRef: Ref[IO, Map[JobId, CrawlerJob]],
    propertyStoreRef: Ref[IO, Map[ListingId, PropertyListing]],
    listingHistoryStoreRef: Ref[IO, Map[(ListingId, LastChange), ListingSnapshot]]
  ): IO[JobRunnerService[IO]] = {

    val mockRetrievalService = new RetrievalService[IO] {
      override def retrieve(listingId: ListingId): IO[Option[RetrievalService.RetrievalResult]] = retrievalServiceResults.get(listingId).pure[IO]
    }

    for {
      jobStore            <- MockJobStore(jobStoreRef)
      propertyListingStore <- MockPropertyListingStore(propertyStoreRef, listingHistoryStoreRef)
    } yield JobRunnerService.apply(jobStore, propertyListingStore, mockRetrievalService)(implicitly, staticClock)
  }

}
