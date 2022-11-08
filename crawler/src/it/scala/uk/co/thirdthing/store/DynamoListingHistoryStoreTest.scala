package uk.co.thirdthing.store

import cats.effect.IO
import cats.syntax.all._
import meteor.api.hi.CompositeTable
import meteor.{DynamoDbType, KeyDef}
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.model.Model.CrawlerJob.LastChange
import uk.co.thirdthing.model.Model.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Model.{ListingSnapshot, ListingStatus, PropertyDetails, TransactionType}
import scala.jdk.FutureConverters._
import scala.jdk.CollectionConverters._

import java.time.Instant
import java.time.temporal.ChronoUnit

class DynamoListingHistoryStoreTest extends munit.CatsEffectSuite with DynamoIntegrationCrawler {
  import CommonCodecs._
  import ListingHistoryStoreCodecs._

  val listingId         = ListingId(12345678)
  val propertyId        = PropertyId(987654321)
  val lastChange        = LastChange(Instant.ofEpochMilli(1658264481000L))
  val dateAdded         = DateAdded(lastChange.value.minus(1, ChronoUnit.DAYS))
  val listingSnapshotId = ListingSnapshotId("142352")

  val details = PropertyDetails(Price(100000), TransactionType.Sale, visible = true, ListingStatus.SoldSTC, Some("weekly"), 100.5, 90.1)

  val listingSnapshot1 = ListingSnapshot(listingId, lastChange, propertyId, dateAdded, listingSnapshotId, details.some)
  val listingSnapshot2 = ListingSnapshot(listingId, LastChange(lastChange.value.plusSeconds(5)), propertyId, dateAdded, listingSnapshotId, details.some)

  test("Store a listing snapshot, and retrieve it again") {
    withDynamoStoresAndClient() { (stores, client) =>
      val result = stores.dynamoListingHistoryStore.put(listingSnapshot1).flatMap { _ =>
        CompositeTable[IO, ListingId, LastChange]("listing-history", partitionKeyDef = KeyDef[ListingId]("listingId", DynamoDbType.N), sortKeyDef = KeyDef[LastChange]("lastChange", DynamoDbType.N),client)
          .get[ListingSnapshot](listingId, lastChange, consistentRead = true)
      }
      assertIO(result, Some(listingSnapshot1))
    }
  }

  test("Store multiple listing snapshots, and retrieve them again") {
    withDynamoStoresAndClient() { (stores, client) =>
      val result = stores.dynamoListingHistoryStore.putStream(fs2.Stream.emits(Seq(listingSnapshot1, listingSnapshot2))).compile.drain.flatMap { _ =>
        IO.fromFuture(IO(client.scan(ScanRequest.builder().tableName("listing-history").build()).asScala)).map{ response =>
          response.items().asScala.toList.map(listingSnapshotDecoder.read).map(_.getOrElse(fail("unable to decode")))
        }
      }
      assertIO(result, List(listingSnapshot1, listingSnapshot2))
    }
  }

}
