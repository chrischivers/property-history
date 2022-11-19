package uk.co.thirdthing.utils

import cats.effect.{IO, Ref}
import cats.syntax.all._
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.store.PropertyListingStore

object MockPropertyListingStore {

  def apply(
    propertyRecords: Ref[IO, Map[ListingId, PropertyListing]],
    listingRecords: Ref[IO, Map[(ListingId, LastChange), ListingSnapshot]]
  ): IO[PropertyListingStore[IO]] = {

    val propertyListingStore = new PropertyListingStore[IO] {
      override def put(property: PropertyListing, listingSnapshot: ListingSnapshot): IO[Unit] =
        propertyRecords.update(_ + (property.listingId                                       -> property)) *>
          listingRecords.update(_ + ((listingSnapshot.listingId, listingSnapshot.lastChange) -> listingSnapshot))

      override def get(listingId: ListingId): IO[Option[PropertyListing]] = propertyRecords.get.map(_.get(listingId))

      override def delete(listingSnapshot: ListingSnapshot): IO[Unit] =
        propertyRecords.update(_ - listingSnapshot.listingId) *>
          listingRecords.update(_ + ((listingSnapshot.listingId, listingSnapshot.lastChange) -> listingSnapshot))
    }
    propertyListingStore.pure[IO]

  }

}
