package uk.co.thirdthing.utils

import cats.effect.{IO, Ref}
import cats.syntax.all._
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.CrawlerJob.LastChange
import uk.co.thirdthing.model.Model.{ListingSnapshot, Property}
import uk.co.thirdthing.store.PropertyListingStore

object MockPropertyListingStore {

  def apply(
    propertyRecords: Ref[IO, Map[ListingId, Property]],
    listingRecords: Ref[IO, Map[(ListingId, LastChange), ListingSnapshot]]
  ): IO[PropertyListingStore[IO]] = {

    val propertyListingStore = new PropertyListingStore[IO] {
      override def put(property: Property, listingSnapshot: ListingSnapshot): IO[Unit] =
        propertyRecords.update(_ + (property.listingId                                       -> property)) *>
          listingRecords.update(_ + ((listingSnapshot.listingId, listingSnapshot.lastChange) -> listingSnapshot))

      override def get(listingId: ListingId): IO[Option[Property]] = propertyRecords.get.map(_.get(listingId))

      override def delete(listingSnapshot: ListingSnapshot): IO[Unit] =
        propertyRecords.update(_ - listingSnapshot.listingId) *>
          listingRecords.update(_ + ((listingSnapshot.listingId, listingSnapshot.lastChange) -> listingSnapshot))
    }
    propertyListingStore.pure[IO]

  }

}
