package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import natchez.Trace.Implicits.noop
import skunk.Session
import skunk.implicits._
import uk.co.thirdthing.model.Types._

trait PostgresIntegration extends munit.CatsEffectSuite {

  case class PropertiesRecord(listingId: ListingId, dateAdded: DateAdded, propertyId: PropertyId, url: String)

  private def deletePropertiesTable(session: Session[IO]) =
    session.execute(sql"DROP TABLE IF EXISTS properties".command).void

  private def database: Resource[IO, Resource[IO, Session[IO]]] =
    Session.pooled[IO](
      host = "localhost",
      port = 5432,
      user = "postgres",
      database = "propertyhistory",
      password = Some("postgres"),
      max = 16
    )

  def withPostgresClient()(f: Resource[IO, Session[IO]] => IO[Unit]): IO[Unit] =
    database.use { pool =>
      pool.use(deletePropertiesTable) *>
        PostgresInitializer.createPostgresTablesIfNotExisting[IO](pool) *>
        f(pool)
    }
}
