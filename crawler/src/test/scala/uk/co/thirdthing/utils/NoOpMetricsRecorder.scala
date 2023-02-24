package uk.co.thirdthing.utils

import cats.effect.IO
import uk.co.thirdthing.metrics.MetricsRecorder

import scala.concurrent.duration.FiniteDuration

object NoOpMetricsRecorder:

  def apply: MetricsRecorder[IO] = new MetricsRecorder[IO]:
    override def recordJobDuration(namespace: String)(duration: FiniteDuration): IO[Unit] = IO.unit
