package uk.co.thirdthing.store

import cats.effect.Async
import cats.effect.kernel.Sync
import fs2.Pipe
import meteor.Client
import meteor.api.hi._
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, QueryRequest}
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId}

import scala.concurrent.duration.DurationInt
import cats.syntax.all._
import meteor.{DynamoDbType, KeyDef}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.jdk.CollectionConverters._

trait JobStore[F[_]] {
  def put(job: CrawlerJob): F[Unit]
  def get(jobId: JobId): F[Option[CrawlerJob]]
  def streamPut: Pipe[F, CrawlerJob, Unit]
  def streamGet: fs2.Stream[F, CrawlerJob]
  def getLatestJob: F[Option[CrawlerJob]]

}

object DynamoJobStore {

  import JobStoreCodecs._
  def apply[F[_]: Async](client: DynamoDbAsyncClient): JobStore[F] = {

    val tableName           = "crawler-jobs"
    val jobsByDateIndexName = "jobsByToDate-GSI"
    val table               = SimpleTable[F, JobId](tableName, KeyDef[JobId]("jobId", DynamoDbType.N), client)
    val meteorClient = Client[F](client)

    new JobStore[F] {

      override def put(job: CrawlerJob): F[Unit] =
        table.put[CrawlerJob](job)

      override def streamPut: Pipe[F, CrawlerJob, Unit] =
        table.batchPutUnordered[CrawlerJob](maxBatchWait = 30.seconds, parallelism = 4, BackoffStrategy.defaultStrategy())

      override def getLatestJob: F[Option[CrawlerJob]] =
        Async[F]
          .fromCompletableFuture(
            Sync[F].delay(
              client.query(
                QueryRequest
                  .builder()
                  .tableName(tableName)
                  .indexName(jobsByDateIndexName)
                  .keyConditionExpression("#type = :t0")
                  .expressionAttributeNames(Map("#type" -> "type").asJava)
                  .expressionAttributeValues(Map(":t0" -> AttributeValue.fromS("JOB")).asJava)
                  .scanIndexForward(false)
                  .limit(1)
                  .build()
              )
            )
          )
          .map(_.items().asScala.toList.headOption)
          .flatMap(itemOpt => itemOpt.fold(Option.empty[CrawlerJob].pure[F])(item => Sync[F].fromEither(crawlerJobDecoder.read(item).map(_.some))))

      override def streamGet: fs2.Stream[F, CrawlerJob] = meteorClient.scan[CrawlerJob](tableName, consistentRead = false, parallelism = 2)

      override def get(jobId: JobId): F[Option[CrawlerJob]] = table.get[CrawlerJob](jobId, consistentRead = false)
    }
  }
}
