package uk.co.thirdthing.store

import cats.syntax.all._
import skunk.exception.PostgresErrorException
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._

import java.time.Instant
import java.time.temporal.ChronoUnit

class PostgresPropertyListingStoreTest extends munit.CatsEffectSuite with PostgresPropertyListingStoreIntegration {

  val listingId  = ListingId(12345678)
  val propertyId = PropertyId(987654321)
  val lastChange = LastChange(Instant.ofEpochMilli(1658264481000L))
  val dateAdded  = DateAdded(lastChange.value.minus(1, ChronoUnit.DAYS))

  val details = PropertyDetails.from(
    price = Price(100000),
    transactionTypeId = TransactionType.Sale,
    visible = true,
    status = ListingStatus.SoldSTC,
    rentFrequency = "weekly",
    latitude = 100.5,
    longitude = 90.1,
    thumbnailUrl = ThumbnailUrl("http://thumbnail.com")
  )

  val listingSnapshot1 = ListingSnapshot(listingId, lastChange, propertyId, dateAdded, details, ListingSnapshotId(1).some)
  val listingSnapshot2 =
    ListingSnapshot(listingId, LastChange(lastChange.value.plusSeconds(5)), propertyId, dateAdded, details, ListingSnapshotId(2).some)

  test("putListingSnapshot: Store a listing snapshot, and retrieve it again") {
    withPostgresPropertyListingStore { store =>
      val result = store.putListingSnapshot(listingSnapshot1) *>
        store.getMostRecentListing(listingSnapshot1.listingId)

      assertIO(result, Some(listingSnapshot1))
    }
  }

  test("putListingSnapshot: Enforce constraint on listingId and lastChange") {
    withPostgresPropertyListingStore { store =>
      val result = store.putListingSnapshot(listingSnapshot1) *>
        store.putListingSnapshot(listingSnapshot1)

      interceptIO[PostgresErrorException](result).void

    }
  }

  test("getMostRecentListing: Return the most recent listing") {
    withPostgresPropertyListingStore { store =>
      val result = store.putListingSnapshot(listingSnapshot1) *>
        store.putListingSnapshot(listingSnapshot2) *>
        store.putListingSnapshot(listingSnapshot2.copy(listingId = ListingId(1485733))) *>
        store.getMostRecentListing(listingSnapshot1.listingId)

      assertIO(result, Some(listingSnapshot2))

    }
  }

  test("getMostRecentListing: Return None when there is no most recent listing") {
    withPostgresPropertyListingStore { store =>
      val result = store.putListingSnapshot(listingSnapshot1) *>
        store.getMostRecentListing(ListingId(84763092))
      assertIO(result, None)
    }
  }

  test("propertyIdFor: Get the property id for a listing id") {
    withPostgresPropertyListingStore { store =>
      val result = store.putListingSnapshot(listingSnapshot1) *>
        store.putListingSnapshot(listingSnapshot2.copy(listingId = ListingId(1485733))) *>
        store.propertyIdFor(listingSnapshot1.listingId)
      assertIO(result, Some(listingSnapshot1.propertyId))
    }
  }

  test("propertyIdFor: Return none when no listings found") {
    withPostgresPropertyListingStore { store =>
      val result = store.putListingSnapshot(listingSnapshot1) *>
        store.propertyIdFor(ListingId(854738823))
      assertIO(result, None)
    }
  }

  test("latestListingsFor: Get latest listings for a property id") {
    withPostgresPropertyListingStore { store =>
      val result = store.putListingSnapshot(listingSnapshot1) *>
        store.putListingSnapshot(listingSnapshot2) *>
        store.putListingSnapshot(listingSnapshot1.copy(listingId = ListingId(9322))) *>
        store.latestListingsFor(listingSnapshot1.propertyId).compile.toList
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
