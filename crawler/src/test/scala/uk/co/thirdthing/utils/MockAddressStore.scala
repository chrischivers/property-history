package uk.co.thirdthing.utils

import cats.data.NonEmptyList
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.store.{AddressStore, PropertyStore}

object MockAddressStore:

  def apply(
    addressRecords: Ref[IO, List[AddressDetails]]
  ): IO[AddressStore[IO]] =

    val addressStore = new AddressStore[IO]:
      override def putAddresses(addressDetails: NonEmptyList[AddressDetails]): IO[Unit] =
        addressRecords.update(_ ++ addressDetails.toList)

      override def getAddressFor(propertyId: PropertyId): IO[Option[AddressDetails]] =
        addressRecords.get.map(_.find(_.propertyId.contains(propertyId)))

    addressStore.pure[IO]