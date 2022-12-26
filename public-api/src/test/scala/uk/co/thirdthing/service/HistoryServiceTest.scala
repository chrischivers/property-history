package uk.co.thirdthing.service

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.store.PropertyStore
import uk.co.thirdthing.utils.Generators._

class HistoryServiceTest extends ScalaCheckSuite:

  def propertyStoreMock(rightmovePropertyId: Option[PropertyId], listings: List[ListingSnapshot]): PropertyStore[IO] = new PropertyStore[IO] {
    override def propertyIdFor(listingId: ListingId): IO[Option[PropertyId]] = IO(rightmovePropertyId)

    override def latestListingsFor(propertyId: PropertyId): fs2.Stream[IO, ListingSnapshot] = fs2.Stream.emits[IO, ListingSnapshot](listings)

    override def putListingSnapshot(listingSnapshot: ListingSnapshot): IO[Unit] = fail("should not be called")

    override def getMostRecentListing(listingId: ListingId): IO[Option[ListingSnapshot]] = fail("should not be called")
  }

  property("History is successfully retrieved") {
    forAll { (propertyId: Option[PropertyId], listings: List[ListingSnapshot]) =>
      val propertyStore  = propertyStoreMock(propertyId, listings)
      val historyService = HistoryService[IO](propertyStore)
      val results        = historyService.historyFor(ListingId(123)).compile.toList.unsafeRunSync()

      if (propertyId.isEmpty) assertEquals(results, List.empty)
      else assertEquals(results, listings)
    }
  }
