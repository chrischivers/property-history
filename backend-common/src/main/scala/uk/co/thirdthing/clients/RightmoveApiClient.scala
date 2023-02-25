package uk.co.thirdthing.clients

import cats.MonadThrow
import cats.effect.Async
import cats.implicits.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder}
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Response, Status, Uri}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.utils.CatsEffectUtils.*

import scala.concurrent.duration.*

trait RightmoveApiClient[F[_]]:

  def listingDetails(listingId: ListingId): F[Option[ListingDetails]]

object RightmoveApiClient:

  case class ListingDetails(
    price: Price,
    transactionTypeId: TransactionType,
    visible: Boolean,
    status: Option[ListingStatus],
    sortDate: Option[Long],
    updateDate: Long,
    rentFrequency: Option[String],
    publicsiteUrl: Uri,
    photoThumbnailUrl: Option[ThumbnailUrl],
    latitude: Option[Double],
    longitude: Option[Double]
  )

  object ListingDetails:
    given Encoder[ListingDetails] = deriveEncoder
    given Decoder[ListingDetails] = deriveDecoder

  sealed trait ResultWrapper

  private case class SuccessResultWrapper(property: Option[ListingDetails]) extends ResultWrapper
  private case class FailureResultWrapper(errorInfo: Option[String])        extends ResultWrapper

  private object ResultWrapper:
    given Decoder[SuccessResultWrapper] = deriveDecoder
    given Decoder[FailureResultWrapper] = deriveDecoder
    given Decoder[ResultWrapper] = Decoder.instance { cursor =>
      cursor.downField("result").as[String].flatMap {
        case "SUCCESS" => cursor.as[SuccessResultWrapper]
        case "FAILURE" => cursor.as[FailureResultWrapper]
        case other     => Left(DecodingFailure(s"Unhandled result type $other", cursor.history))
      }
    }

  def apply[F[_]: Async](client: Client[F], baseUrl: Uri) = new RightmoveApiClient[F]:

    private given logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

    private given EntityDecoder[F, ResultWrapper] = jsonOf[F, ResultWrapper]

    override def listingDetails(listingId: ListingId): F[Option[ListingDetails]] =
      val uri = (baseUrl / "api" / "propertyDetails")
        .withQueryParam("propertyId", listingId.value)
        .withQueryParam("apiApplication", "IPAD")

      client
        .get(uri) { response =>
          response.status match
            case Status(200) => handleSuccessfulResponse(response, listingId)
            case Status(500) => handleErrorResponse(response, listingId)
            case Status(other) =>
              new RuntimeException(s"Unexpected response code $other from API for listing id ${listingId.value}")
                .raiseError[F, Option[ListingDetails]]
        }
        .withBackoffRetry(30.seconds, 5, maxRetries = 10)

    private def handleSuccessfulResponse(response: Response[F], listingId: ListingId): F[Option[ListingDetails]] =
      response
        .as[ResultWrapper]
        .flatMap[Option[ListingDetails]] {
          case SuccessResultWrapper(Some(details)) =>
            logger.debug(s"Property found for $listingId").as(details.some)
          case SuccessResultWrapper(_) =>
            logger.warn(s"Received status SUCCESS but no property details for listingID $listingId").as(None)
          case FailureResultWrapper(Some("Property not found")) =>
            logger.debug(s"No property found for $listingId").as(None)
          case FailureResultWrapper(Some(errorInfo)) =>
            logger.warn(s"Received status FAILURE for listingID $listingId. Error Info [$errorInfo]").as(None)
          case FailureResultWrapper(None) =>
            logger.warn(s"Received status FAILURE for listingID $listingId with no error info").as(None)
        }
        .recoverWith { case DecodingFailure(msg, _) =>
          logger.warn(msg).as(None)
        }

    private def handleErrorResponse(response: Response[F], listingId: ListingId): F[Option[ListingDetails]] =
      response.as[String].flatMap { errorResponse =>
        if errorResponse.contains("Something went wrong") then
          logger.debug(s"500 returned from API with 'something went wrong' message for id $listingId").as(None)
        else
          MonadThrow[F].raiseError(
            new RuntimeException(s"Unexpected 500 response from API $errorResponse for listing id $listingId")
          )
      }
