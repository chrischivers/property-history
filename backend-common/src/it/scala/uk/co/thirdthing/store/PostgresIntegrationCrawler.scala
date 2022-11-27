package uk.co.thirdthing.store

import cats.effect.IO

trait PostgresIntegrationCrawler extends PostgresIntegration {

  case class PostgresStores(postgresPropertyListingStore: PropertyStore[IO])

  def withPostgresStores()(f: PostgresStores => IO[Unit]): Unit =
    withPostgresClient()(session => f(PostgresStores(PostgresPropertyStore[IO](session)))).unsafeRunSync()

}
