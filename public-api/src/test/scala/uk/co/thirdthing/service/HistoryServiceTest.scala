package uk.co.thirdthing.service

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import uk.co.thirdthing.Rightmove.{ListingId, PropertyId}
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll
import uk.co.thirdthing.model.Model.RightmoveListing
import uk.co.thirdthing.store.{PropertyIdStore, PropertyListingStore}
import uk.co.thirdthing.utils.PublicApiGenerators._
import uk.co.thirdthing.utils.Generators._

class HistoryServiceTest extends ScalaCheckSuite {

  def propertyIdStoreMock(rightmovePropertyId: Option[PropertyId]): PropertyIdStore[IO] = new PropertyIdStore[IO] {
    override def idFor(listingId: ListingId): IO[Option[PropertyId]] = IO(rightmovePropertyId)
  }

  def propertyListingStoreMock(listings: List[RightmoveListing]): PropertyListingStore[IO] = new PropertyListingStore[IO] {
    override def listingsFor(propertyId: PropertyId): fs2.Stream[IO, RightmoveListing] = fs2.Stream.emits[IO, RightmoveListing](listings)
  }

  property("History is successfully retrieved") {
    forAll { (rightmovePropertyId: Option[PropertyId], rightmoveListings: List[RightmoveListing]) =>
      val propertyIdStore      = propertyIdStoreMock(rightmovePropertyId)
      val propertyListingStore = propertyListingStoreMock(rightmoveListings)
      val historyService       = HistoryService[IO](propertyIdStore, propertyListingStore)
      val results              = historyService.historyFor(ListingId(123)).compile.toList.unsafeRunSync()

      if (rightmovePropertyId.isEmpty) assertEquals(results, List.empty)
      else assertEquals(results, rightmoveListings)
    }
  }

}
