package uk.co.thirdthing.service

import cats.data.OptionT
import cats.effect.Sync
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, PropertyId}
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import uk.co.thirdthing.clients.RightmoveHtmlClient.RightmoveHtmlScrapeResult
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveHtmlClient}
import uk.co.thirdthing.model.Model.{ListingStatus, Property, PropertyDetails}
import uk.co.thirdthing.service.RetrievalService.RetrievalResult

import java.time.Instant

trait RetrievalService[F[_]] {
  def retrieve(listingId: ListingId): F[Option[RetrievalResult]]
}

object RetrievalService {

  private val DeletedStatusCodes = Set(302, 404)

  final case class RetrievalResult(listingId: ListingId, propertyId: PropertyId, dateAdded: DateAdded,  propertyDetails: PropertyDetails)

  def apply[F[_]: Sync](rightmoveApiClient: RightmoveApiClient[F], rightmoveHtmlClient: RightmoveHtmlClient[F]): RetrievalService[F] =
    new RetrievalService[F] {
      override def retrieve(listingId: ListingId): F[Option[RetrievalResult]] =
        OptionT(rightmoveApiClient.listingDetails(listingId)).flatMap { listingDetails =>
          OptionT.liftF(rightmoveHtmlClient.scrapeDetails(listingId)).flatMap { scrapeResult =>
            OptionT.fromOption(scrapeResult.propertyId).map { propertyId =>
              RetrievalResult(
                listingId,
                propertyId,
                DateAdded(Instant.ofEpochMilli(listingDetails.sortDate.getOrElse(listingDetails.updateDate))),
                PropertyDetails(
                  listingDetails.price,
                  listingDetails.transactionTypeId,
                  listingDetails.visible,
                  listingStatusFrom(scrapeResult, listingDetails),
                  listingDetails.rentFrequency,
                  listingDetails.latitude,
                  listingDetails.longitude
                )
              )
            }
          }
        }.value
    }
  private def listingStatusFrom(htmlPageResult: RightmoveHtmlScrapeResult, listingDetails: ListingDetails): ListingStatus = {

    if (DeletedStatusCodes(htmlPageResult.statusCode)) ListingStatus.Deleted
    else if (!listingDetails.visible) ListingStatus.Hidden
    else listingDetails.status.getOrElse(ListingStatus.Unknown)
  }
}
