package uk.co.thirdthing.store

import cats.effect.Async
import fs2.Pipe
import meteor.api.hi._
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy
import uk.co.thirdthing.model.Model.CrawlerJob.LastChange
import uk.co.thirdthing.model.Model.ListingSnapshot

import scala.concurrent.duration.DurationInt
//import meteor.codec.Codec.dynamoCodecFromEncoderAndDecoder
import meteor.{DynamoDbType, KeyDef}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.Property

trait ListingHistoryStore[F[_]] {
  def put(listingSnapshot: ListingSnapshot): F[Unit]
  def putStream: Pipe[F, ListingSnapshot, Unit]
}

object DynamoListingHistoryStore  {
  import CommonCodecs._
  import ListingHistoryStoreCodecs._

  def apply[F[_]: Async](client: DynamoDbAsyncClient): ListingHistoryStore[F] = {

    val table                                         = CompositeTable[F, ListingId, LastChange]("listing-history", KeyDef[ListingId]("listingId", DynamoDbType.N), KeyDef[LastChange]("lastChange", DynamoDbType.N), client)
    new ListingHistoryStore[F] {
      override def put(listingSnapshot: ListingSnapshot): F[Unit] =
        table.put[ListingSnapshot](listingSnapshot)

      override def putStream: Pipe[F, ListingSnapshot, Unit] = {
         table.batchPutUnordered[ListingSnapshot](maxBatchWait = 30.seconds, parallelism = 4, BackoffStrategy.defaultStrategy())

      }
    }
  }
}
