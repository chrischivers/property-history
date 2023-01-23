package uk.co.thirdthing.service

import cats.effect.{Async, Sync}
import cats.syntax.all._
import uk.co.thirdthing.model.Model.{JobId, RunJobCommand}
import uk.co.thirdthing.sqs.SqsPublisher
import uk.co.thirdthing.store.JobStore
import uk.co.thirdthing.model.Model
import uk.co.thirdthing.config.JobRunnerPollerConfig
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait JobRunnerPoller[F[_]] {
  def start: fs2.Stream[F, Unit]
}

object JobRunnerPoller {
  def apply[F[_]: Async](jobStore: JobStore[F], jobRunnerService: JobRunnerService[F], config: JobRunnerPollerConfig) = new JobRunnerPoller[F] {

    implicit val logger = Slf4jLogger.getLogger[F]
    def start: fs2.Stream[F, Unit] =
      fs2.Stream
        .repeatEval[F, Unit](fetchAndRun)
        .meteredStartImmediately(config.minimumPollingInterval)

    private def fetchAndRun: F[Unit] =
      jobStore.nextJobToRun.flatMap {
        case Some(job) =>
          logger.info(s"Picking up job ${job.jobId.value} [$job]") *>
            jobRunnerService.run(job.jobId)
        case None =>
          logger.warn(s"No jobs available to pick up. Will retry on next poll") *>
            ().pure[F]
      }
  }
}
