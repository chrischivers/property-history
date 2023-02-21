package uk.co.thirdthing.pollers

import cats.effect.Async

import scala.concurrent.duration.FiniteDuration

abstract class PollingService[F[_]: Async] {
  def action: F[Unit]

  def startPolling(pollingInterval: FiniteDuration): fs2.Stream[F, Unit] =
    fs2.Stream
      .repeatEval[F, Unit](action)
      .meteredStartImmediately(pollingInterval)
}
