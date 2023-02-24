package uk.co.thirdthing.clients

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.syntax.all.*
import fs2.text
import io.circe.parser.*
import org.http4s.client.Client
import org.http4s.{Request, Status, Uri}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.clients.RightmovePostcodeSearchHtmlClient.RightmovePostcodeSearchResult
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.utils.CatsEffectUtils.*
import scala.util.Try

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.*

trait RightmovePostcodeSearchHtmlClient[F[_]]:

  def scrapeDetails(postcode: Postcode): F[Set[RightmovePostcodeSearchResult]]

object RightmovePostcodeSearchHtmlClient:
  final case class RightmovePostcodeSearchResult(
    fullAddress: FullAddress,
    postcode: Postcode,
    listingId: Option[ListingId],
    transactions: List[RightmovePostcodeSearchTransaction]
  )

  final case class RightmovePostcodeSearchTransaction(
    price: Option[Price],
    date: Option[LocalDate],
    tenure: Option[Tenure]
  )

  def apply[F[_]: Async](client: Client[F], baseUrl: Uri): RightmovePostcodeSearchHtmlClient[F] =
    new RightmovePostcodeSearchHtmlClient[F]:

      implicit def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      import io.circe.generic.auto.*
      case class PropertyDetailsTransaction(displayPrice: String, dateSold: String, tenure: String)
      case class PropertyDetails(address: String, transactions: List[PropertyDetailsTransaction], detailUrl: String)

      override def scrapeDetails(postcode: Postcode): F[Set[RightmovePostcodeSearchResult]] =

        def paginationHelper(
          page: Int,
          accumulatedResults: Set[RightmovePostcodeSearchResult]
        ): F[Set[RightmovePostcodeSearchResult]] =
          val url = (baseUrl / "house-prices" / s"${postcode.value.toLowerCase.replace(" ", "-")}.html")
            .withQueryParam("page", page.toString)
          client
            .stream(Request.apply[F](uri = url))
            .flatMap { response =>
              response.status match
                case Status.Ok =>
                  handleByteStream(response.body).map(_.map(transform(_, postcode)))
                case Status.BadRequest =>
                  fs2.Stream
                    .eval(logger.warn(s"400 returned from url $url for postcode ${postcode.value}"))
                    .flatMap(_ => fs2.Stream.empty)
                case other =>
                  fs2.Stream.eval(
                    new RuntimeException(s"Unexpected status code ${other.code} returned from uri $url").raiseError
                  )
            }
            .compile
            .lastOrError
            .withBackoffRetry(30.seconds, 5, maxRetries = 10)
            .flatMap { results =>
              val combinedResults = results.toSet ++ accumulatedResults
              if combinedResults == accumulatedResults then accumulatedResults.pure[F]
              else paginationHelper(page + 1, combinedResults)
            }
        paginationHelper(1, Set.empty)

      private def transform(result: PropertyDetails, postcode: Postcode): RightmovePostcodeSearchResult =
        RightmovePostcodeSearchResult(
          FullAddress(result.address),
          postcode,
          extractListingId(result.detailUrl),
          result.transactions.map(tx =>
            RightmovePostcodeSearchTransaction(
              formatPrice(tx.displayPrice),
              formatDate(tx.dateSold),
              Tenure.withValueOpt(tx.tenure)
            )
          )
        )

      private def handleByteStream(stream: fs2.Stream[F, Byte]): fs2.Stream[F, List[PropertyDetails]] =
        val lineBeginsWith = "            }</script><script>window.__PRELOADED_STATE__"
        stream
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(_.startsWith(lineBeginsWith))
          .evalMap { line =>
            val trimmedLine = line.replace(s"$lineBeginsWith = ", "").split("</script>").head
            Sync[F].fromEither {
              parse(trimmedLine).flatMap(
                _.hcursor.downField("results").downField("properties").as[List[PropertyDetails]]
              )
            }
          }

      private def formatPrice(price: String): Option[Price] =
        price.filter(_.isDigit).mkString.toIntOption.map(Price(_))

      private def formatDate(date: String): Option[LocalDate] =
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
        Try(LocalDate.parse(date, formatter)).toOption

      private def extractListingId(url: String): Option[ListingId] =
        url.split("/details/").last.split("-").toList match
          case _ :: id :: _ :: Nil => id.toLongOption.map(ListingId(_))
          case _                   => None
