package uk.co.thirdthing.utils

import cats.effect.{IO, Ref}
import fs2.Pipe
import uk.co.thirdthing.model.Model
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId}
import uk.co.thirdthing.store.JobStore
import cats.effect.Clock
import uk.co.thirdthing.utils.TimeUtils._

object MockJobStore {

  def apply(initialJobsRef: Ref[IO, Map[JobId, CrawlerJob]]): IO[JobStore[IO]] = {

    val jobStore = new JobStore[IO] {

      override def nextJobToRun: IO[Option[CrawlerJob]] = ???

      override def put(job: Model.CrawlerJob): IO[Unit] = initialJobsRef.update(_ + (job.jobId -> job))

      override def getLatestJob: IO[Option[Model.CrawlerJob]] = initialJobsRef.get.map(_.view.values.toList.sortBy(_.from.value).lastOption)

      override def getAndLock(jobId: JobId): IO[Option[CrawlerJob]] = Clock[IO].realTimeInstant.flatMap { now =>
        initialJobsRef
          .updateAndGet(
            _.updatedWith(jobId)(_.map(_.copy(state = Model.JobState.Pending, lastRunStarted = Some(CrawlerJob.LastRunStarted(now)))))
          )
          .map(_.get(jobId))
      }
      override def jobs: fs2.Stream[IO, CrawlerJob] = fs2.Stream.evals(initialJobsRef.get.map(_.view.values.toList))
    }

    initialJobsRef.get
      .flatMap(jobsList => fs2.Stream.emits(jobsList.view.values.toList).evalTap(jobStore.put).compile.drain)
      .as(jobStore)
  }

}
