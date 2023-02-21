package uk.co.thirdthing.consumer

import cats.effect.Sync
import cats.syntax.all.*
import io.circe.Json
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.service.JobSeeder
import uk.co.thirdthing.sqs.SqsConsumer

object JobSeedTriggerConsumer:

  def apply[F[_]: Sync](jobSeeder: JobSeeder[F]) = new SqsConsumer[F, Json]:

    implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

    override def handle(msg: Json): F[Unit] =
      logger.info("Received seed trigger request") *>
        jobSeeder.seed *>
        logger.info("Completed seed trigger request")
