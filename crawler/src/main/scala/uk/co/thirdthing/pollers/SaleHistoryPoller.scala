package uk.co.thirdthing.pollers

import cats.effect.Async
import cats.syntax.all.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.service.JobRunnerService
import uk.co.thirdthing.store.JobStore

object SaleHistoryPoller:
  def apply[F[_]: Async](jobStore: JobStore[F], jobRunnerService: JobRunnerService[F]) =
    new PollingService[F]:

      implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      override def action: F[Unit] = ???
