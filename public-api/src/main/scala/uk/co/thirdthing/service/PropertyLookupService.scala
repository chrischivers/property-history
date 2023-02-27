package uk.co.thirdthing.service

import cats.Parallel
import cats.effect.{Async, Clock, Sync}
import cats.syntax.all.*
import uk.co.thirdthing.model.Types.{DateAdded, LastChange, ListingId, ListingSnapshot, PropertyId, Transaction}
import uk.co.thirdthing.service.PropertyScrapingService.ScrapeResult
import uk.co.thirdthing.store.PropertyStore
import uk.co.thirdthing.store.AddressStore
import uk.co.thirdthing.service.PropertyLookupService.*
import uk.co.thirdthing.model.Types.*

trait PropertyLookupService[F[_]]:
  def detailsFor(id: ListingId): F[Option[PropertyLookupDetails]]

object PropertyLookupService:

  def apply[F[_]: Sync: Parallel](
    propertyStore: PropertyStore[F],
    addressStore: AddressStore[F],
    scrapingService: PropertyScrapingService[F]
  )(implicit
    clock: Clock[F]
  ): PropertyLookupService[F] =
    new PropertyLookupService[F]:
      override def detailsFor(id: ListingId): F[Option[PropertyLookupDetails]] = retrieveHistory(id)

      private def retrieveHistory(listingId: ListingId): F[Option[PropertyLookupDetails]] =
        propertyStore.propertyIdFor(listingId).flatMap {
          case Some(propertyId) => combineListingsAndAddresses(propertyId).map(_.some)
          case None             => handleUnknownListingId(listingId)
        }

      private def handleUnknownListingId(listingId: ListingId): F[Option[PropertyLookupDetails]] =
        scrapingService.scrape(listingId).flatMap {
          _.fold(Option.empty[PropertyLookupDetails].pure[F]) { scrapeResult =>
            combineListingsAndAddresses(scrapeResult.propertyId).flatMap { result =>
              listingSnapshotFrom(scrapeResult).map { retrievalResultSnapshot =>
                result.copy(listingRecords = retrievalResultSnapshot +: result.listingRecords).some
              }
            }
          }
        }

      private def combineListingsAndAddresses(propertyId: PropertyId): F[PropertyLookupDetails] =
        (
          addressStore.getAddressesFor(propertyId).compile.last,
          propertyStore.latestListingsFor(propertyId).compile.toList
        ).parMapN { case (addresses, listings) =>
          PropertyLookupDetails(
            addresses.map(_.address),
            addresses.map(_.postcode),
            listings,
            addresses.fold(List.empty)(_.transactions)
          )
        }

      private def listingSnapshotFrom(scrapeResult: ScrapeResult): F[ListingSnapshot] =
        clock.realTimeInstant.map { now =>
          ListingSnapshot(
            scrapeResult.listingId,
            LastChange(now),
            scrapeResult.propertyId,
            DateAdded(now),
            scrapeResult.propertyDetails
          )
        }
