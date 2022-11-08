package uk.co.thirdthing.store

import cats.effect.Async
import fs2.Pipe
import meteor.api.hi._
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy

import scala.concurrent.duration.DurationInt
//import meteor.codec.Codec.dynamoCodecFromEncoderAndDecoder
import meteor.{DynamoDbType, KeyDef}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.Property

trait PropertyStore[F[_]] {
  def put(property: Property): F[Unit]
  def get(listingId: ListingId): F[Option[Property]]
  def delete(listingId: ListingId): F[Unit]
  def putStream: Pipe[F, Property, Unit]
}

object DynamoPropertyStore  {

  import CommonCodecs._
  import PropertyStoreCodecs._

  def apply[F[_]: Async](client: DynamoDbAsyncClient): PropertyStore[F] = {

    val table                                         = SimpleTable[F, ListingId]("properties", KeyDef[ListingId]("listingId", DynamoDbType.N), client)
    new PropertyStore[F] {
      override def put(property: Property): F[Unit] =
        table.put[Property](property)

      override def putStream: Pipe[F, Property, Unit] = {
         table.batchPutUnordered[Property](maxBatchWait = 30.seconds, parallelism = 4, BackoffStrategy.defaultStrategy())

      }

      override def get(listingId: ListingId): F[Option[Property]] = table.get[Property](listingId, consistentRead = false)

      override def delete(listingId: ListingId): F[Unit] = table.delete(listingId)
    }
  }
}
