package uk.co.thirdthing.store

import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.utils.Hasher.Hash

import java.time.Instant
import java.time.temporal.ChronoUnit

class DynamoPropertyStoreIntegrationTestListing extends munit.CatsEffectSuite with DynamoIntegrationPublicApi {

  private val listingId              = ListingId(12345678)
  private val propertyId             = PropertyId(987654321)
  private val dateAdded              = DateAdded(Instant.ofEpochMilli(1658264481000L))
  private val snapshotId             = ListingSnapshotId.generate
  private val hash                   = Hash("blah")
  private val defaultPropertyListing = PropertyListing(listingId, propertyId, dateAdded, snapshotId, hash)

  test("propertyIdFor: Return none when there is no corresponding property id in the store for a given listing id") {
    withDynamoStores()(stores => assertIO(stores.dynamoPropertyIdStore.propertyIdFor(listingId), None))
  }

  test("propertyIdFor: Return the property id when there is a corresponding property id in the store for a given listing id") {
    withDynamoStores(List(defaultPropertyListing))(store => assertIO(store.dynamoPropertyIdStore.propertyIdFor(listingId), Some(propertyId)))
  }

  test("listingsFor: Return an empty list when there is no corresponding property id in the uk.co.thirdthing.store") {
    withDynamoStores()(stores => assertIO(stores.dynamoPropertyIdStore.listingsFor(propertyId).compile.toList, List.empty))
  }

  test("listingsFor: Return the record when there is a corresponding property id in the uk.co.thirdthing.store") {

    withDynamoStores(List(defaultPropertyListing)) { stores =>
      assertIO(stores.dynamoPropertyIdStore.listingsFor(propertyId).compile.toList, List(defaultPropertyListing))
    }
  }

  test("listingsFor: Return multiple records when there is a corresponding property id in the uk.co.thirdthing.store") {

    val listingId2       = ListingId(3456789)
    val dateAdded2       = DateAdded(dateAdded.value.minus(2, ChronoUnit.DAYS))
    val propertyListing2 = PropertyListing(listingId2, propertyId, dateAdded2, ListingSnapshotId.generate, Hash("blah"))

    withDynamoStores(
      List(
        defaultPropertyListing,
        propertyListing2
      )
    ) { stores =>
      assertIO(
        stores.dynamoPropertyIdStore.listingsFor(propertyId).compile.toList,
        List(
          propertyListing2,
          defaultPropertyListing
        )
      )
    }
  }

}
