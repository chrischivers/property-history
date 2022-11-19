package uk.co.thirdthing.store

import cats.effect.Async
import meteor.api.hi._
import meteor.codec._
import meteor.syntax._
import meteor.{DynamoDbType, KeyDef}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import uk.co.thirdthing.model.Types.{ListingId, PropertyId, PropertyListing}

trait PropertyStore[F[_]] {
  def propertyIdFor(listingId: ListingId): F[Option[PropertyId]]
  def listingsFor(propertyId: PropertyId): fs2.Stream[F, PropertyListing]
}

object DynamoPropertyStore {


  private val propertyTableName = "properties"
  private val propertyTablePrimaryKey = "listingId"

  private val propertyIdIndexName = "propertyId-LSI"
  private val propertyIdKeyName = "propertyId"

  def apply[F[_]: Async](client: DynamoDbAsyncClient): PropertyStore[F] = {

    import Codecs._

    val propertiesTable                                                  = SimpleTable[F, ListingId](propertyTableName, KeyDef[ListingId](propertyTablePrimaryKey, DynamoDbType.N), client)

    val propertyIdIndex = SecondaryCompositeIndex[F, PropertyId, Long](
      propertyTableName,
      propertyIdIndexName,
      KeyDef[PropertyId](propertyIdKeyName, DynamoDbType.N),
      KeyDef[Long]("dateAdded", DynamoDbType.N),
      client
    )

    new PropertyStore[F] {

      override def propertyIdFor(listingId: ListingId): F[Option[PropertyId]] = {
        propertiesTable.get[PropertyId](listingId, consistentRead = false)
      }

      override def listingsFor(propertyId: PropertyId): fs2.Stream[F, PropertyListing] =
        propertyIdIndex.retrieve[PropertyListing](propertyId)

    }
  }
}
