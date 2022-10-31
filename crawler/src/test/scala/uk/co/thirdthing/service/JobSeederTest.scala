package uk.co.thirdthing.service

import cats.effect.{IO, Ref}
import fs2.Pipe
import fs2.io.file.{Path => Fs2Path}
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, StaticFile, Uri}
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.clients.RightmoveApiClient
import uk.co.thirdthing.config.JobSeederConfig
import uk.co.thirdthing.model.Model
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId, JobState}
import uk.co.thirdthing.store.JobStore

class JobSeederTest extends munit.CatsEffectSuite {

  val listingId: ListingId = ListingId(12345678)

  test("Seed the jobs successfully when there are no previous jobs in table") {

    val config = JobSeederConfig(jobChunkSize = 10, startingMaxListingIdForFirstRun = 100, emptyRecordsToDetermineLatest = 50)

    val result = for {
      jobsRef   <- Ref.of[IO, Set[CrawlerJob]](Set.empty)
      jobSeeder <- service(jobsRef, config, Set.empty)
      _         <- jobSeeder.seed
      jobs      <- jobsRef.get
    } yield jobs

    assertIO(result.map(_.size), 11) *>
      assertIO(result, (0 to 10).toList.map(i => CrawlerJob(JobId(i + 1), ListingId(i * 10), ListingId((i + 1) * 10), None, JobState.NeverRun)).toSet)

  }

  test("Seed the jobs successfully when there ARE previous jobs in table") {

    val config = JobSeederConfig(jobChunkSize = 10, startingMaxListingIdForFirstRun = 100, emptyRecordsToDetermineLatest = 5)

    val result = for {
      jobsRef <- Ref.of[IO, Set[CrawlerJob]](
                  Set(
                    CrawlerJob(JobId(1), ListingId(0), ListingId(10), None, JobState.NeverRun),
                    CrawlerJob(JobId(2), ListingId(10), ListingId(20), None, JobState.NeverRun)
                  )
                )
      jobSeeder <- service(jobsRef, config, Set(ListingId(21)))
      _         <- jobSeeder.seed
      jobs      <- jobsRef.get
    } yield jobs

    assertIO(result.map(_.size), 3) *>
      assertIO(result, (0 to 2).toList.map(i => CrawlerJob(JobId(i + 1), ListingId(i * 10), ListingId((i + 1) * 10), None, JobState.NeverRun)).toSet)
  }

  test("Calling the seed method will not increase the existing jobs list if the API does not return any successful results ") {

    val config = JobSeederConfig(jobChunkSize = 10, startingMaxListingIdForFirstRun = 100, emptyRecordsToDetermineLatest = 5)

    val result = for {
      jobsRef <- Ref.of[IO, Set[CrawlerJob]](
                  Set(
                    CrawlerJob(JobId(1), ListingId(0), ListingId(10), None, JobState.NeverRun),
                    CrawlerJob(JobId(2), ListingId(10), ListingId(20), None, JobState.NeverRun)
                  )
                )
      jobSeeder <- service(jobsRef, config, Set.empty)
      _         <- jobSeeder.seed
      _         <- jobSeeder.seed
      _         <- jobSeeder.seed
      jobs      <- jobsRef.get
      _         = println(jobs)
    } yield jobs

    assertIO(result.map(_.size), 2)
  }

  def service(initialJobsRef: Ref[IO, Set[CrawlerJob]], config: JobSeederConfig, apiClientSuccessfulIds: Set[ListingId]): IO[JobSeeder[IO]] = {

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

    val jobStore = new JobStore[IO] {
      override def put(job: Model.CrawlerJob): IO[Unit] = initialJobsRef.update(_ + job)

      override def streamPut: Pipe[IO, Model.CrawlerJob, Unit] = _.evalMap(job => initialJobsRef.update(_ + job))

      override def getLatestJob: IO[Option[Model.CrawlerJob]] = initialJobsRef.get.map(jobList => jobList.toList.sortBy(_.from.value).lastOption)
    }
    initialJobsRef.get
      .flatMap(jobsList => fs2.Stream.emits(jobsList.toSeq).through(jobStore.streamPut).compile.drain)
      .as(JobSeeder.apply[IO](apiClient, jobStore, config))
  }

}
