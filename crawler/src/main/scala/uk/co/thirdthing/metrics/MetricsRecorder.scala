package uk.co.thirdthing.metrics

import cats.effect.kernel.{Async, Sync}
import cats.syntax.functor.*
import software.amazon.awssdk.services.cloudwatch.{CloudWatchAsyncClient, CloudWatchClient}
import software.amazon.awssdk.services.cloudwatch.model.{MetricDatum, PutMetricDataRequest, StandardUnit}

import scala.jdk.FutureConverters.*
import scala.concurrent.duration.FiniteDuration

trait MetricsRecorder[F[_]]:
  def recordJobDuration(namespace: String)(duration: FiniteDuration): F[Unit]

object CloudWatchMetricsRecorder:
  private val jobDurationMetricName = "jobDurationMillis"

  def apply[F[_]: Async](cloudWatchClient: CloudWatchAsyncClient) = new MetricsRecorder[F]:
    override def recordJobDuration(namespace: String)(duration: FiniteDuration): F[Unit] =
      val request =
        PutMetricDataRequest
          .builder()
          .namespace(namespace)
          .metricData(
            MetricDatum
              .builder()
              .metricName(jobDurationMetricName)
              .value(duration.toMillis.toDouble)
              .unit(StandardUnit.MILLISECONDS)
              .build()
          )
          .build()
      Async[F].fromFuture(Sync[F].delay(cloudWatchClient.putMetricData(request).asScala)).void
