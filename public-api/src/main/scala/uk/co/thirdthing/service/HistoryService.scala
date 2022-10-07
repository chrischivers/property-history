package uk.co.thirdthing.service

import cats.effect.Sync
import cats.implicits._
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.RightmoveListing
import uk.co.thirdthing.store.{PropertyIdStore, PropertyListingStore}

trait HistoryService[F[_]] {
  def historyFor(id: ListingId): fs2.Stream[F, RightmoveListing]
}
object HistoryService {
  def apply[F[_]: Sync](propertyIdStore: PropertyIdStore[F], propertyListingStore: PropertyListingStore[F]): HistoryService[F] =
    new HistoryService[F] {
      override def historyFor(id: ListingId): fs2.Stream[F, RightmoveListing] =
        fs2.Stream.evals(propertyIdStore.idFor(id)).flatMap(propertyListingStore.listingsFor)
    }
}
