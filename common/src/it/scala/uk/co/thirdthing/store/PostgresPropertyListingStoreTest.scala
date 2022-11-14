package uk.co.thirdthing.store

import cats.syntax.all._
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._

import java.time.Instant
import java.time.temporal.ChronoUnit

class PostgresPropertyListingStoreTest extends munit.CatsEffectSuite with PostgresIntegrationCrawler {

  val listingId         = ListingId(12345678)
  val propertyId        = PropertyId(987654321)
  val lastChange        = LastChange(Instant.ofEpochMilli(1658264481000L))
  val dateAdded         = DateAdded(lastChange.value.minus(1, ChronoUnit.DAYS))
  val listingSnapshotId = ListingSnapshotId(142352)

  val details = PropertyDetails.from(Price(100000), TransactionType.Sale, visible = true, ListingStatus.SoldSTC, "weekly", 100.5, 90.1)

  val listingSnapshot1 = ListingSnapshot(listingId, lastChange, propertyId, dateAdded, details, listingSnapshotId.some)
  val listingSnapshot2 =
    ListingSnapshot(listingId, LastChange(lastChange.value.plusSeconds(5)), propertyId, dateAdded, details, listingSnapshotId.some)

  test("putListingSnapshot: Store a listing snapshot and property, and retrieve it again") {
    withPostgresStores() { stores =>
      val result = stores.postgresPropertyListingStore.putListingSnapshot(listingSnapshot1) *>
        stores.postgresPropertyListingStore.getMostRecentListing(listingSnapshot1.listingId)

      assertIO(result, Some(listingSnapshot1))
    }
  }
}
