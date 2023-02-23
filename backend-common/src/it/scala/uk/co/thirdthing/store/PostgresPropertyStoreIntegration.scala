package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import natchez.Trace.Implicits.noop
import skunk.Session
import skunk.implicits.*
import uk.co.thirdthing.model.Types.*

trait PostgresPropertyStoreIntegration extends munit.CatsEffectSuite with PostgresIntegration:

  case class PropertiesRecord(listingId: ListingId, dateAdded: DateAdded, propertyId: PropertyId, url: String)

  private def deletePropertiesTable(session: Session[IO]) =
    session.execute(sql"DROP TABLE IF EXISTS properties".command).void

  private def withPostgresClient(f: Resource[IO, Session[IO]] => IO[Unit]): IO[Unit] =
    database.use { pool =>
      pool.use(deletePropertiesTable) *>
        PostgresInitializer.createPropertiesTableIfNotExisting[IO](pool) *>
        f(pool)
    }

  def withPostgresPropertyListingStore(f: PropertyStore[IO] => IO[Unit]): Unit =
    withPostgresClient(session => f(PostgresPropertyStore.apply[IO](session))).unsafeRunSync()
