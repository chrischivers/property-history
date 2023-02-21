package uk.co.thirdthing.sqs

import scala.concurrent.duration.FiniteDuration
import monix.newtypes.NewtypeWrapped
import uk.co.thirdthing.sqs.SqsConfig.*

final case class SqsConfig(
  queueUrl: QueueUrl,
  waitTime: WaitTime,
  visibilityTimeout: VisibilityTimeout,
  heartbeatInterval: HeartbeatInterval,
  retrySleepTime: RetrySleepTime,
  streamThrottlingRate: StreamThrottlingRate,
  parallelism: Parallelism
)

object SqsConfig:
  type QueueUrl = QueueUrl.Type
  object QueueUrl extends NewtypeWrapped[String]

  type WaitTime = WaitTime.Type
  object WaitTime extends NewtypeWrapped[FiniteDuration]

  type VisibilityTimeout = VisibilityTimeout.Type
  object VisibilityTimeout extends NewtypeWrapped[FiniteDuration]

  type HeartbeatInterval = HeartbeatInterval.Type
  object HeartbeatInterval extends NewtypeWrapped[FiniteDuration]

  type RetrySleepTime = RetrySleepTime.Type
  object RetrySleepTime extends NewtypeWrapped[FiniteDuration]

  type StreamThrottlingRate = StreamThrottlingRate.Type
  object StreamThrottlingRate extends NewtypeWrapped[FiniteDuration]

  type Parallelism = Parallelism.Type
  object Parallelism extends NewtypeWrapped[Int]
