package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import natchez.Trace.Implicits.noop
import skunk.Session
import cats.syntax.all.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.config.JobSchedulingConfig
import java.time.LocalDateTime

trait PostgresPostcodeStoreIntegration extends munit.CatsEffectSuite with PostgresIntegration:

  case class PostcodeRecord(
    postcode: Postcode,
    inUse: Boolean,
    lockedAt: Option[LocalDateTime],
    lastScraped: Option[LocalDateTime]
  )

  private def deletePostcodesTable(session: Session[IO]) =
    session.execute(sql"DROP TABLE IF EXISTS postcodes".command).void

  private def withPostgresClient(f: Resource[IO, Session[IO]] => IO[Unit]): IO[Unit] =
    database.use { pool =>
      pool.use(deletePostcodesTable) *>
        PostgresInitializer.createPostcodesTableIfNotExisting[IO](pool) *>
        f(pool)
    }

  private val insertPostcode: Command[PostcodeRecord] =
    sql"""
         INSERT INTO postcodes(postcode, inuse, lockedat, lastscraped) VALUES
         (${varchar(10)}, $bool, ${timestamp.opt}, ${timestamp.opt})
     """.command.contramap { (pr: PostcodeRecord) =>
      pr.postcode.value ~ pr.inUse ~ pr.lockedAt ~ pr.lastScraped
    }

  def withPostgresPostcodeStore(initialState: List[PostcodeRecord])(f: PostcodeStore[IO] => IO[Unit]): Unit =
    withPostgresClient { session =>
      initialState.traverse(state => session.use(_.prepare(insertPostcode).flatMap(_.execute(state)))) *>
        f(PostgresPostcodeStore.apply[IO](session, JobSchedulingConfig.default))
    }.unsafeRunSync()
