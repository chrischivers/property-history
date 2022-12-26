package uk.co.thirdthing.routes

import cats.effect.Concurrent
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl

object MetaRoute:

  val API_VERSION = "v1"

  def routes[F[_]: Concurrent]: HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] { case GET -> Root / "ping" =>
      Ok("pong")
    }
