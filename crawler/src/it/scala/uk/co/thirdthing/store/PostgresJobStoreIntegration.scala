package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import natchez.Trace.Implicits.noop
import skunk.Session
import skunk.implicits._
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.config.JobSchedulingConfig

trait PostgresJobStoreIntegration extends munit.CatsEffectSuite {

  private def deleteJobsTable(session: Session[IO]) =
    session.execute(sql"DROP TABLE IF EXISTS jobs".command).void

  private def database: Resource[IO, Resource[IO, Session[IO]]] =
    Session.pooled[IO](
      host = "localhost",
      port = 5432,
      user = "postgres",
      database = "propertyhistory",
      password = Some("postgres"),
      max = 16
    )

  private def withPostgresClient(f: Resource[IO, Session[IO]] => IO[Unit]): IO[Unit] =
    database.use { pool =>
      pool.use(deleteJobsTable) *>
        PostgresInitializer.createJobsTableIfNotExisting[IO](pool) *>
        f(pool)
    }

  def withPostgresJobStore(f: JobStore[IO] => IO[Unit]): Unit =
    withPostgresClient(session => f(PostgresJobStore.apply[IO](session, JobSchedulingConfig.default))).unsafeRunSync()

}
