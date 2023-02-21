package uk.co.thirdthing.service

import cats.effect.Sync
import cats.syntax.all.*
import org.http4s.{Request, Uri}
import org.http4s.client.Client
import uk.co.thirdthing.clients.RightmoveApiClient
import uk.co.thirdthing.model.Types.{ListingId, ListingSnapshot, ThumbnailUrl}
import uk.co.thirdthing.store.PropertyStore

trait ThumbnailService[F[_]]:
  def thumbnailFor(id: ListingId): fs2.Stream[F, Byte]
  def thumbnailFor(url: ThumbnailUrl): fs2.Stream[F, Byte]

object ThumbnailService:
  case object NoThumbnailAvailable extends Throwable

  def apply[F[_]: Sync](rightmoveApiClient: RightmoveApiClient[F], httpClient: Client[F]): ThumbnailService[F] =
    new ThumbnailService[F]:
      override def thumbnailFor(id: ListingId): fs2.Stream[F, Byte] =
        fs2.Stream
          .eval(rightmoveApiClient.listingDetails(id))
          .flatMap(_.flatMap(_.photoThumbnailUrl).fold(fs2.Stream.raiseError(NoThumbnailAvailable))(thumbnailFor))

      override def thumbnailFor(url: ThumbnailUrl): fs2.Stream[F, Byte] =
        fs2.Stream.fromEither[F](Uri.fromString(url.value)).flatMap { uri =>
          httpClient
            .stream(Request.apply[F](uri = uri))
            .flatMap(_.body)
        }
