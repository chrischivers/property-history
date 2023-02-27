package uk.co.thirdthing.service

import cats.Applicative
import cats.effect.{Clock, IO, Ref}
import cats.syntax.all.*
import uk.co.thirdthing.model.Model.CrawlerJob.LastRunCompleted
import uk.co.thirdthing.model.Model.*
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.PropertyScrapingService.ScrapeResult
import uk.co.thirdthing.utils.{MockJobStore, MockPropertyStore, NoOpMetricsRecorder}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

class UpdatePropertyHistoryServiceTest extends munit.CatsEffectSuite:

  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val staticClock = new Clock[IO]:
    override def applicative: Applicative[IO] = implicitly

    override def monotonic: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]

    override def realTime: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]
  private val staticListingSnapshotId = ListingSnapshotId(12345)

  private val jobId = JobId(98765)
  private val job1 = CrawlerJob(
    jobId = jobId,
    from = ListingId(0),
    to = ListingId(1000),
    state = JobState.Pending,
    lastRunStarted = None,
    lastRunCompleted = None,
    lastChange = None,
    latestDateAdded = None
  )

  private val listingId  = ListingId(500)
  private val propertyId = PropertyId(3495732)
  private val scrapedResult1 = ScrapeResult(
    listingId,
    propertyId,
    DateAdded(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
    PropertyDetails.from(
      Price(100000),
      TransactionType.Sale,
      visible = true,
      ListingStatus.SoldSTC,
      "weekly",
      100.5,
      90.1,
      ThumbnailUrl("http://thumbnail.com")
    )
  )

  test("Run a job successfully and update the records") {

    testWith(
      jobId = jobId,
      scrapeServiceResult = Set(scrapedResult1),
      initialJobs = Set(job1),
      initialListingSnapshots = Set.empty,
      expectedJobs = Set(
        job1.copy(
          lastRunCompleted = LastRunCompleted(now).some,
          lastChange = LastChange(now).some,
          latestDateAdded = scrapedResult1.dateAdded.some,
          state = JobState.Completed
        )
      ),
      expectedListingSnapshots = Set(
        ListingSnapshot(
          scrapedResult1.listingId,
          LastChange(now),
          scrapedResult1.propertyId,
          scrapedResult1.dateAdded,
          scrapedResult1.propertyDetails,
          staticListingSnapshotId.some
        )
      )
    )
  }

  test("Run a job successfully update the records for a record already existing but the data has changed") {

    val existingListingSnapshot =
      ListingSnapshot(
        scrapedResult1.listingId,
        LastChange(now.minus(1, ChronoUnit.DAYS)),
        scrapedResult1.propertyId,
        scrapedResult1.dateAdded,
        scrapedResult1.propertyDetails.copy(price = Price(222).some),
        staticListingSnapshotId.some
      )

    testWith(
      jobId = jobId,
      scrapeServiceResult = Set(scrapedResult1),
      initialJobs = Set(job1),
      initialListingSnapshots = Set(existingListingSnapshot),
      expectedJobs = Set(
        job1.copy(
          lastRunCompleted = LastRunCompleted(now).some,
          lastChange = LastChange(now).some,
          latestDateAdded = scrapedResult1.dateAdded.some,
          state = JobState.Completed
        )
      ),
      expectedListingSnapshots = Set(
        existingListingSnapshot,
        existingListingSnapshot.copy(lastChange = LastChange(now), details = scrapedResult1.propertyDetails)
      )
    )
  }

  def testWith(
                jobId: JobId,
                scrapeServiceResult: Set[ScrapeResult],
                initialJobs: Set[CrawlerJob],
                initialListingSnapshots: Set[ListingSnapshot],
                expectedJobs: Set[CrawlerJob],
                expectedListingSnapshots: Set[ListingSnapshot]
  ) =
    val result = for
      jobsStoreRef <- Ref.of[IO, Map[JobId, CrawlerJob]](initialJobs.map(job => job.jobId -> job).toMap)
      listingSnapshotRef <- Ref.of[IO, Map[(ListingId, LastChange), ListingSnapshot]](
        initialListingSnapshots.map(ls => (ls.listingId, ls.lastChange) -> ls).toMap
      )
      jobRunner <- service(scrapeServiceResult.map(r => r.listingId -> r).toMap, jobsStoreRef, listingSnapshotRef)
      _         <- jobRunner.run(jobId)
      jobs      <- jobsStoreRef.get.map(_.view.values.toSet)
      listingSnapshots <- listingSnapshotRef.get.map(
        _.view.values.map(_.copy(listingSnapshotId = staticListingSnapshotId.some)).toSet
      )
    yield (jobs, listingSnapshots)

    assertIO(
      result,
      (expectedJobs, expectedListingSnapshots)
    )

  def service(
    scrapeServiceResults: Map[ListingId, ScrapeResult],
    jobStoreRef: Ref[IO, Map[JobId, CrawlerJob]],
    listingSnapshotStoreRef: Ref[IO, Map[(ListingId, LastChange), ListingSnapshot]]
  ): IO[UpdatePropertyHistoryService[IO]] =

    val mockScrapeService = new PropertyScrapingService[IO]:
      override def scrape(listingId: ListingId): IO[Option[ScrapeResult]] =
        scrapeServiceResults.get(listingId).pure[IO]

    for
      jobStore             <- MockJobStore(jobStoreRef)
      propertyListingStore <- MockPropertyStore(listingSnapshotStoreRef)
    yield UpdatePropertyHistoryService.apply(jobStore, propertyListingStore, mockScrapeService, NoOpMetricsRecorder.apply)(
      implicitly,
      staticClock
    )
