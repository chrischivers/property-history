package uk.co.thirdthing.sqs

import scala.concurrent.duration.FiniteDuration

final case class SqsConfig(
  queueUrl: String,
  waitTime: FiniteDuration,
  visibilityTimeout: FiniteDuration,
  heartbeatInterval: FiniteDuration,
  retrySleepTime: FiniteDuration,
  streamThrottlingRate: FiniteDuration,
  processingParallelism: Int
)
