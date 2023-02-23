package uk.co.thirdthing.store

import cats.effect.{IO, Resource}
import natchez.Trace.Implicits.noop
import skunk.Session
import skunk.implicits.*
import uk.co.thirdthing.model.Types.*

trait PostgresIntegration:

  def database: Resource[IO, Resource[IO, Session[IO]]] =
    Session.pooled[IO](
      host = "localhost",
      port = 5432,
      user = "postgres",
      database = "propertyhistory",
      password = Some("postgres"),
      max = 16
    )
