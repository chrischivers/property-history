package uk.co.thirdthing.utils

import cats.effect.{IO, Ref}
import cats.syntax.all.*
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.store.PropertyStore

object MockPropertyStore:

  def apply(
    listingRecords: Ref[IO, Map[(ListingId, LastChange), ListingSnapshot]]
  ): IO[PropertyStore[IO]] =

    val propertyListingStore = new PropertyStore[IO]:

      private def mostRecentListingFor(listingId: ListingId) =
        listingRecords.get.map(
          _.toList
            .filter { case ((lId, _), _) => lId == listingId }
            .sortBy { case ((_, lc), _) => lc.value }
            .reverse
            .headOption
            .map { case ((_, _), l) => l }
        )

      override def propertyIdFor(listingId: ListingId): IO[Option[PropertyId]] =
        mostRecentListingFor(listingId).map(_.map(_.propertyId))

      override def latestListingsFor(propertyId: PropertyId): fs2.Stream[IO, ListingSnapshot] =
        fs2.Stream.evals {
          listingRecords.get.flatMap(
            _.toList
              .filter { case ((_, _), l) => l.propertyId == propertyId }
              .traverse { case ((lId, _), _) => mostRecentListingFor(lId) }
              .map(_.flatten)
          )
        }

      override def putListingSnapshot(listingSnapshot: ListingSnapshot): IO[Unit] =
        listingRecords.update(_ + ((listingSnapshot.listingId, listingSnapshot.lastChange) -> listingSnapshot))

      override def getMostRecentListing(listingId: ListingId): IO[Option[ListingSnapshot]] = mostRecentListingFor(
        listingId
      )
    propertyListingStore.pure[IO]
