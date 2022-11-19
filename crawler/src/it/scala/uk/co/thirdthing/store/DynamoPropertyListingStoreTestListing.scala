package uk.co.thirdthing.store

import cats.effect.IO
import cats.syntax.all._
import meteor.api.hi.{CompositeTable, SimpleTable}
import meteor.{DynamoDbType, KeyDef}
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.utils.Hasher.Hash

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

class DynamoPropertyListingStoreTestListing extends munit.CatsEffectSuite with DynamoIntegrationCrawler {
  import Codecs._

  val listingId         = ListingId(12345678)
  val propertyId        = PropertyId(987654321)
  val lastChange        = LastChange(Instant.ofEpochMilli(1658264481000L))
  val dateAdded         = DateAdded(lastChange.value.minus(1, ChronoUnit.DAYS))
  val listingSnapshotId = ListingSnapshotId("142352")

  val details = PropertyDetails(Price(100000), TransactionType.Sale, visible = true, ListingStatus.SoldSTC, Some("weekly"), 100.5.some, 90.1.some)

  val listingSnapshot1 = ListingSnapshot(listingId, lastChange, propertyId, dateAdded, listingSnapshotId, details.some)
  val listingSnapshot2 =
    ListingSnapshot(listingId, LastChange(lastChange.value.plusSeconds(5)), propertyId, dateAdded, listingSnapshotId, details.some)

  val detailsHash = Hash("123546")

  val property1 = PropertyListing(listingId, propertyId, dateAdded, listingSnapshotId, detailsHash)
  val property2 = PropertyListing(listingId, propertyId, dateAdded, listingSnapshotId, detailsHash)

  test("Store a listing snapshot and property, and retrieve it again") {
    withDynamoStoresAndClient() { (stores, client) =>
      val result = stores.dynamoPropertyListingStore.put(property1, listingSnapshot1) *>
        CompositeTable[IO, ListingId, LastChange](
          "listing-history",
          partitionKeyDef = KeyDef[ListingId]("listingId", DynamoDbType.N),
          sortKeyDef = KeyDef[LastChange]("lastChange", DynamoDbType.N),
          client
        ).get[ListingSnapshot](listingId, lastChange, consistentRead = true)
          .flatMap(listingSnapshot =>
            SimpleTable[IO, ListingId]("properties", KeyDef[ListingId]("listingId", DynamoDbType.N), client)
              .get[PropertyListing](listingId, consistentRead = true)
              .map(_ -> listingSnapshot)
          )

      assertIO(result, (Some(property1), Some(listingSnapshot1)))
    }
  }

  test("Store multiple listing snapshots, and retrieve them again") {
    withDynamoStoresAndClient() { (stores, client) =>
      val result = stores.dynamoPropertyListingStore.put(property1, listingSnapshot1) *>
        stores.dynamoPropertyListingStore.put(property2, listingSnapshot2) *>
        IO.fromFuture(IO(client.scan(ScanRequest.builder().tableName("listing-history").build()).asScala))
          .map(response => response.items().asScala.toList.map(listingSnapshotDecoder.read).map(_.getOrElse(fail("unable to decode"))))
          .flatMap { listingSnapshots =>
            IO.fromFuture(IO(client.scan(ScanRequest.builder().tableName("properties").build()).asScala))
              .map(response => response.items().asScala.toList.map(propertyDecoder.read).map(_.getOrElse(fail("unable to decode"))))
              .map(_ -> listingSnapshots)
          }
      assertIO(result, (List(property1), List(listingSnapshot1, listingSnapshot2)))
    }
  }

}
