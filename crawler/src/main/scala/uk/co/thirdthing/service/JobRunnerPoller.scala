package uk.co.thirdthing.service

import cats.effect.{Async, Sync}
import uk.co.thirdthing.model.Model.{JobId, RunJobCommand}
import uk.co.thirdthing.sqs.SqsPublisher
import uk.co.thirdthing.store.JobStore

trait JobRunnerPoller[F[_]] {
  def poll: F[Unit]
}

object JobRunnerPoller {
  def apply[F[_]: Async](jobRunnerService: JobRunnerService[F]) = new JobRunnerService[F] {
    override def run(jobId: JobId): F[Unit] = ???
  }
}
