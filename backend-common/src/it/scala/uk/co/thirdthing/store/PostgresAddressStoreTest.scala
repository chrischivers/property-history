package uk.co.thirdthing.store

import cats.syntax.all.*
import cats.data.NonEmptyList
import skunk.exception.PostgresErrorException
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types.*

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class PostgresAddressStoreTest extends munit.CatsEffectSuite with PostgresAddressStoreIntegration:

  val addressDetails1 = AddressDetails(
    FullAddress("32 Windsor Gardens, London, SW2 8PF"),
    Postcode("SW2 8PD"),
    Some(PropertyId(134222)),
    List(Transaction(Price(100000), LocalDate.of(2929, 1, 1), Some(Tenure.Freehold)))
  )

  val addressDetails2 = AddressDetails(
    FullAddress("64 Partridge Court"),
    Postcode("WC1 9PL"),
    Some(PropertyId(847382)),
    List(Transaction(Price(100000), LocalDate.of(2929, 1, 1), Some(Tenure.Freehold)))
  )

  test("Stores an address and retrieves by propertyId") {
    withPostgresAddressStore { store =>
      val result = store.putAddresses(NonEmptyList.one(addressDetails1)) *>
        store.getAddressFor(addressDetails1.propertyId.get)
      assertIO(result, Some(addressDetails1))
    }
  }

  test("Stores multiple addresses and retrieves by propertyId") {
    withPostgresAddressStore { store =>
      store.putAddresses(NonEmptyList.of(addressDetails1, addressDetails2)) *>
        assertIO(store.getAddressFor(addressDetails1.propertyId.get), Some(addressDetails1)) *>
        assertIO(store.getAddressFor(addressDetails2.propertyId.get), Some(addressDetails2))

    }
  }

  test("Returns empty if propertyId does not exist") {
    withPostgresAddressStore { store =>
      val result = store.getAddressFor(addressDetails1.propertyId.get)
      assertIO(result, None)
    }
  }

  test("Does not allow the same address to be stored twice") {
    withPostgresAddressStore { store =>
      val result = store.putAddresses(NonEmptyList.one(addressDetails1)) *>
        store.putAddresses(NonEmptyList.one(addressDetails2.copy(address = addressDetails1.address)))
      interceptIO[PostgresErrorException](result).void
    }
  }

  test("Does not allow the same propertyId to be stored twice") {
    withPostgresAddressStore { store =>
      val result = store.putAddresses(NonEmptyList.one(addressDetails1)) *>
        store.putAddresses(NonEmptyList.one(addressDetails2.copy(propertyId = addressDetails1.propertyId)))
      interceptIO[PostgresErrorException](result).void
    }
  }
