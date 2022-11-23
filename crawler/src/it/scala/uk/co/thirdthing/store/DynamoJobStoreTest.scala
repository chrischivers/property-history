package uk.co.thirdthing.store

import cats.effect.IO
import meteor.api.hi.SimpleTable
import meteor.{DynamoDbType, KeyDef}
import uk.co.thirdthing.model.Model.CrawlerJob.LastRunCompleted
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId, JobState}
import uk.co.thirdthing.model.Types.ListingId

import java.time.Instant
import java.time.temporal.ChronoUnit

class DynamoJobStoreTest extends munit.CatsEffectSuite with DynamoIntegrationCrawler {
  import JobStoreCodecs._

  private val crawlerJob1 = CrawlerJob(JobId(123), ListingId(333), ListingId(444), JobState.NeverRun, None, None, None, None)
  private val crawlerJob2 = CrawlerJob(
    jobId = JobId(234),
    from = ListingId(444),
    to = ListingId(555),
    state = JobState.Pending,
    lastRunScheduled = None,
    lastRunCompleted = Some(LastRunCompleted(Instant.now().truncatedTo(ChronoUnit.MILLIS))),
    lastChange = None,
    latestDateAdded = None
  )
  private val crawlerJob3 =
    CrawlerJob(
      jobId = JobId(345),
      from = ListingId(222),
      to = ListingId(333),
      state = JobState.Completed,
      lastRunScheduled = None,
      lastRunCompleted = Some(LastRunCompleted(Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS))),
      lastChange = None,
      latestDateAdded = None
    )

  test("Put a single job") {
    withDynamoStoresAndClient() { (stores, client) =>
      val result = stores.dynamoJobStore.put(crawlerJob1).flatMap { _ =>
        SimpleTable[IO, JobId]("crawler-jobs", partitionKeyDef = KeyDef[JobId]("jobId", DynamoDbType.N), client)
          .get[CrawlerJob](crawlerJob1.jobId, consistentRead = true)
      }
      assertIO(result, Some(crawlerJob1))

    }
  }

  test("Batch put multiple jobs") {
    withDynamoStoresAndClient() { (stores, client) =>
      val result = stores.dynamoJobStore.putStream(fs2.Stream.emits[IO, CrawlerJob](Seq(crawlerJob1, crawlerJob2))).compile.drain.flatMap { _ =>
        val table = SimpleTable[IO, JobId]("crawler-jobs", partitionKeyDef = KeyDef[JobId]("jobId", DynamoDbType.N), client)
        table.get[CrawlerJob](crawlerJob1.jobId, consistentRead = true).flatMap { job1Result =>
          table.get[CrawlerJob](crawlerJob2.jobId, consistentRead = true).map(Seq(job1Result, _))
        }
      }
      assertIO(result, Seq(Some(crawlerJob1), Some(crawlerJob2)))

    }
  }

  test("Retrieve the latest job (the job with the highest 'to' value)") {
    withDynamoStoresAndClient() { (stores, _) =>
      val result =
        stores.dynamoJobStore.putStream(fs2.Stream.emits[IO, CrawlerJob](Seq(crawlerJob1, crawlerJob2, crawlerJob3))).compile.drain.flatMap { _ =>
          stores.dynamoJobStore.getLatestJob
        }
      assertIO(result, Some(crawlerJob2))
    }
  }

  test("Return empty if there is no latest job") {
    withDynamoStoresAndClient() { (stores, _) =>
      val result = stores.dynamoJobStore.getLatestJob
      assertIO(result, None)
    }
  }

  test("Stream all jobs") {
    withDynamoStoresAndClient() { (stores, _) =>
      val result =
        stores.dynamoJobStore.putStream(fs2.Stream.emits[IO, CrawlerJob](Seq(crawlerJob1, crawlerJob2, crawlerJob3))).compile.drain.flatMap { _ =>
          stores.dynamoJobStore.getStream.compile.toList
        }
      assertIO(result, List(crawlerJob1, crawlerJob2, crawlerJob3))
    }
  }

}
