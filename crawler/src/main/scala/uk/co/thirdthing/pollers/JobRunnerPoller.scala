package uk.co.thirdthing.pollers

import cats.effect.Async
import cats.syntax.all.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.service.JobRunnerService
import uk.co.thirdthing.store.JobStore

object JobRunnerPoller:
  def apply[F[_]: Async](jobStore: JobStore[F], jobRunnerService: JobRunnerService[F]) =
    new PollingService[F]:

      implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]
      override def action: F[Unit] =
        jobStore.nextJobToRun.flatMap {
          case Some(job) =>
            logger.info(s"Picking up job ${job.jobId.value} [$job]") *>
              jobRunnerService.run(job.jobId)
          case None =>
            logger.warn(s"No jobs available to pick up. Will retry on next poll") *>
              ().pure[F]
        }
