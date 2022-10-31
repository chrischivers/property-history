package uk.co.thirdthing.store

import cats.effect.Async
import cats.effect.kernel.Sync
import fs2.Pipe
import meteor.{Client, Expression, Query, SortKeyQuery}
import meteor.api.hi._
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, QueryRequest}
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId}

import java.time.Instant
import scala.concurrent.duration.DurationInt
//import meteor.codec.Codec.dynamoCodecFromEncoderAndDecoder
import meteor.{DynamoDbType, KeyDef}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.Property
import cats.syntax.all._
import scala.jdk.CollectionConverters._

trait JobStore[F[_]] {
  def put(job: CrawlerJob): F[Unit]
  def streamPut: Pipe[F, CrawlerJob, Unit]
  def getLatestJob: F[Option[CrawlerJob]]
}

object DynamoJobStore {

  import JobStoreCodecs._
  def apply[F[_]: Async](client: DynamoDbAsyncClient): JobStore[F] = {

    val tableName           = "crawler-jobs"
    val jobsByDateIndexName = "jobsByToDate-GSI"
    val table               = SimpleTable[F, JobId](tableName, KeyDef[JobId]("jobId", DynamoDbType.N), client)

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
    }
  }
}
