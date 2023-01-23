package uk.co.thirdthing.config

import scala.concurrent.duration._

final case class JobSchedulingConfig(jobExpiryTimeSinceScheduled: FiniteDuration, timeBetweenRunsFactor: Int)

object JobSchedulingConfig {

  private val jobExpiryTimeSinceScheduled = 4.hours 
  private val timeBetweenRunsFactor       = 12

  def default = JobSchedulingConfig(jobExpiryTimeSinceScheduled, timeBetweenRunsFactor)
}
