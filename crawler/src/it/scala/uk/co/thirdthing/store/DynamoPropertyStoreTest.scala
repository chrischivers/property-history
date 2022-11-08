package uk.co.thirdthing.store

import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, PropertyId}
import uk.co.thirdthing.model.Model.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Model.Property
import uk.co.thirdthing.utils.Hasher.Hash

import java.time.Instant

class DynamoPropertyStoreTest extends munit.CatsEffectSuite with DynamoIntegrationCrawler {

  val listingId         = ListingId(12345678)
  val propertyId        = PropertyId(987654321)
  val dateAdded         = DateAdded(Instant.ofEpochMilli(1658264481000L))
  val listingSnapshotId = ListingSnapshotId("142352")

  val detailsHash = Hash("123546")

  val property = Property(listingId, propertyId, dateAdded, listingSnapshotId, detailsHash)

  test("Store a property, and retrieve it again") {
    withDynamoStoresAndClient() { (stores, _) =>
      val result = stores.dynamoPropertyStore.put(property).flatMap(_ => stores.dynamoPropertyStore.get(listingId))
      assertIO(result, Some(property))

    }
  }

}
