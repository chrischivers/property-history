package uk.co.thirdthing.store

import cats.effect.Async
import cats.syntax.all._
import meteor._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.co.thirdthing.model.Types.ListingId

import java.time.Instant

object Initializer {

  def createTablesIfNotExisting[F[_]: Async](dynamoDbAsyncClient: DynamoDbAsyncClient): F[Unit] = {

    val recoverIfAlreadyExists: PartialFunction[Throwable, F[Unit]] = {
      case _: ResourceInUseException => ().pure[F]
    }

    createJobsTable(dynamoDbAsyncClient).recoverWith(recoverIfAlreadyExists) *>
      createPropertiesTable(dynamoDbAsyncClient).recoverWith(recoverIfAlreadyExists) *>
      createListingHistoryTable(dynamoDbAsyncClient).recoverWith(recoverIfAlreadyExists)
  }

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
