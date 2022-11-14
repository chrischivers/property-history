package uk.co.thirdthing.service

import cats.Applicative
import cats.effect.{Clock, IO, Ref}
import cats.syntax.all._
import uk.co.thirdthing.model.Model.CrawlerJob.LastRunCompleted
import uk.co.thirdthing.model.Model._
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.service.RetrievalService.RetrievalResult
import uk.co.thirdthing.utils.{MockJobStore, MockPropertyStore, NoOpMetricsRecorder}

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
  private val staticListingSnapshotId = ListingSnapshotId(12345)

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
    PropertyDetails.from(Price(100000), TransactionType.Sale, visible = true, ListingStatus.SoldSTC, "weekly", 100.5, 90.1)
  )

  test("Run a job successfully and update the records") {

    testWith(
      jobId = jobId,
      retrievalServiceResults = Set(retrievalResult1),
      initialJobs = Set(job1),
      initialListingSnapshots = Set.empty,
      expectedJobs = Set(job1.copy(lastRunCompleted = LastRunCompleted(now).some, lastChange = LastChange(now).some, state = JobState.Completed)
      ),
      expectedListingSnapshots = Set(
        ListingSnapshot(
          retrievalResult1.listingId,
          LastChange(now),
          retrievalResult1.propertyId,
          retrievalResult1.dateAdded,
          retrievalResult1.propertyDetails,
          staticListingSnapshotId.some
        )
      )
    )
  }

  test("Run a job successfully update the records for a record already existing where the hash has changed") {

    val existingListingSnapshot =
      ListingSnapshot(retrievalResult1.listingId,       LastChange(now.minus(1, ChronoUnit.DAYS)), retrievalResult1.propertyId, retrievalResult1.dateAdded, retrievalResult1.propertyDetails, staticListingSnapshotId.some)

    testWith(
      jobId = jobId,
      retrievalServiceResults = Set(retrievalResult1),
      initialJobs = Set(job1),
      initialListingSnapshots = Set(existingListingSnapshot),
      expectedJobs = Set(job1.copy(lastRunCompleted = LastRunCompleted(now).some, lastChange = LastChange(now).some, state = JobState.Completed)),
      expectedListingSnapshots = Set(existingListingSnapshot, existingListingSnapshot.copy(lastChange = LastChange(now)))
    )
  }

  def testWith(
    jobId: JobId,
    retrievalServiceResults: Set[RetrievalResult],
    initialJobs: Set[CrawlerJob],
    initialListingSnapshots: Set[ListingSnapshot],
    expectedJobs: Set[CrawlerJob],
    expectedListingSnapshots: Set[ListingSnapshot]
  ) = {
    val result = for {
      jobsStoreRef     <- Ref.of[IO, Map[JobId, CrawlerJob]](initialJobs.map(job => job.jobId                -> job).toMap)
      listingSnapshotRef <- Ref.of[IO, Map[(ListingId, LastChange), ListingSnapshot]](
                                 initialListingSnapshots.map(ls => (ls.listingId, ls.lastChange) -> ls).toMap
                               )
      jobRunner        <- service(retrievalServiceResults.map(r => r.listingId -> r).toMap, jobsStoreRef, listingSnapshotRef)
      _                <- jobRunner.run(jobId)
      jobs             <- jobsStoreRef.get.map(_.view.values.toSet)
      listingSnapshots <- listingSnapshotRef.get.map(_.view.values.map(_.copy(listingSnapshotId = staticListingSnapshotId.some)).toSet)
    } yield (jobs, listingSnapshots)

    assertIO(
      result,
      (expectedJobs, expectedListingSnapshots)
    )
  }

  def service(
               retrievalServiceResults: Map[ListingId, RetrievalResult],
               jobStoreRef: Ref[IO, Map[JobId, CrawlerJob]],
               listingSnapshotStoreRef: Ref[IO, Map[(ListingId, LastChange), ListingSnapshot]]
  ): IO[JobRunnerService[IO]] = {

    val mockRetrievalService = new RetrievalService[IO] {
      override def retrieve(listingId: ListingId): IO[Option[RetrievalService.RetrievalResult]] = retrievalServiceResults.get(listingId).pure[IO]
    }

    for {
      jobStore             <- MockJobStore(jobStoreRef)
      propertyListingStore <- MockPropertyStore(listingSnapshotStoreRef)
    } yield JobRunnerService.apply(jobStore, propertyListingStore, mockRetrievalService, NoOpMetricsRecorder.apply)(implicitly, staticClock)
  }

}
