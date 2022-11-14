package uk.co.thirdthing.store

import cats.effect.IO

trait PostgresIntegrationCrawler extends PostgresIntegration {

  case class PostgresStores(postgresPropertyListingStore: PropertyStore[IO])

  def withPostgresStores(existingPropertyRecords: List[PropertiesRecord] = List.empty)(f: PostgresStores => IO[Unit]): Unit =
    withPostgresClient(existingPropertyRecords)(session => f(PostgresStores(PostgresPropertyStore[IO](session)))).unsafeRunSync()

}
