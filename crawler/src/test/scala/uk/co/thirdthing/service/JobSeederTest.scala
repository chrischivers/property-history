package uk.co.thirdthing.service

import cats.effect.{IO, Ref}
import fs2.io.file.{Path => Fs2Path}
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, StaticFile, Uri}
import uk.co.thirdthing.clients.RightmoveApiClient
import uk.co.thirdthing.config.JobSeederConfig
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId, JobState}
import uk.co.thirdthing.model.Types.ListingId
import uk.co.thirdthing.utils.MockJobStore

class JobSeederTest extends munit.CatsEffectSuite {

  val listingId: ListingId = ListingId(12345678)

  test("Seed the jobs successfully when there are no previous jobs in table") {

    val config = JobSeederConfig(jobChunkSize = 10, startingMaxListingIdForFirstRun = 100, emptyRecordsToDetermineLatest = 50)
    testWith(
      initialJobs = Set.empty,
      config = config,
      apiClientSuccessfulIds = Set.empty,
      expectedJobs = (0 until 10).toList
        .map(i => CrawlerJob(JobId(i + 1), ListingId((i * 10) + 1), ListingId((i + 1) * 10), JobState.NeverRun, None, None, None, None))
        .toSet,
      expectedSize = 10
    )
  }

  test("Seed the jobs successfully when there ARE previous jobs in table") {

    val config = JobSeederConfig(jobChunkSize = 10, startingMaxListingIdForFirstRun = 100, emptyRecordsToDetermineLatest = 5)
    testWith(
      initialJobs = Set(
        CrawlerJob(JobId(1), ListingId(1), ListingId(10), JobState.NeverRun, None, None, None, None),
        CrawlerJob(JobId(2), ListingId(11), ListingId(20), JobState.NeverRun, None, None, None, None)
      ),
      config = config,
      apiClientSuccessfulIds = Set(ListingId(21)),
      expectedJobs = (0 to 2).toList
        .map(i => CrawlerJob(JobId(i + 1), ListingId((i * 10) + 1), ListingId((i + 1) * 10), JobState.NeverRun, None, None, None, None))
        .toSet,
      expectedSize = 3
    )

  }

  test("Calling the seed method will not increase the existing jobs list if the API does not return any successful results ") {

    val config = JobSeederConfig(jobChunkSize = 10, startingMaxListingIdForFirstRun = 100, emptyRecordsToDetermineLatest = 5)
    testWith(
      initialJobs = Set(
        CrawlerJob(JobId(1), ListingId(1), ListingId(10), JobState.NeverRun, None, None, None, None),
        CrawlerJob(JobId(2), ListingId(11), ListingId(20), JobState.NeverRun, None, None, None, None)
      ),
      config = config,
      apiClientSuccessfulIds = Set.empty,
      expectedJobs = Set(
        CrawlerJob(JobId(1), ListingId(1), ListingId(10), JobState.NeverRun, None, None, None, None),
        CrawlerJob(JobId(2), ListingId(11), ListingId(20), JobState.NeverRun, None, None, None, None)
      ),
      expectedSize = 2
    )

  }

  def testWith(
    initialJobs: Set[CrawlerJob],
    config: JobSeederConfig,
    apiClientSuccessfulIds: Set[ListingId],
    expectedJobs: Set[CrawlerJob],
    expectedSize: Int
  ) = {
    val result = for {
      jobsRef   <- Ref.of[IO, Map[JobId, CrawlerJob]](initialJobs.map(job => job.jobId -> job).toMap)
      jobSeeder <- service(jobsRef, config, apiClientSuccessfulIds)
      _         <- jobSeeder.seed
      jobs      <- jobsRef.get.map(_.view.values.toSet)
    } yield jobs

    assertIO(result.map(_.size), expectedSize) *>
      assertIO(
        result,
        expectedJobs
      )
  }

  def service(initialJobsRef: Ref[IO, Map[JobId, CrawlerJob]], config: JobSeederConfig, apiClientSuccessfulIds: Set[ListingId]): IO[JobSeeder[IO]] = {

    object PropertyIdMatcher extends QueryParamDecoderMatcher[Long]("propertyId")

    val apiClient: RightmoveApiClient[IO] = RightmoveApiClient.apply[IO](
      Client.fromHttpApp[IO](
        HttpRoutes
          .of[IO] {
            case request @ GET -> Root / "api" / "propertyDetails" :? PropertyIdMatcher(id) if apiClientSuccessfulIds(ListingId(id)) =>
              StaticFile
                .fromPath(Fs2Path(getClass.getResource("/rightmove-api-success-response.json").getPath), Some(request))
                .getOrElseF(NotFound())
            case _ => InternalServerError("Something went wrong")

          }
          .orNotFound
      ),
      Uri.unsafeFromString("/")
    )

    MockJobStore(initialJobsRef).map(jobStore => JobSeeder.apply[IO](apiClient, jobStore, config))
  }

}
