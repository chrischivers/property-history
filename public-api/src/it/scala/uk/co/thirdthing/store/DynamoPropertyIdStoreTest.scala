package uk.co.thirdthing.store

import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, PropertyId}

import java.time.Instant

class DynamoPropertyIdStoreTest extends munit.CatsEffectSuite with DynamoIntegrationPublicApi {

  val listingId = ListingId(12345678)
  val propertyId = PropertyId(987654321)
  val dateAdded  = DateAdded(Instant.ofEpochMilli(1658264481000L))

  test("Return none when there is no corresponding property id in the store for a given listing id") { withDynamoStores() { stores =>
    assertIO(stores.dynamoPropertyIdStore.idFor(listingId), None)
  }}

  test("Return the property id when there is a corresponding property id in the store for a given listing id") {
    withDynamoStores(List(PropertiesRecord(listingId, dateAdded, propertyId, "some-url"))) { store =>
    assertIO(store.dynamoPropertyIdStore.idFor(listingId), Some(propertyId))
  }}


}
