package uk.co.thirdthing.consumer

import cats.effect.Sync
import cats.syntax.all._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.service.JobSeeder
import uk.co.thirdthing.sqs.SqsConsumer


class JobSeedRequestConsumer[F[_]: Sync](jobSeeder: JobSeeder[F]) extends SqsConsumer[F, Unit] {

  implicit val logger = Slf4jLogger.getLogger[F]

  override def handle(msg: Unit): F[Unit] = {
    logger.info("Received seed request") *>
    jobSeeder.seed
  }
}
