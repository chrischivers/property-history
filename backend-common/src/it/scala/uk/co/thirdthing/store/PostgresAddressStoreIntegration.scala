package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import natchez.Trace.Implicits.noop
import skunk.Session
import skunk.implicits.*
import uk.co.thirdthing.model.Types.*

trait PostgresAddressStoreIntegration extends munit.CatsEffectSuite with PostgresIntegration:

  private def deleteAddressesTable(session: Session[IO]) =
    session.execute(sql"DROP TABLE IF EXISTS addresses".command).void

  private def withPostgresClient(f: Resource[IO, Session[IO]] => IO[Unit]): IO[Unit] =
    database.use { pool =>
      pool.use(deleteAddressesTable) *>
        PostgresInitializer.createAddressesTableIfNotExisting[IO](pool) *>
        f(pool)
    }

  def withPostgresAddressStore(f: AddressStore[IO] => IO[Unit]): Unit =
    withPostgresClient(session => f(PostgresAddressStore.apply[IO](session))).unsafeRunSync()
