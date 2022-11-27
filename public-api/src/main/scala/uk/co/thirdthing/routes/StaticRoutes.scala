package uk.co.thirdthing.routes

import cats.effect.IO
import cats.effect.kernel.Sync
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, StaticFile}

import java.nio.file.Paths


object StaticRoutes {


  def routes[F[_]: Sync]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    val ACCEPTABLE_ENDINGS = Set(".js", "js.map")

    val indexHtml = StaticFile
      .fromResource[F](
        name = "index.html",
        req = None,
        preferGzipped = true
      )
      .getOrElseF(NotFound())


    HttpRoutes.of[F] {
      case req@GET -> Root / "assets" / filename if ACCEPTABLE_ENDINGS.exists(filename.endsWith) =>
        StaticFile
          .fromResource[F](
            Paths.get("assets", filename).toString,
            Some(req),
            preferGzipped = true
          )
          .getOrElseF(NotFound())
      case req if req.method == GET => indexHtml

    }
  }
}