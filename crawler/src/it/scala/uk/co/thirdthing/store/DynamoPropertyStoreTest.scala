package uk.co.thirdthing.store

import cats.effect.IO
import meteor.api.hi.SimpleTable
import meteor.codec.Encoder
import meteor.syntax.RichWriteAttributeValue
import meteor.{Client, DynamoDbType, KeyDef, PartitionKeyTable}
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.model.Model.{ListingStatus, Property, PropertyDetails, TransactionType}

import java.time.Instant

class DynamoPropertyStoreTest extends munit.CatsEffectSuite with DynamoIntegrationCrawler {
  import PropertyStoreCodecs._

  val listingId  = ListingId(12345678)
  val propertyId = PropertyId(987654321)
  val dateAdded  = DateAdded(Instant.ofEpochMilli(1658264481000L))

  val details = PropertyDetails(Price(100000), TransactionType.Sale, visible = true, ListingStatus.SoldSTC, Some("weekly"), 100.5, 90.1)

  val property = Property(listingId, propertyId, dateAdded, details)

  test("Store a property, and retrieve it again") {
    withDynamoStoresAndClient() { (stores, client) =>

      val result = stores.dynamoPropertyStore.put(property).flatMap { _ =>
        SimpleTable[IO, ListingId]("properties", partitionKeyDef = KeyDef[ListingId]("listingId", DynamoDbType.N), client)
          .get[Property](listingId, consistentRead = true)
        }
      assertIO(result, Some(property))



    }
  }

}
