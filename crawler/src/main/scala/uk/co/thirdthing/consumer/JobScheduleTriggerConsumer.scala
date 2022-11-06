package uk.co.thirdthing.consumer

import cats.effect.Sync
import cats.syntax.all._
import io.circe.Json
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.service.{JobScheduler, JobSeeder}
import uk.co.thirdthing.sqs.SqsConsumer


object JobScheduleTriggerConsumer {

  def apply[F[_] : Sync](jobScheduler: JobScheduler[F]) = new SqsConsumer[F, Json] {

    implicit val logger = Slf4jLogger.getLogger[F]

    override def handle(msg: Json): F[Unit] = {
      logger.info("Received job schedule trigger request") *>
        jobScheduler.scheduleJobs
    }
  }
}
