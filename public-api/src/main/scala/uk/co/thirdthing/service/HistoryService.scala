package uk.co.thirdthing.service

import cats.effect.{Clock, Sync}
import cats.syntax.all.*
import uk.co.thirdthing.model.Types.{DateAdded, LastChange, ListingId, ListingSnapshot}
import uk.co.thirdthing.service.RetrievalService.RetrievalResult
import uk.co.thirdthing.store.PropertyStore

trait HistoryService[F[_]]:
  def historyFor(id: ListingId): fs2.Stream[F, ListingSnapshot]

object HistoryService:
  def apply[F[_]: Sync](propertyStore: PropertyStore[F], retrievalService: RetrievalService[F])(implicit
    clock: Clock[F]
  ): HistoryService[F] =
    new HistoryService[F]:
      override def historyFor(id: ListingId): fs2.Stream[F, ListingSnapshot] = retrievePropertyId(id)

      def retrievePropertyId(listingId: ListingId) =
        fs2.Stream.eval(propertyStore.propertyIdFor(listingId)).flatMap {
          case Some(propertyId) => propertyStore.latestListingsFor(propertyId)
          case None             => handleUnknownListingId(listingId)
        }

      def handleUnknownListingId(listingId: ListingId): fs2.Stream[F, ListingSnapshot] =
        fs2.Stream.evals(retrievalService.retrieve(listingId)).flatMap { retrievalResult =>
          fs2.Stream
            .eval(listingSnapshotFrom(retrievalResult))
            .combine(propertyStore.latestListingsFor(retrievalResult.propertyId))

        }

      def listingSnapshotFrom(result: RetrievalResult): F[ListingSnapshot] =
        clock.realTimeInstant.map { now =>
          ListingSnapshot(result.listingId, LastChange(now), result.propertyId, DateAdded(now), result.propertyDetails)
        }
