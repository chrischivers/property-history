package uk.co.thirdthing.store

import cats.syntax.all.*
import skunk.exception.PostgresErrorException
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.store.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDateTime
import uk.co.thirdthing.model.Model

class PostgresPostcodeStoreTest extends munit.CatsEffectSuite with PostgresPostcodeStoreIntegration:

  val now = LocalDateTime.now()

  test("get and lock the next postcode") {

    val initialState = List(PostcodeRecord(Postcode("AR1 1PG"), inUse = true, lockedAt = None, lastScraped = None))

    withPostgresPostcodeStore(initialState) { store =>
      val result = store.getAndLockNextPostcode

      assertIO(result, Some(initialState.head.postcode))
    }
  }

  test("do not get and lock the next postcode if not in use") {

    val initialState = List(PostcodeRecord(Postcode("AR1 1PG"), inUse = false, lockedAt = None, lastScraped = None))

    withPostgresPostcodeStore(initialState) { store =>
      val result = store.getAndLockNextPostcode

      assertIO(result, None)
    }
  }

  test("do not get and lock the next postcode if it has been recently locked") {

    val initialState = List(PostcodeRecord(Postcode("AR1 1PG"), inUse = true, lockedAt = Some(now), lastScraped = None))

    withPostgresPostcodeStore(initialState) { store =>
      val result = store.getAndLockNextPostcode

      assertIO(result, None)
    }
  }


  test("get and lock the next postcode if it has NOT been recently locked") {

    val initialState = List(PostcodeRecord(Postcode("AR1 1PG"), inUse = true, lockedAt = Some(now.minusDays(1)), lastScraped = None))

    withPostgresPostcodeStore(initialState) { store =>
      val result = store.getAndLockNextPostcode

      assertIO(result, Some(initialState.head.postcode))
    }
  }


  test("return the next postcode sorted by scraped time") {

    val initialState = List(
      PostcodeRecord(Postcode("AR1 1PG"), inUse = true, lockedAt = None, lastScraped = Some(now.minusDays(3))),
      PostcodeRecord(Postcode("AR1 2PG"), inUse = true, lockedAt = None, lastScraped = Some(now.minusDays(1))),
      PostcodeRecord(Postcode("AR1 3PG"), inUse = true, lockedAt = None, lastScraped = Some(now.minusDays(2)))
    )

    withPostgresPostcodeStore(initialState) { store =>
      val result = store.getAndLockNextPostcode

      assertIO(result, Some(initialState.head.postcode))
    }
  }

