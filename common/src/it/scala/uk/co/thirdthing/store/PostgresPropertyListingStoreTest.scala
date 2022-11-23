package uk.co.thirdthing.store

import cats.syntax.all._
import skunk.exception.PostgresErrorException
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._

import java.time.Instant
import java.time.temporal.ChronoUnit

class PostgresPropertyListingStoreTest extends munit.CatsEffectSuite with PostgresIntegrationCrawler {

  val listingId  = ListingId(12345678)
  val propertyId = PropertyId(987654321)
  val lastChange = LastChange(Instant.ofEpochMilli(1658264481000L))
  val dateAdded  = DateAdded(lastChange.value.minus(1, ChronoUnit.DAYS))

  val details = PropertyDetails.from(Price(100000), TransactionType.Sale, visible = true, ListingStatus.SoldSTC, "weekly", 100.5, 90.1)

  val listingSnapshot1 = ListingSnapshot(listingId, lastChange, propertyId, dateAdded, details, ListingSnapshotId(1).some)
  val listingSnapshot2 =
    ListingSnapshot(listingId, LastChange(lastChange.value.plusSeconds(5)), propertyId, dateAdded, details, ListingSnapshotId(2).some)

  test("putListingSnapshot: Store a listing snapshot, and retrieve it again") {
    withPostgresStores() { stores =>
      val result = stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1) *>
        stores.postgresPropertyListingStore.getMostRecentListing(listingSnapshot1.listingId)

      assertIO(result, Some(listingSnapshot1))
    }
  }

  test("putListingSnapshot: Enforce constraint on listingId and lastChange") {
    withPostgresStores() { stores =>
      val result = stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1) *>
        stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1)

      interceptIO[PostgresErrorException](result).void

    }
  }

  test("getMostRecentListing: Return the most recent listing") {
    withPostgresStores() { stores =>
      val result = stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1) *>
        stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot2) *>
        stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot2.copy(listingId = ListingId(1485733))) *>
        stores.postgresPropertyListingStore.getMostRecentListing(listingSnapshot1.listingId)

      assertIO(result, Some(listingSnapshot2))

    }
  }

  test("getMostRecentListing: Return None when there is no most recent listing") {
    withPostgresStores() { stores =>
      val result = stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1) *>
        stores.postgresPropertyListingStore.getMostRecentListing(ListingId(84763092))
      assertIO(result, None)
    }
  }

  test("propertyIdFor: Get the property id for a listing id") {
    withPostgresStores() { stores =>
      val result = stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1) *>
        stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot2.copy(listingId = ListingId(1485733))) *>
        stores.postgresPropertyListingStore.propertyIdFor(listingSnapshot1.listingId)
      assertIO(result, Some(listingSnapshot1.propertyId))
    }
  }

  test("propertyIdFor: Return none when no listings found") {
    withPostgresStores() { stores =>
      val result = stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1) *>
        stores.postgresPropertyListingStore.propertyIdFor(ListingId(854738823))
      assertIO(result, None)
    }
  }

  test("latestListingsFor: Get latest listings for a property id") {
    withPostgresStores() { stores =>
      val result = stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1) *>
        stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot2) *>
        stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1.copy(listingId = ListingId(9322))) *>
        stores.postgresPropertyListingStore.latestListingsFor(listingSnapshot1.propertyId).compile.toList
      assertIO(
        result,
        List(
          listingSnapshot2.copy(listingSnapshotId = ListingSnapshotId(2).some),
          listingSnapshot1.copy(listingId = ListingId(9322), listingSnapshotId = ListingSnapshotId(3).some)
        )
      )
    }
  }
}
