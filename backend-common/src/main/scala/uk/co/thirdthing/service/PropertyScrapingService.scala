package uk.co.thirdthing.service

import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import uk.co.thirdthing.clients.RightmoveListingHtmlClient.RightmoveHtmlScrapeResult
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveListingHtmlClient}
import uk.co.thirdthing.service.PropertyScrapingService.ScrapeResult

import java.time.Instant

trait PropertyScrapingService[F[_]]:
  def scrape(listingId: ListingId): F[Option[ScrapeResult]]

object PropertyScrapingService:

  private val DeletedStatusCodes = Set(302, 404)

  final case class ScrapeResult(
    listingId: ListingId,
    propertyId: PropertyId,
    dateAdded: DateAdded,
    propertyDetails: PropertyDetails
  )

  def apply[F[_]: Sync](
    rightmoveApiClient: RightmoveApiClient[F],
    rightmoveHtmlClient: RightmoveListingHtmlClient[F]
  ): PropertyScrapingService[F] =
    new PropertyScrapingService[F]:

      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      override def scrape(listingId: ListingId): F[Option[ScrapeResult]] =
        logger.debug(s"Handling retrieval request for listing ${listingId.value}") *>
          OptionT(rightmoveApiClient.listingDetails(listingId)).flatMap { listingDetails =>
            OptionT.liftF(rightmoveHtmlClient.scrapeDetails(listingId)).flatMap { scrapeResult =>
              OptionT.fromOption(scrapeResult.propertyId).map { propertyId =>
                ScrapeResult(
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

  private def validateMillisTimestamp(ts: Long): Option[Long] =
    if ts >= 0 then ts.some
    else none

  private def dateAddedFrom(listingDetails: ListingDetails): DateAdded =
    val timestampMillis =
      listingDetails.sortDate
        .flatMap(validateMillisTimestamp)
        .orElse(validateMillisTimestamp(listingDetails.updateDate))
        .getOrElse(0L)
    DateAdded(Instant.ofEpochMilli(timestampMillis))
    
  private def listingStatusFrom(
    htmlPageResult: RightmoveHtmlScrapeResult,
    listingDetails: ListingDetails
  ): ListingStatus =
    if DeletedStatusCodes(htmlPageResult.statusCode) then ListingStatus.Deleted
    else if !listingDetails.visible then ListingStatus.Hidden
    else listingDetails.status.getOrElse(ListingStatus.Unknown)
