package uk.co.thirdthing.store

import cats.effect.IO

trait DynamoIntegrationPublicApi extends DynamoIntegration {

  case class DynamoStores(dynamoPropertyIdStore: PropertyIdStore[IO], dynamoPropertyListingStore: PropertyListingStore[IO])

  def withDynamoStores(existingRecords: List[PropertiesRecord] = List.empty)(f: DynamoStores => IO[Unit]) = {
    withDynamoClient(existingRecords)
      .map(client => DynamoStores(DynamoPropertyIdStore[IO](client), DynamoPropertyListingStore[IO](client)))
      .use(f)
      .unsafeRunSync()
  }

}
