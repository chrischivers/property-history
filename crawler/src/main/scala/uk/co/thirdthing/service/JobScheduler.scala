package uk.co.thirdthing.service

import cats.effect.{Async, Sync}
import cats.syntax.all._
import cats.effect.kernel.Clock
import fs2.Pipe
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.co.thirdthing.config.JobSchedulerConfig
import uk.co.thirdthing.model.Model.CrawlerJob.LastRunScheduled
import uk.co.thirdthing.model.Model.{CrawlerJob, JobState, RunJobCommand}
import uk.co.thirdthing.sqs.SqsPublisher
import uk.co.thirdthing.store.JobStore

import java.time.ZoneId
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

trait JobScheduler[F[_]] {
  def scheduleJobs: F[Unit]
}

object JobScheduler {

  private type TimeSinceLastDataChange = FiniteDuration
  private type TimeBetweenRuns         = FiniteDuration

  def apply[F[_]: Async](jobStore: JobStore[F], publisher: SqsPublisher[F, RunJobCommand], config: JobSchedulerConfig)(implicit clock: Clock[F]) =
    new JobScheduler[F] {

      private val timeBetweenRuns: TimeSinceLastDataChange => TimeBetweenRuns = _ / config.timeBetweenRunsFactor

    override def scheduleJobs: F[Unit] =
      jobStore.getStream
        .evalMap(job => shouldSchedule(job).map(_ -> job))
        .collect { case (shouldSchedule, job) if shouldSchedule => job }
        .evalMap(updateScheduledJobState)
        .evalTap(jobStore.put) //todo batch puts
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
              case (lastRun, lastDataChange) =>
                val nowMillis               = now.toEpochMilli
                val timeSinceLastDataChange = nowMillis - lastDataChange.value.toEpochMilli
                val timeSinceLastRun        = nowMillis - lastRun.value.toEpochMilli
                val requiredTimeBetweenRuns = timeBetweenRuns(timeSinceLastDataChange.millis)
                timeSinceLastRun >= requiredTimeBetweenRuns.toMillis
            }
          } else false

        }
    }

}
