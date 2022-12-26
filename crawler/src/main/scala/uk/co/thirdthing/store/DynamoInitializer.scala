package uk.co.thirdthing.store

import cats.effect.Async
import cats.syntax.all._
import meteor._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.co.thirdthing.model.Types.ListingId

object DynamoInitializer {

  def createDynamoTablesIfNotExisting[F[_]: Async](dynamoDbAsyncClient: DynamoDbAsyncClient): F[Unit] = {

    val recoverIfAlreadyExists: PartialFunction[Throwable, F[Unit]] = { case _: ResourceInUseException =>
      ().pure[F]
    }

    createJobsTable(dynamoDbAsyncClient).recoverWith(recoverIfAlreadyExists)
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

}
