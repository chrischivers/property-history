package uk.co.thirdthing.utils

import cats.effect.kernel.{Async, Sync}
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException

import scala.concurrent.duration._

object CatsEffectUtils {

  implicit class CatsEffectOps[F[_]: Async, T](f: F[T]) {

    def retryWhenThroughputExceeded(implicit logger: Logger[F]): F[T] =
      withBackoffRetry(1.minute, 10, errorsToHandle = {
        case _: ProvisionedThroughputExceededException => true
        case _                                         => false
      })

    def withBackoffRetry(
      maxDelay: FiniteDuration,
      multiplier: Double,
      maxRetries: Int = 50,
      attemptNumber: Int = 1,
      errorsToHandle: Throwable => Boolean = _ => true
    )(implicit logger: Logger[F]): F[T] = {

      val delayInSec  = (Math.pow(2.0, attemptNumber) - 1.0) * .5
      val backOffWait = Math.round(Math.min(delayInSec * multiplier, maxDelay.toSeconds.toDouble))
      f.attempt.flatMap {
        case Left(err) if attemptNumber == maxRetries => logger.error(err)("Maximum number of retry attempts exceeded") >> err.raiseError[F, T]
        case Left(err) if !errorsToHandle(err) =>
          logger.error(err)(s"Error type not handled by retry mechanism") >> err.raiseError[F, T]
        case Left(err) =>
          logger.error(err)(err.getMessage) >>
            logger.info(s"retrying another time with backoff wait of $backOffWait seconds") *> Sync[F].sleep(backOffWait.seconds) *> withBackoffRetry(
              maxDelay,
              multiplier,
              attemptNumber + 1
            )
        case Right(t) => t.pure[F]
      }
    }
  }
}
