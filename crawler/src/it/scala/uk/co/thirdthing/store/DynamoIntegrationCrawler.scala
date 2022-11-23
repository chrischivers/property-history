package uk.co.thirdthing.store

import cats.effect.IO
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

trait DynamoIntegrationCrawler extends DynamoIntegration {

  case class DynamoStores(dynamoJobStore: JobStore[IO])

  def withDynamoStores()(f: DynamoStores => IO[Unit]): Unit =
    withDynamoClient()
      .map(client => DynamoStores(DynamoJobStore[IO](client)))
      .use(f)
      .unsafeRunSync()

  def withDynamoStoresAndClient()(f: (DynamoStores, DynamoDbAsyncClient) => IO[Unit]): Unit =
    withDynamoClient()
      .map(client => DynamoStores(DynamoJobStore[IO](client)) -> client)
      .use {
        case (stores, client) => f(stores, client)
      }
      .unsafeRunSync()

}
