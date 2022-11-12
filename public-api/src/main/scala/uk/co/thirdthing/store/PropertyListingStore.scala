package uk.co.thirdthing.store

import cats.effect.Async
import meteor.api.hi._
import meteor.codec._
import meteor.syntax._
import meteor.{DynamoDbType, KeyDef}
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, PropertyId}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import uk.co.thirdthing.model.Model.RightmoveListing

import java.time.Instant

trait PropertyListingStore[F[_]] {
  def listingsFor(propertyId: PropertyId): fs2.Stream[F, RightmoveListing]
}

object DynamoPropertyListingStore {

  implicit private val decoder: Decoder[RightmoveListing] = Decoder.instance { attValue =>
    for {
      id        <- attValue.getAs[Long]("listingId").map(ListingId.apply)
      url       <- attValue.getAs[String]("url")
      dateAdded <- attValue.getAs[Instant]("dateAdded").map(DateAdded.apply)
    } yield RightmoveListing(id, url, dateAdded)
  }

  def apply[F[_]: Async](client: DynamoDbAsyncClient) = {
    implicit val listingIdEncoder: Encoder[PropertyId] = Encoder.instance(_.value.asAttributeValue)
    val index = SecondaryCompositeIndex[F, PropertyId, Long](
      "properties",
      "propertyId-LSI",
      KeyDef[PropertyId]("propertyId", DynamoDbType.N),
      KeyDef[Long]("dateAdded", DynamoDbType.N),
      client
    )

    new PropertyListingStore[F] {
      override def listingsFor(propertyId: PropertyId): fs2.Stream[F, RightmoveListing] =
        index.retrieve[RightmoveListing](propertyId)
    }

  }
}
