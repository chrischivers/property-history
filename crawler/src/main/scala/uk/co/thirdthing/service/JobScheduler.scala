package uk.co.thirdthing.service

import cats.effect.Async
import cats.effect.kernel.Clock
import cats.syntax.all._
import uk.co.thirdthing.config.JobSchedulerConfig
import uk.co.thirdthing.model.Model.CrawlerJob.{LastRunCompleted, LastRunScheduled}
import uk.co.thirdthing.model.Model.{CrawlerJob, JobState, RunJobCommand}
import uk.co.thirdthing.model.Types.{DateAdded, LastChange}
import uk.co.thirdthing.sqs.SqsPublisher
import uk.co.thirdthing.store.JobStore

import java.time.Instant
import scala.concurrent.duration.{FiniteDuration, _}

trait JobScheduler[F[_]] {
  def scheduleJobs: F[Unit]
  def scheduleJob(job: CrawlerJob): F[Unit]
}

object JobScheduler {

  private type TimeSinceLastDataChange  = FiniteDuration
  private type TimeSinceLastRecordAdded = FiniteDuration
  private type TimeBetweenRuns          = FiniteDuration

  def apply[F[_]: Async](jobStore: JobStore[F], publisher: SqsPublisher[F, RunJobCommand], config: JobSchedulerConfig)(implicit clock: Clock[F]) =
    new JobScheduler[F] {

      override def scheduleJob(job: CrawlerJob) =
        updateScheduledJobState(job).flatMap(jobStore.put)

      override def scheduleJobs: F[Unit] =
        jobStore.getStream
          .evalMap(job => shouldSchedule(job).map(_ -> job))
          .collect { case (shouldSchedule, job) if shouldSchedule => job }
          .evalTap(scheduleJob)
          .evalMap(job => publisher.publish(RunJobCommand(job.jobId)))
          .compile
          .drain

      private def updateScheduledJobState(job: CrawlerJob): F[CrawlerJob] =
        clock.realTimeInstant.map(now => job.copy(state = JobState.Pending, lastRunScheduled = LastRunScheduled(now).some))

      private def shouldSchedule(crawlerJob: CrawlerJob): F[Boolean] =
        clock.realTimeInstant.map { now =>
          val pendingJobExpiredCutoff = now.minusMillis(config.jobExpiryTimeSinceScheduled.toMillis)
          if (crawlerJob.state.schedulable || crawlerJob.lastRunScheduled.exists(_.value.isBefore(pendingJobExpiredCutoff))) {
            crawlerJob.lastRunCompleted.flatMap(lr => crawlerJob.lastChange.map(lr -> _)).fold(true) {
              case (lastRun, lastDataChange) => shouldScheduleByDates(now, lastRun, lastDataChange, crawlerJob.latestDateAdded)
            }
          } else false

        }

      private def shouldScheduleByDates(
        now: Instant,
        lastRunCompleted: LastRunCompleted,
        lastDataChange: LastChange,
        latestDateAdded: Option[DateAdded]
      ): Boolean = {
        val lastChangedBaseline                                         = LastChange(Instant.parse("2022-11-28T17:48:04.00Z"))
        val defaultDatedAddedWhereNotExisted                            = DateAdded(Instant.parse("2000-01-01T00:00:00.00Z"))
        val timeBetweenRuns: TimeSinceLastDataChange => TimeBetweenRuns = _ / config.timeBetweenRunsFactor

        //Here our last change value is within the initial seeding run, so we use the data added instead
        val lastChanged =
          if (lastDataChange.value.isBefore(lastChangedBaseline.value)) latestDateAdded.getOrElse(defaultDatedAddedWhereNotExisted).value
          else lastDataChange.value

        val nowMillis               = now.toEpochMilli
        val timeSinceLastDataChange = nowMillis - lastChanged.toEpochMilli
        val timeSinceLastRun        = nowMillis - lastRunCompleted.value.toEpochMilli
        val requiredTimeBetweenRuns = timeBetweenRuns(timeSinceLastDataChange.millis)
        timeSinceLastRun >= requiredTimeBetweenRuns.toMillis
      }
    }

}
