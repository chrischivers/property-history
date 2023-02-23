package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import natchez.Trace.Implicits.noop
import skunk.Session
import skunk.implicits.*
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.config.JobSchedulingConfig

trait PostgresJobStoreIntegration extends munit.CatsEffectSuite with PostgresIntegration:

  private def deleteJobsTable(session: Session[IO]) =
    session.execute(sql"DROP TABLE IF EXISTS jobs".command).void

  private def withPostgresClient(f: Resource[IO, Session[IO]] => IO[Unit]): IO[Unit] =
    database.use { pool =>
      pool.use(deleteJobsTable) *>
        PostgresInitializer.createJobsTableIfNotExisting[IO](pool) *>
        f(pool)
    }

  def withPostgresJobStore(f: JobStore[IO] => IO[Unit]): Unit =
    withPostgresClient(session => f(PostgresJobStore.apply[IO](session, JobSchedulingConfig.default))).unsafeRunSync()
