package uk.co.thirdthing.store

import cats.effect.IO
import uk.co.thirdthing.model.Types.PropertyListing

trait DynamoIntegrationPublicApi extends DynamoIntegration {

  case class DynamoStores(dynamoPropertyIdStore: PropertyStore[IO])

  def withDynamoStores(existingRecords: List[PropertyListing] = List.empty)(f: DynamoStores => IO[Unit]) =
    withDynamoClient(existingRecords)
      .map(client => DynamoStores(DynamoPropertyStore[IO](client)))
      .use(f)
      .unsafeRunSync()

}
