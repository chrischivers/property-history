package uk.co.thirdthing.utils

import cats.effect.{IO, Ref}
import fs2.Pipe
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model
import uk.co.thirdthing.model.Model.Property
import uk.co.thirdthing.store.PropertyStore

object MockPropertyStore {

  def apply(propertyRecords: Ref[IO, Map[ListingId, Property]]): IO[PropertyStore[IO]] = {

    val propertyStore = new PropertyStore[IO] {
      override def put(property: Model.Property): IO[Unit] = propertyRecords.update(_ + (property.listingId -> property))

      override def get(listingId: ListingId): IO[Option[Model.Property]] = propertyRecords.get.map(_.get(listingId))

      override def delete(listingId: ListingId): IO[Unit] = propertyRecords.update(_ - listingId)

      override def putStream: Pipe[IO, Model.Property, Unit] = _.evalMap(put)
    }

    propertyRecords.get
      .flatMap(records => fs2.Stream.emits(records.view.values.toList).through(propertyStore.putStream).compile.drain)
      .as(propertyStore)
  }

}
