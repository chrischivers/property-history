package uk.co.thirdthing.utils

import cats.effect.{IO, Ref}
import fs2.Pipe
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model
import uk.co.thirdthing.model.Model.CrawlerJob.LastChange
import uk.co.thirdthing.model.Model.{ListingSnapshot, Property}
import uk.co.thirdthing.store.{ListingHistoryStore, PropertyStore}

object MockListingHistoryStore {

  def apply(listingRecords: Ref[IO, Map[(ListingId, LastChange), ListingSnapshot]]): IO[ListingHistoryStore[IO]] = {

    val listingHistoryStore = new ListingHistoryStore[IO] {
      override def put(listingSnapshot: Model.ListingSnapshot): IO[Unit] = listingRecords.update(_ + ((listingSnapshot.listingId, listingSnapshot.lastChange) -> listingSnapshot))

      override def putStream: Pipe[IO, Model.ListingSnapshot, Unit] = _.evalMap(put)
    }

    listingRecords.get
      .flatMap(records => fs2.Stream.emits(records.view.values.toList).through(listingHistoryStore.putStream).compile.drain)
      .as(listingHistoryStore)
  }

}
