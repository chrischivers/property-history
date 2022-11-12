package uk.co.thirdthing.store

import cats.effect.Async
import cats.effect.kernel.Sync
import cats.syntax.all._
import meteor.api.hi.SimpleTable
import meteor.syntax.RichWriteAttributeValue
import meteor.{DynamoDbType, KeyDef}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{Delete, Put, TransactWriteItem, TransactWriteItemsRequest}
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.{ListingSnapshot, Property}
import uk.co.thirdthing.utils.CatsEffectUtils._

import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

trait PropertyListingStore[F[_]] {
  def put(property: Property, listingSnapshot: ListingSnapshot): F[Unit]
  def get(listingId: ListingId): F[Option[Property]]
  def delete(listingSnapshot: ListingSnapshot): F[Unit]
}

object DynamoPropertyListingStore {
  private val propertyTableName       = "properties"
  private val propertyTablePrimaryKey = "listingId"

  private val listingHistoryTableName = "listing-history"
//  private val listingHistoryTablePrimaryKey = "listingId"
//  private val listingHistoryTableSortKey    = "lastChange"

  import Codecs._

  def apply[F[_]: Async](client: DynamoDbAsyncClient) = new PropertyListingStore[F] {

    implicit val logger = Slf4jLogger.getLogger[F]

    private val propertyTable = SimpleTable[F, ListingId](propertyTableName, KeyDef[ListingId](propertyTablePrimaryKey, DynamoDbType.N), client)

    override def get(listingId: ListingId): F[Option[Property]] =
      propertyTable.get[Property](listingId, consistentRead = false).retryWhenThroughputExceeded

    override def put(property: Property, listingSnapshot: ListingSnapshot): F[Unit] = {

      val putListingSnaphot = TransactWriteItem
        .builder()
        .put(Put.builder().tableName(listingHistoryTableName).item(listingSnapshotEncoder.write(listingSnapshot).m()).build())
        .build()
      val putProperty =
        TransactWriteItem.builder().put(Put.builder().tableName(propertyTableName).item(propertyEncoder.write(property).m()).build()).build()
      Async[F]
        .fromFuture(
          Sync[F].delay(client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(putListingSnaphot, putProperty).build()).asScala)
        )
        .void
    }.retryWhenThroughputExceeded

    override def delete(listingSnapshot: ListingSnapshot): F[Unit] = {

      val putListingSnaphot = TransactWriteItem
        .builder()
        .put(Put.builder().tableName(listingHistoryTableName).item(listingSnapshotEncoder.write(listingSnapshot).m()).build())
        .build()
      val deleteProperty =
        TransactWriteItem
          .builder()
          .delete(
            Delete
              .builder()
              .tableName(propertyTableName)
              .key(Map(propertyTablePrimaryKey -> listingSnapshot.listingId.value.asAttributeValue).asJava)
              .build()
          )
          .build()
      Async[F]
        .fromFuture(
          Sync[F]
            .delay(client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(putListingSnaphot, deleteProperty).build()).asScala)
        )
        .void
    }.retryWhenThroughputExceeded

  }
}
