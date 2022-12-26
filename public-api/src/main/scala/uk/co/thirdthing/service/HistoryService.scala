package uk.co.thirdthing.service

import cats.effect.Sync
import cats.implicits._
import uk.co.thirdthing.model.Types.{ListingId, ListingSnapshot}
import uk.co.thirdthing.store.PropertyStore

trait HistoryService[F[_]]:
  def historyFor(id: ListingId): fs2.Stream[F, ListingSnapshot]
  
object HistoryService:
  def apply[F[_]: Sync](propertyStore: PropertyStore[F]): HistoryService[F] =
    new HistoryService[F] {
      override def historyFor(id: ListingId): fs2.Stream[F, ListingSnapshot] =
        fs2.Stream.evals(propertyStore.propertyIdFor(id)).flatMap(propertyStore.latestListingsFor)
    }
