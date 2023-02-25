package uk.co.thirdthing.pollers

import cats.effect.Async
import cats.syntax.all.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.service.UpdatePropertyHistoryService
import uk.co.thirdthing.store.JobStore

object UpdatePropertyHistoryPoller:
  def apply[F[_]: Async](jobStore: JobStore[F], updatePropertyHistoryService: UpdatePropertyHistoryService[F]) =
    new PollingService[F]:

      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]
      override def action: F[Unit] =
        jobStore.nextJobToRun.flatMap {
          case Some(job) =>
            logger.info(s"Picking up 'update property history' job ${job.jobId.value} [$job]") *>
              updatePropertyHistoryService.run(job.jobId)
          case None =>
            logger.warn(s"No property history update jobs available to pick up. Will retry on next poll") *>
              ().pure[F]
        }
