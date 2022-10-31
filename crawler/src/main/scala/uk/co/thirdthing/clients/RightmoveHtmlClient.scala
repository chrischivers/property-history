package uk.co.thirdthing.clients

import cats.effect.Sync
import cats.syntax.all._
import fs2.text
import io.circe.parser._
import org.http4s.client.Client
import org.http4s.{Request, Status, Uri}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.Rightmove.{ListingId, PropertyId}
import uk.co.thirdthing.clients.RightmoveHtmlClient.RightmoveHtmlScrapeResult

trait RightmoveHtmlClient[F[_]] {

  def scrapeDetails(listingId: ListingId): F[RightmoveHtmlScrapeResult]

}

object RightmoveHtmlClient {
  final case class RightmoveHtmlScrapeResult(statusCode: Int, propertyId: Option[PropertyId])

  def apply[F[_] : Sync](client: Client[F], baseUrl: Uri): RightmoveHtmlClient[F] = new RightmoveHtmlClient[F] {

    implicit def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

    override def scrapeDetails(listingId: ListingId): F[RightmoveHtmlScrapeResult] = {
      val uri = (baseUrl / "properties" / listingId.value).withQueryParam("channel", "RES_BUY")
      client
        .stream(Request.apply[F](uri = uri))
        .flatMap { response =>
          response.status match {
            case s@(Status.NotFound | Status.Found) =>
              fs2.Stream.emit[F, RightmoveHtmlScrapeResult](RightmoveHtmlScrapeResult(s.code, None))
            case s@(Status.Gone | Status.Ok) => handleByteStream(response.body).map(dpid => RightmoveHtmlScrapeResult(s.code, dpid))
            case other =>
              fs2.Stream.eval(new RuntimeException(s"Unexpected status code ${other.code} returned from uri $uri").raiseError)
          }
        }
        .compile
        .lastOrError
    }

    private def handleByteStream(stream: fs2.Stream[F, Byte]): fs2.Stream[F, Option[PropertyId]] = {
      val lineBeginsWith = "    window.PAGE_MODEL"
      stream
        .through(text.utf8.decode)
        .through(text.lines)
        .filter(_.startsWith(lineBeginsWith))
        .evalMap { line =>
          val trimmedLine = line.replace(s"$lineBeginsWith = ", "")
          Sync[F].fromEither {
            parse(trimmedLine).flatMap(_.hcursor.downField("propertyData").downField("address").downField("deliveryPointId").as[Option[PropertyId]])
          }
        }
    }

  }
}
