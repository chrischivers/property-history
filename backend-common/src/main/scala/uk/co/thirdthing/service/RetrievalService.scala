package uk.co.thirdthing.service

import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import uk.co.thirdthing.clients.RightmoveHtmlClient.RightmoveHtmlScrapeResult
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveHtmlClient}
import uk.co.thirdthing.service.RetrievalService.RetrievalResult

import java.time.Instant

trait RetrievalService[F[_]] {
  def retrieve(listingId: ListingId): F[Option[RetrievalResult]]
}

object RetrievalService {

  private val DeletedStatusCodes = Set(302, 404)

  final case class RetrievalResult(listingId: ListingId, propertyId: PropertyId, dateAdded: DateAdded, propertyDetails: PropertyDetails)

  def apply[F[_]: Sync](rightmoveApiClient: RightmoveApiClient[F], rightmoveHtmlClient: RightmoveHtmlClient[F]): RetrievalService[F] =
    new RetrievalService[F] {

      implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      override def retrieve(listingId: ListingId): F[Option[RetrievalResult]] =
        logger.debug(s"Handling retrieval request for listing ${listingId.value}") *>
          OptionT(rightmoveApiClient.listingDetails(listingId)).flatMap { listingDetails =>
            OptionT.liftF(rightmoveHtmlClient.scrapeDetails(listingId)).flatMap { scrapeResult =>
              OptionT.fromOption(scrapeResult.propertyId).map { propertyId =>
                RetrievalResult(
                  listingId,
                  propertyId,
                  dateAddedFrom(listingDetails),
                  PropertyDetails(
                    listingDetails.price.some,
                    listingDetails.transactionTypeId.some,
                    listingDetails.visible.some,
                    listingStatusFrom(scrapeResult, listingDetails).some,
                    listingDetails.rentFrequency,
                    listingDetails.latitude,
                    listingDetails.longitude,
                    listingDetails.photoThumbnailUrl
                  )
                )
              }
            }
          }.value
    }

  private def validateMillisTimestamp(ts: Long): Option[Long] =
    if (ts >= 0) ts.some
    else none

  private def dateAddedFrom(listingDetails: ListingDetails): DateAdded = {
    val timestampMillis =
      listingDetails.sortDate.flatMap(validateMillisTimestamp).orElse(validateMillisTimestamp(listingDetails.updateDate)).getOrElse(0L)
    DateAdded(Instant.ofEpochMilli(timestampMillis))
  }
  private def listingStatusFrom(htmlPageResult: RightmoveHtmlScrapeResult, listingDetails: ListingDetails): ListingStatus =
    if (DeletedStatusCodes(htmlPageResult.statusCode)) ListingStatus.Deleted
    else if (!listingDetails.visible) ListingStatus.Hidden
    else listingDetails.status.getOrElse(ListingStatus.Unknown)
}
