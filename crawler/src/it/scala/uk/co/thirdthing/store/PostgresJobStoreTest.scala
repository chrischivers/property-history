package uk.co.thirdthing.store

import cats.syntax.all._
import skunk.exception.PostgresErrorException
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.store._

import java.time.Instant
import java.time.temporal.ChronoUnit
import uk.co.thirdthing.model.Model

class PostgresJobStoreTest extends munit.CatsEffectSuite with PostgresJobStoreIntegration {

  private val jobId1 = Model.JobId(98765)
  private val jobId2 = Model.JobId(32454)
  private val job1 = Model.CrawlerJob(
    jobId = jobId1,
    from = ListingId(0),
    to = ListingId(1000),
    state = Model.JobState.Pending,
    lastRunStarted = None,
    lastRunCompleted = None,
    lastChange = None,
    latestDateAdded = None
  )

  private val job2 = Model.CrawlerJob(
    jobId = jobId2,
    from = ListingId(1001),
    to = ListingId(2000),
    state = Model.JobState.Pending,
    lastRunStarted = None,
    lastRunCompleted = None,
    lastChange = None,
    latestDateAdded = None
  )

  test("Store a job, and retrieve it again") {
    withPostgresJobStore { store =>
      val result = store.put(job1) *>
        store.getAndLock(jobId1)

      assertIO(result, Some(job1))
    }
  }

  test("Store multiple jobs, and retrieve them again") {
    withPostgresJobStore { store =>
      val result = store.put(job1) *> store.put(job2) *>
        store.jobs.compile.toList

      assertIO(result, List(job1, job2))
    }
  }

    test("Store a job, retrieve it again, but not a second time") {
    withPostgresJobStore { store =>
      val result = store.put(job1) *>
        store.getAndLock(jobId1) *>
         store.getAndLock(jobId1)

      assertIO(result, None)
    }
  }

  test("Update record where job with same jobId already exists") {
    withPostgresJobStore { store =>
      val result = store.put(job1) *> store.put(job1.copy(state = Model.JobState.Completed)) *>
        store.getAndLock(jobId1)

      assertIO(result, Some(job1.copy(state = Model.JobState.Completed)))
    }
  }

  test("Retrieve latest job") {
    withPostgresJobStore { store =>
      val result = store.put(job2) *> store.put(job1) *>
        store.getLatestJob

      assertIO(result, Some(job2))
    }
  }

  test("Retrieve next job to schedule where lastRunCompleted is null") {
    withPostgresJobStore { store =>
      val result = store.put(job2.copy(state = Model.JobState.Completed)) *> store.put(job1.copy(state = Model.JobState.Completed)) *>
        store.nextJobToRun

      assertIO(result, Some(job2.copy(state = Model.JobState.Completed)))
    }
  }
}
