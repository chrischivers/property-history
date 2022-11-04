package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import cats.implicits.toTraverseOps
import meteor._
import meteor.codec.Encoder
import meteor.syntax._
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, PropertyId}

import java.net.URI

trait DynamoIntegration extends munit.CatsEffectSuite {

  case class PropertiesRecord(listingId: ListingId, dateAdded: DateAdded, propertyId: PropertyId, url: String)

  implicit private val encoder: Encoder[PropertiesRecord] = Encoder.instance { pr =>
    Map(
      "listingId" -> pr.listingId.value.asAttributeValue,
      "dateAdded" -> pr.dateAdded.value.asAttributeValue,
      "propertyId" -> pr.propertyId.value.asAttributeValue,
      "url" -> pr.url.asAttributeValue
    ).asAttributeValue

  }

  private def deletePropertiesTable(dynamoDbAsyncClient: DynamoDbAsyncClient) = {
    Client.apply[IO](dynamoDbAsyncClient).deleteTable("properties").attempt.void
  }

  private def deleteJobsTable(dynamoDbAsyncClient: DynamoDbAsyncClient) = {
    Client.apply[IO](dynamoDbAsyncClient).deleteTable("crawler-jobs").attempt.void
  }

  private def createPropertiesTable(dynamoDbAsyncClient: DynamoDbAsyncClient) =
    Client
      .apply[IO](dynamoDbAsyncClient)
      .createPartitionKeyTable(
        tableName = "properties",
        partitionKeyDef = KeyDef[ListingId]("listingId", DynamoDbType.N),
        billingMode = BillingMode.PAY_PER_REQUEST,
        attributeDefinition = Map(
          "propertyId" -> DynamoDbType.N,
          "listingId" -> DynamoDbType.N,
          "dateAdded" -> DynamoDbType.N
        ),
        globalSecondaryIndexes = Set(
          GlobalSecondaryIndex.builder()
            .indexName("propertyId-LSI")
            .keySchema(
              KeySchemaElement.builder().attributeName("propertyId").keyType(KeyType.HASH).build(),
              KeySchemaElement.builder().attributeName("dateAdded").keyType(KeyType.RANGE).build()
            )
            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
            .build()
        )
      )

  private def createJobsTable(dynamoDbAsyncClient: DynamoDbAsyncClient) =
    Client
      .apply[IO](dynamoDbAsyncClient)
      .createPartitionKeyTable(
        tableName = "crawler-jobs",
        partitionKeyDef = KeyDef[ListingId]("jobId", DynamoDbType.N),
        billingMode = BillingMode.PAY_PER_REQUEST,
        attributeDefinition = Map(
          "jobId" -> DynamoDbType.N,
          "type" -> DynamoDbType.S,
          "to" -> DynamoDbType.N
        ),
        globalSecondaryIndexes = Set(
          GlobalSecondaryIndex.builder()
            .indexName("jobsByToDate-GSI")
            .keySchema(
              KeySchemaElement.builder().attributeName("type").keyType(KeyType.HASH).build(),
              KeySchemaElement.builder().attributeName("to").keyType(KeyType.RANGE).build()
            )
            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
            .build()
        )
      )

  private def populateTable(dynamoDbAsyncClient: DynamoDbAsyncClient, propertiesRecords: List[PropertiesRecord]) = {
    val client = Client.apply[IO](dynamoDbAsyncClient)
      propertiesRecords.traverse(record => client.put[PropertiesRecord]("properties", record)).void
  }

  def withDynamoClient(existingPropertyRecords: List[PropertiesRecord] = List.empty) = {
    val dummyCreds = AwsBasicCredentials.create("dummy-access-key", "dummy-secret-key")
    Resource
      .fromAutoCloseable(
        IO(
          DynamoDbAsyncClient
            .builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(dummyCreds))
            .endpointOverride(new URI("http://localhost:4566"))
            .build()
        )
      )
      .evalTap(deletePropertiesTable)
      .evalTap(createPropertiesTable)
      .evalTap(deleteJobsTable)
      .evalTap(createJobsTable)
      .evalTap(populateTable(_, existingPropertyRecords))
  }
}
