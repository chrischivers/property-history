package uk.co.thirdthing.store

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all._
import meteor._
import skunk.Session
import skunk.implicits._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.co.thirdthing.model.Types.ListingId

import java.time.Instant

object Initializer {

  def createDynamoTablesIfNotExisting[F[_]: Async](dynamoDbAsyncClient: DynamoDbAsyncClient): F[Unit] = {

    val recoverIfAlreadyExists: PartialFunction[Throwable, F[Unit]] = {
      case _: ResourceInUseException => ().pure[F]
    }

    createJobsTable(dynamoDbAsyncClient).recoverWith(recoverIfAlreadyExists) *>
      createPropertiesTable(dynamoDbAsyncClient).recoverWith(recoverIfAlreadyExists) *>
      createListingHistoryTable(dynamoDbAsyncClient).recoverWith(recoverIfAlreadyExists)
  }



  def createPostgresTablesIfNotExisting[F[_]: Sync](pool: Resource[F, Session[F]]) = {
    val createPropertiesTable =
      sql"""CREATE TABLE IF NOT EXISTS properties(
         recordId BIGSERIAL NOT NULL PRIMARY KEY,
         listingId BIGINT NOT NULL,
         propertyId BIGINT NOT NULL,
         dateAdded TIMESTAMP NOT NULL,
         lastChange TIMESTAMP NOT NULL,
         price INTEGER,
         transactionTypeId INTEGER,
         visible BOOLEAN,
         listingStatus VARCHAR(24),
         rentFrequency VARCHAR(32),
         latitude DOUBLE PRECISION,
         longitude DOUBLE PRECISION,
         CONSTRAINT listingId_lastChange_unique UNIQUE (listingId, lastChange)
         )""".command


    val createPropertyIdIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS property_id_date_added_idx
              ON properties (propertyId, dateAdded);
              """.command

    val createListingIdIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS listing_id_idx
              ON properties (listingId);
              """.command



    pool.use(_.execute(createPropertiesTable)) *>
      pool.use(_.execute(createPropertyIdIndex)) *>
      pool.use(_.execute(createListingIdIndex))
  }

  private def createPropertiesTable[F[_]: Async](dynamoDbAsyncClient: DynamoDbAsyncClient) =
    Client
      .apply[F](dynamoDbAsyncClient)
      .createPartitionKeyTable(
        tableName = "properties",
        partitionKeyDef = KeyDef[ListingId]("listingId", DynamoDbType.N),
        billingMode = BillingMode.PAY_PER_REQUEST,
        attributeDefinition = Map(
          "propertyId" -> DynamoDbType.N,
          "listingId"  -> DynamoDbType.N,
          "dateAdded"  -> DynamoDbType.N
        ),
        globalSecondaryIndexes = Set(
          GlobalSecondaryIndex
            .builder()
            .indexName("propertyId-LSI")
            .keySchema(
              KeySchemaElement.builder().attributeName("propertyId").keyType(KeyType.HASH).build(),
              KeySchemaElement.builder().attributeName("dateAdded").keyType(KeyType.RANGE).build()
            )
            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
            .build()
        )
      )

  private def createJobsTable[F[_]: Async](dynamoDbAsyncClient: DynamoDbAsyncClient) =
    Client
      .apply[F](dynamoDbAsyncClient)
      .createPartitionKeyTable(
        tableName = "crawler-jobs",
        partitionKeyDef = KeyDef[ListingId]("jobId", DynamoDbType.N),
        billingMode = BillingMode.PAY_PER_REQUEST,
        attributeDefinition = Map(
          "jobId" -> DynamoDbType.N,
          "type"  -> DynamoDbType.S,
          "to"    -> DynamoDbType.N
        ),
        globalSecondaryIndexes = Set(
          GlobalSecondaryIndex
            .builder()
            .indexName("jobsByToDate-GSI")
            .keySchema(
              KeySchemaElement.builder().attributeName("type").keyType(KeyType.HASH).build(),
              KeySchemaElement.builder().attributeName("to").keyType(KeyType.RANGE).build()
            )
            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
            .build()
        )
      )

  private def createListingHistoryTable[F[_]: Async](dynamoDbAsyncClient: DynamoDbAsyncClient) =
    Client
      .apply[F](dynamoDbAsyncClient)
      .createCompositeKeysTable(
        tableName = "listing-history",
        partitionKeyDef = KeyDef[ListingId]("listingId", DynamoDbType.N),
        sortKeyDef = KeyDef[Instant]("lastChange", DynamoDbType.N),
        billingMode = BillingMode.PAY_PER_REQUEST
      )

}
