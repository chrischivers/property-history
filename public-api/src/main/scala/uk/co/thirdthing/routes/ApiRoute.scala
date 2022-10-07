package uk.co.thirdthing.routes

import cats.effect.Concurrent
import cats.implicits.toFlatMapOps
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.service.HistoryService
import org.http4s.circe.CirceEntityEncoder._

object ApiRoute {

  val API_VERSION = "v1"

  def routes[F[_]: Concurrent](historyService: HistoryService[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / API_VERSION / "history" / LongVar(id) =>
        val listingId = ListingId(id)
        historyService.historyFor(listingId).compile.toList.flatMap {
          case Nil => NotFound()
          case l   => Ok(l)
        }
    }
  }
}
