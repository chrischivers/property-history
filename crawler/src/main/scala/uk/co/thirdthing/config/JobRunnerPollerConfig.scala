package uk.co.thirdthing.config

import scala.concurrent.duration.*

final case class JobRunnerPollerConfig(minimumPollingInterval: FiniteDuration)

object JobRunnerPollerConfig:

  private val minumumPollingInterval = 10.seconds

  def default = JobRunnerPollerConfig(minumumPollingInterval)
