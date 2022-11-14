package uk.co.thirdthing.clients

import cats.MonadThrow
import cats.effect.Async
import cats.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import uk.co.thirdthing.Rightmove.{ListingId, Price}
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Response, Status, Uri}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import uk.co.thirdthing.model.Model.{ListingStatus, TransactionType}
import uk.co.thirdthing.utils.CatsEffectUtils._
import scala.concurrent.duration._

trait RightmoveApiClient[F[_]] {

  def listingDetails(listingId: ListingId): F[Option[ListingDetails]]

}

object RightmoveApiClient {

  case class ListingDetails(
    price: Price,
    transactionTypeId: TransactionType,
    visible: Boolean,
    status: Option[ListingStatus],
    sortDate: Option[Long],
    updateDate: Long,
    rentFrequency: Option[String],
    publicsiteUrl: Uri,
    latitude: Option[Double],
    longitude: Option[Double]
  )

  object ListingDetails {
    implicit val encoder: Encoder[ListingDetails] = deriveEncoder
    implicit val decoder: Decoder[ListingDetails] = deriveDecoder
  }

  private case class ResultWrapper(result: String, property: Option[ListingDetails], errorInfo: Option[String])

  private object ResultWrapper {
    implicit val encoder: Encoder[ResultWrapper] = deriveEncoder
    implicit val decoder: Decoder[ResultWrapper] = deriveDecoder
  }

  def apply[F[_]: Async](client: Client[F], baseUrl: Uri) = new RightmoveApiClient[F] {

    implicit val logger = Slf4jLogger.getLogger[F]

    implicit private val entityDecoder: EntityDecoder[F, ResultWrapper] = jsonOf[F, ResultWrapper]

    override def listingDetails(listingId: ListingId): F[Option[ListingDetails]] = {
      val uri = (baseUrl / "api" / "propertyDetails")
        .withQueryParam("propertyId", listingId.value)
        .withQueryParam("apiApplication", "IPAD")

      client.get(uri) { response =>
        response.status match {
          case Status(200) => handleSuccessfulResponse(response, listingId)
          case Status(500) => handleErrorResponse(response, listingId)
          case Status(other) =>
            new RuntimeException(s"Unexpected response code $other from API for listing id ${listingId.value}").raiseError[F, Option[ListingDetails]]
        }
      }.withBackoffRetry(30.seconds, 5, maxRetries = 10)
    }

    private def handleSuccessfulResponse(response: Response[F], listingId: ListingId): F[Option[ListingDetails]] =
      response.as[ResultWrapper].flatMap {
        case ResultWrapper("SUCCESS", Some(details), _) =>
          logger.debug(s"Property found for $listingId").as(details.some)
        case ResultWrapper("SUCCESS", _, _) =>
          logger.warn(s"Received status SUCCESS but no property details for listingID $listingId").as(None)
        case ResultWrapper("FAILURE", _, Some("Property not found")) =>
          logger.debug(s"No property found for $listingId").as(None)
        case ResultWrapper("FAILURE", _, Some(errorInfo)) =>
          logger.warn(s"Received status FAILURE for listingID $listingId. Error Info [$errorInfo]").as(None)
        case ResultWrapper("FAILURE", _, None) =>
          logger.warn(s"Received status FAILURE for listingID $listingId with no error info").as(None)
        case ResultWrapper(otherStatus, _, _) =>
          logger.warn(s"Received unhandled status $otherStatus for listingID $listingId").as(None)
      }

    private def handleErrorResponse(response: Response[F], listingId: ListingId): F[Option[ListingDetails]] =
      response.as[String].flatMap { errorResponse =>
        if (errorResponse.contains("Something went wrong"))
          logger.debug(s"500 returned from API with 'something went wrong' message for id $listingId").as(None)
        else MonadThrow[F].raiseError(new RuntimeException(s"Unexpected 500 response from API $errorResponse for listing id $listingId"))
      }
  }
}
