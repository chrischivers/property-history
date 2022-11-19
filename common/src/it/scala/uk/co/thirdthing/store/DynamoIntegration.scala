package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import cats.implicits.toTraverseOps
import meteor._
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import uk.co.thirdthing.model.Types.PropertyListing

import java.net.URI

trait DynamoIntegration extends munit.CatsEffectSuite {

  import Codecs._

  private def deletePropertiesTable(dynamoDbAsyncClient: DynamoDbAsyncClient) =
    Client.apply[IO](dynamoDbAsyncClient).deleteTable("properties").attempt.void

  private def deleteJobsTable(dynamoDbAsyncClient: DynamoDbAsyncClient) =
    Client.apply[IO](dynamoDbAsyncClient).deleteTable("crawler-jobs").attempt.void

  private def populateTable(dynamoDbAsyncClient: DynamoDbAsyncClient, propertiesRecords: List[PropertyListing]) = {
    val client = Client.apply[IO](dynamoDbAsyncClient)
    propertiesRecords.traverse(record => client.put[PropertyListing]("properties", record)).void
  }

  def withDynamoClient(existingPropertyRecords: List[PropertyListing] = List.empty) = {
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
      .evalTap(deleteJobsTable)
      .evalTap(Initializer.createTablesIfNotExisting[IO])
      .evalTap(populateTable(_, existingPropertyRecords))
  }
}
