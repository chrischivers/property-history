package uk.co.thirdthing.service

import cats.effect.{IO, Ref}
import uk.co.thirdthing.MockSqsPublisher
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.config.JobSchedulerConfig
import uk.co.thirdthing.model.Model.CrawlerJob.{LastDataChange, LastRunCompleted, LastRunScheduled}
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId, JobState, RunJobCommand}

import java.time.Instant
import java.time.temporal.ChronoUnit

class JobSchedulerTest extends munit.CatsEffectSuite {

  val config = JobSchedulerConfig.default

  private val crawlerJob1 = CrawlerJob(JobId(1), ListingId(0), ListingId(10), JobState.NeverRun, None, None, None)
  private val now         = Instant.now()

  test("Schedule an run job successfully") {

    testWith(Set(crawlerJob1), Set(crawlerJob1.copy(state = JobState.Pending)), List(RunJobCommand(crawlerJob1.jobId)))
  }

  test("Don't schedule jobs when there are no jobs") {

    testWith(Set.empty, Set.empty, List.empty)
  }

  test("Schedule a job that has was run recently but no data change value") {

    val job = crawlerJob1.copy(lastRunCompleted = Some(LastRunCompleted(now)))
    testWith(Set(job), Set(job.copy(state = JobState.Pending)), List(RunJobCommand(job.jobId)))

  }

  test("Do not schedule a job that is in Pending state and has not exceeded the expiry threshold") {

    val job = crawlerJob1.copy(
      state = JobState.Pending,
      lastRunCompleted = Some(LastRunCompleted(now)),
      lastDataChange = Some(LastDataChange(now)),
      lastRunScheduled = Some(LastRunScheduled(now.minus(20, ChronoUnit.DAYS)))
    )
    testWith(Set(job), Set(job), List.empty)
  }

  test("Schedule a job that is in Pending state and has exceeded the expiry threshold") {

    val job = crawlerJob1.copy(
      state = JobState.Pending,
      lastRunCompleted = Some(LastRunCompleted(now)),
      lastDataChange = Some(LastDataChange(now)),
      lastRunScheduled = Some(LastRunScheduled(now.minus(40, ChronoUnit.DAYS)))
    )
    testWith(Set(job), Set(job), List(RunJobCommand(job.jobId)))
  }

  test("Schedule a job that has a recent data change value, but was not run recently") {

    val job = crawlerJob1.copy(lastDataChange = Some(LastDataChange(now)))
    testWith(Set(job), Set(job.copy(state = JobState.Pending)), List(RunJobCommand(job.jobId)))
  }

  test("Schedule a job that was run recently but data has been changed recently") {

    val job = crawlerJob1.copy(
      lastRunCompleted = Some(LastRunCompleted(now.minus(2, ChronoUnit.HOURS))),
      lastDataChange = Some(LastDataChange(now.minus(24, ChronoUnit.HOURS)))
    )

    testWith(Set(job), Set(job), List(RunJobCommand(job.jobId)))

  }

  test("Do not schedule a job that was run very recently") {

    val job = crawlerJob1.copy(
      lastRunCompleted = Some(LastRunCompleted(now.minus(1, ChronoUnit.HOURS))),
      lastDataChange = Some(LastDataChange(now.minus(24, ChronoUnit.HOURS)))
    )
    testWith(Set(job), Set(job), List.empty)

  }

  test("Do not schedule a job that was run recently but data has not been changed for a while") {

    val job = crawlerJob1.copy(
      lastRunCompleted = Some(LastRunCompleted(now.minus(24, ChronoUnit.HOURS))),
      lastDataChange = Some(LastDataChange(now.minus(200, ChronoUnit.DAYS)))
    )

    testWith(Set(job), Set(job), List.empty)

  }

  test("Do not schedule a job if it is not in a schedulable state") {

    val job = crawlerJob1.copy(state = JobState.Pending)
    testWith(Set(job), Set(job), List.empty)
  }

  def testWith(initialJobs: Set[CrawlerJob], expectedJobs: Set[CrawlerJob], expectedMessages: List[RunJobCommand]) = {
    val result = for {
      jobsRef      <- Ref.of[IO, Map[JobId, CrawlerJob]](initialJobs.map(job => job.jobId -> job).toMap)
      msgsRef      <- Ref.of[IO, List[RunJobCommand]](List.empty)
      jobScheduler <- service(jobsRef, msgsRef)
      _            <- jobScheduler.scheduleJobs
      jobs         <- jobsRef.get.map(_.view.values.toSet)
      msgs         <- msgsRef.get
    } yield (jobs, msgs)

    assertIO(
      result.map { case (jobs, _) => jobs },
      expectedJobs
    )

    assertIO(
      result.map { case (_, msgs) => msgs },
      expectedMessages
    )
  }

  def service(initialJobsRef: Ref[IO, Map[JobId, CrawlerJob]], messagesRef: Ref[IO, List[RunJobCommand]]): IO[JobScheduler[IO]] = {
    val publisher = MockSqsPublisher[RunJobCommand](messagesRef)
    MockJobStore(initialJobsRef).map(jobStore => JobScheduler.apply[IO](jobStore, publisher, config))
  }

}
