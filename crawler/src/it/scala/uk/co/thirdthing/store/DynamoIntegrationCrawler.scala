package uk.co.thirdthing.store

import cats.effect.IO
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import uk.co.thirdthing.model.Types.PropertyListing

trait DynamoIntegrationCrawler extends DynamoIntegration {

  case class DynamoStores(dynamoPropertyListingStore: PropertyListingStore[IO], dynamoJobStore: JobStore[IO])

  def withDynamoStores(existingPropertyRecords: List[PropertyListing] = List.empty)(f: DynamoStores => IO[Unit]): Unit =
    withDynamoClient(existingPropertyRecords)
      .map(client => DynamoStores(DynamoPropertyListingStore[IO](client), DynamoJobStore[IO](client)))
      .use(f)
      .unsafeRunSync()

  def withDynamoStoresAndClient(existingRecords: List[PropertyListing] = List.empty)(f: (DynamoStores, DynamoDbAsyncClient) => IO[Unit]): Unit =
    withDynamoClient(existingRecords)
      .map(client => DynamoStores(DynamoPropertyListingStore[IO](client), DynamoJobStore[IO](client)) -> client)
      .use {
        case (stores, client) => f(stores, client)
      }
      .unsafeRunSync()

}
