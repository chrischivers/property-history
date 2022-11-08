package uk.co.thirdthing.utils

import cats.effect.{IO, Ref}
import fs2.Pipe
import uk.co.thirdthing.model.Model
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId}
import uk.co.thirdthing.store.JobStore

object MockJobStore {

  def apply(initialJobsRef: Ref[IO, Map[JobId, CrawlerJob]]): IO[JobStore[IO]] = {

    val jobStore = new JobStore[IO] {
      override def put(job: Model.CrawlerJob): IO[Unit] = initialJobsRef.update(_ + (job.jobId -> job))

      override def putStream: Pipe[IO, Model.CrawlerJob, Unit] = _.evalMap(put)

      override def getLatestJob: IO[Option[Model.CrawlerJob]] = initialJobsRef.get.map(_.view.values.toList.sortBy(_.from.value).lastOption)

      override def getStream: fs2.Stream[IO, CrawlerJob] = fs2.Stream.evals(initialJobsRef.get.map(_.view.values.toList))

      override def get(jobId: JobId): IO[Option[CrawlerJob]] = initialJobsRef.get.map(_.get(jobId))
    }

    initialJobsRef.get
      .flatMap(jobsList => fs2.Stream.emits(jobsList.view.values.toList).through(jobStore.putStream).compile.drain)
      .as(jobStore)
  }

}
