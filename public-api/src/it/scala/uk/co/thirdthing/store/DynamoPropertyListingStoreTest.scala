package uk.co.thirdthing.store

import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, PropertyId}
import uk.co.thirdthing.model.Model.RightmoveListing

import java.time.Instant
import java.time.temporal.ChronoUnit

class DynamoPropertyListingStoreTest extends munit.CatsEffectSuite with DynamoIntegrationPublicApi {

  val listingId = ListingId(12345678)
  val propertyId = PropertyId(987654321)
  val dateAdded  = DateAdded(Instant.ofEpochMilli(1658264481000L))

  test("Return an empty list when there is no corresponding property id in the uk.co.thirdthing.store") {
    withDynamoStores() { stores =>
      assertIO(stores.dynamoPropertyListingStore.listingsFor(propertyId).compile.toList, List.empty)
    }
  }

  test("Return the record when there is a corresponding property id in the uk.co.thirdthing.store") {

    withDynamoStores(List(PropertiesRecord(listingId, dateAdded, propertyId, "some-url"))) { stores =>
      assertIO(stores.dynamoPropertyListingStore.listingsFor(propertyId).compile.toList, List(RightmoveListing(listingId, "some-url", dateAdded)))
    }
  }

  test("Return multiple records when there is a corresponding property id in the uk.co.thirdthing.store") {

    val listingId2  = ListingId(3456789)
    val dateAdded2  = DateAdded(dateAdded.value.minus(2, ChronoUnit.DAYS))

    withDynamoStores(
      List(
        PropertiesRecord(listingId, dateAdded, propertyId, "some-url-1"),
        PropertiesRecord(listingId2, dateAdded2, propertyId, "some-url-2")
      )
    ) { stores =>
      assertIO(
        stores.dynamoPropertyListingStore.listingsFor(propertyId).compile.toList,
        List(
          RightmoveListing(listingId2, "some-url-2", dateAdded2),
          RightmoveListing(listingId, "some-url-1", dateAdded)
        )
      )
    }
  }

}
