package uk.co.thirdthing.utils

import cats.effect.kernel.{Async, Sync}
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

object CatsEffectUtils:

  extension [F[_]: Async, T](f: F[T])
    def withBackoffRetry(
      maxDelay: FiniteDuration,
      multiplier: Double,
      maxRetries: Int = 50,
      attemptNumber: Int = 1,
      errorsToHandle: Throwable => Boolean = _ => true
    )(using logger: Logger[F]): F[T] =

      val delayInSec  = (Math.pow(2.0, attemptNumber) - 1.0) * .5
      val backOffWait = Math.round(Math.min(delayInSec * multiplier, maxDelay.toSeconds.toDouble))
      f.attempt.flatMap {
        case Left(err) if attemptNumber == maxRetries =>
          logger.error(err)("Maximum number of retry attempts exceeded") *> err.raiseError[F, T]
        case Left(err) if !errorsToHandle(err) =>
          logger.error(err)(s"Error type not handled by retry mechanism") *> err.raiseError[F, T]
        case Left(err) =>
          logger.error(err)(err.getMessage) >>
            logger.info(s"retrying another time with backoff wait of $backOffWait seconds") *> Sync[F].sleep(
              backOffWait.seconds
            ) *> withBackoffRetry(
              maxDelay,
              multiplier,
              attemptNumber + 1
            )
        case Right(t) => t.pure[F]
      }
