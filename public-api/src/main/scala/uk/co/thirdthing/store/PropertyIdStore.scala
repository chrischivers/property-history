package uk.co.thirdthing.store

import cats.effect.Async
import meteor.api.hi._
import meteor.codec._
import meteor.syntax._
import meteor.{DynamoDbType, KeyDef}
import uk.co.thirdthing.Rightmove.{ListingId, PropertyId}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

trait PropertyIdStore[F[_]] {
  def idFor(listingId: ListingId): F[Option[PropertyId]]
}

object DynamoPropertyIdStore {

  implicit private val decoder: Decoder[PropertyId] = Decoder.instance(_.getAs[Long]("propertyId").map(PropertyId.apply))

  def apply[F[_]: Async](client: DynamoDbAsyncClient): PropertyIdStore[F] = {
    implicit val listingIdEncoder: Encoder[ListingId] = Encoder.instance(_.value.asAttributeValue)
    val table                                                  = SimpleTable[F, ListingId]("properties", KeyDef[ListingId]("listingId", DynamoDbType.N), client)
    new PropertyIdStore[F] {
      override def idFor(listingId: ListingId): F[Option[PropertyId]] = {
        table.get[PropertyId](listingId, consistentRead = false)
      }

    }
  }
}
