//package uk.co.thirdthing.consumer
//
//import cats.effect.{Clock, Sync}
//import cats.syntax.all._
//import org.typelevel.log4cats.slf4j.Slf4jLogger
//import uk.co.thirdthing.model.Model.RunJobCommand
//import uk.co.thirdthing.service.JobRunnerService
//import uk.co.thirdthing.sqs.SqsConsumer
//
//object JobRunnerConsumer {
//
//  def apply[F[_]: Sync](jobRunnerService: JobRunnerService[F]) = new SqsConsumer[F, RunJobCommand] {
//
//    implicit val logger = Slf4jLogger.getLogger[F]
//
//    override def handle(msg: RunJobCommand): F[Unit] =
//      logger.info(s"Received run job command for job ${msg.jobId.value}") *>
//        jobRunnerService.run(msg.jobId) *>
//        logger.info(s"Completed run job command for job")
//  }
//
//}
