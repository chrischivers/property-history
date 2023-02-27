package uk.co.thirdthing.service

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.{Clock, IO}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import munit.ScalaCheckSuite
import org.scalacheck.Prop.{forAll, proved}
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.PropertyScrapingService.ScrapeResult
import uk.co.thirdthing.store.{AddressStore, PropertyStore}
import uk.co.thirdthing.utils.Generators.*
import uk.co.thirdthing.utils.Generators.given

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.*

class PropertyLookupServiceTest extends ScalaCheckSuite:

  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private given staticClock: Clock[IO] = new Clock[IO]:
    override def applicative: Applicative[IO] = implicitly

    override def monotonic: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]

    override def realTime: IO[FiniteDuration] = now.toEpochMilli.millis.pure[IO]

  private def propertyStoreMock(
    rightmovePropertyId: Option[PropertyId],
    listings: List[ListingSnapshot]
  ): PropertyStore[IO] = new PropertyStore[IO]:
    override def propertyIdFor(listingId: ListingId): IO[Option[PropertyId]] = IO(rightmovePropertyId)

    override def latestListingsFor(propertyId: PropertyId): fs2.Stream[IO, ListingSnapshot] =
      fs2.Stream.emits[IO, ListingSnapshot](listings)

    override def putListingSnapshot(listingSnapshot: ListingSnapshot): IO[Unit] = fail("should not be called")

    override def getMostRecentListing(listingId: ListingId): IO[Option[ListingSnapshot]] = fail("should not be called")

  private def addressStoreMock(
    addressDetails: Option[AddressDetails]
  ): AddressStore[IO] = new AddressStore[IO]:
    override def getAddressesFor(propertyId: PropertyId): fs2.Stream[IO, AddressDetails] =
      fs2.Stream.emits[IO, AddressDetails](addressDetails.filter(_.propertyId.contains(propertyId)).toList)

    override def putAddresses(addressDetails: NonEmptyList[AddressDetails]): IO[Unit] = fail("should not be called")

  private def scrapingServiceMock(scrapeResult: Option[ScrapeResult]): PropertyScrapingService[IO] =
    new PropertyScrapingService[IO]:
      override def scrape(listingId: ListingId): IO[Option[ScrapeResult]] = scrapeResult.pure[IO]

  private def listingSnapshotFrom(result: ScrapeResult): ListingSnapshot =
    ListingSnapshot(result.listingId, LastChange(now), result.propertyId, DateAdded(now), result.propertyDetails)

  property("History is successfully retrieved") {
    forAll {
      (
        propertyId: Option[PropertyId],
        listings: List[ListingSnapshot],
        addressDetails: Option[AddressDetails],
        scrapeResult: Option[ScrapeResult]
      ) =>
        val addressDetailsWithPropertyId = addressDetails.map(_.copy(propertyId = propertyId.orElse(scrapeResult.map(_.propertyId))))
        val propertyStore                = propertyStoreMock(propertyId, listings)
        val addressStore                 = addressStoreMock(addressDetailsWithPropertyId)
        val scrapingService              = scrapingServiceMock(scrapeResult)
        val lookupService                = PropertyLookupService.apply[IO](propertyStore, addressStore, scrapingService)
        val results                      = lookupService.detailsFor(ListingId(123)).unsafeRunSync()

        (propertyId, scrapeResult) match
          case (None, None, _) => assertEquals(results, None)
          case (Some(_), _) =>
            assertEquals(
              results,
              Some(
                PropertyLookupDetails(
                  addressDetailsWithPropertyId.map(_.address),
                  addressDetailsWithPropertyId.map(_.postcode),
                  listings,
                  addressDetails.fold(List.empty)(_.transactions)
                )
              )
            )
          case (None, Some(result)) =>
            assertEquals(
              results,
              Some(
                PropertyLookupDetails(
                  addressDetailsWithPropertyId.map(_.address),
                  addressDetailsWithPropertyId.map(_.postcode),
                  listingSnapshotFrom(result) +: listings,
                  addressDetails.fold(List.empty)(_.transactions)
                )
              )
            )
    }
  }
