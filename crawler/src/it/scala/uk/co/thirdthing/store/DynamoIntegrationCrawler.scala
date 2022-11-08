package uk.co.thirdthing.store

import cats.effect.IO
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

trait DynamoIntegrationCrawler extends DynamoIntegration {

  case class DynamoStores(dynamoPropertyStore: PropertyStore[IO], dynamoJobStore: JobStore[IO], dynamoListingHistoryStore: ListingHistoryStore[IO])

  def withDynamoStores(existingPropertyRecords: List[PropertiesRecord] = List.empty)(f: DynamoStores => IO[Unit]): Unit =
    withDynamoClient(existingPropertyRecords)
      .map(client => DynamoStores(DynamoPropertyStore[IO](client), DynamoJobStore[IO](client), DynamoListingHistoryStore(client)))
      .use(f)
      .unsafeRunSync()

  def withDynamoStoresAndClient(existingRecords: List[PropertiesRecord] = List.empty)(f: (DynamoStores, DynamoDbAsyncClient) => IO[Unit]): Unit =
    withDynamoClient(existingRecords)
      .map(client => DynamoStores(DynamoPropertyStore[IO](client), DynamoJobStore[IO](client), DynamoListingHistoryStore(client)) -> client)
      .use {
        case (stores, client) => f(stores, client)
      }
      .unsafeRunSync()

}
