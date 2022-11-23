package uk.co.thirdthing.utils

import cats.effect.{IO, Ref}
import fs2.Pipe
import uk.co.thirdthing.model.Model
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId}
import uk.co.thirdthing.service.JobScheduler
import uk.co.thirdthing.store.JobStore

object MockJobScheduler {

  def apply(): JobScheduler[IO] =
    new JobScheduler[IO] {
      override def scheduleJobs: IO[Unit] = IO.unit

      override def scheduleJob(job: CrawlerJob): IO[Unit] = IO.unit
    }

}
