package uk.co.thirdthing.routes

import cats.effect.Concurrent
import cats.implicits.toFlatMapOps
import io.circe.Json
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.{LongVar, PathVar}
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.{PropertyLookupService, ThumbnailService}

import scala.util.Try

object ApiRoute:

  def routes[F[_]: Concurrent](
                                lookupService: PropertyLookupService[F],
                                thumbnailService: ThumbnailService[F]
  ): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    object ThumbnailUrlMatcher       extends QueryParamDecoderMatcher[String]("thumbnailUrl")
    object ThumbnailListingIdMatcher extends QueryParamDecoderMatcher[Long]("listingId")

    HttpRoutes.of[F] {
      case GET -> Root / "history" / LongVar(id) =>
        val listingId = ListingId(id)
        lookupService.detailsFor(listingId).flatMap {
          case None => NotFound()
          case Some(l)   => Ok(l.asJson)
        }

      case GET -> Root / "thumbnail" :? ThumbnailUrlMatcher(url) =>
        Ok(thumbnailService.thumbnailFor(ThumbnailUrl(url)))

      case GET -> Root / "thumbnail" :? ThumbnailListingIdMatcher(id) =>
        val listingId = ListingId(id)
        Ok(thumbnailService.thumbnailFor(listingId))
    }
