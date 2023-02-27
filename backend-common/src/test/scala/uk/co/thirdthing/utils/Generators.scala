package uk.co.thirdthing.utils

import org.http4s.Uri
import org.scalacheck.{Arbitrary, Gen}
import uk.co.thirdthing.clients.RightmoveApiClient.ListingDetails
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.PropertyScrapingService.ScrapeResult

import java.time.{Instant, ZoneId}

object Generators:

  val instantGen: Gen[Instant] = Gen.chooseNum(0L, System.currentTimeMillis()).map(Instant.ofEpochMilli)
  val listingSnapshotIdGen: Gen[ListingSnapshotId] = Gen.long.map(ListingSnapshotId(_))
  val listingIdGen: Gen[ListingId]                 = Gen.long.map(ListingId(_))
  val propertyIdGen: Gen[PropertyId]               = Gen.long.map(PropertyId(_))
  val lastChangeGen: Gen[LastChange]               = instantGen.map(LastChange(_))
  val dateAddedGen: Gen[DateAdded]                 = instantGen.map(DateAdded(_))
  val priceGen: Gen[Price]                         = Gen.posNum[Int].map(Price(_))
  val transactionTypeGen: Gen[TransactionType]     = Gen.oneOf(TransactionType.values)
  val listingStatusGen: Gen[ListingStatus]         = Gen.oneOf(ListingStatus.values)
  val thumbnailUrlGen: Gen[ThumbnailUrl]           = Gen.alphaStr.map(ThumbnailUrl(_))
  val uriGen: Gen[Uri]                             = Gen.alphaStr.map(str => Uri.unsafeFromString(s"http://$str.com"))

  val propertyDetailsGen: Gen[PropertyDetails] = for
    price           <- Gen.option(priceGen)
    transactionType <- Gen.option(transactionTypeGen)
    visible         <- Gen.option(Gen.oneOf(true, false))
    status          <- Gen.option(listingStatusGen)
    rentFrequency   <- Gen.option(Gen.alphaStr)
    latitude        <- Gen.option(Gen.double)
    longitude       <- Gen.option(Gen.double)
    thumbnailUrl    <- Gen.option(thumbnailUrlGen)
  yield PropertyDetails(price, transactionType, visible, status, rentFrequency, latitude, longitude, thumbnailUrl)

  val listingSnapshotGen: Gen[ListingSnapshot] = for
    snapshotId <- Gen.option(listingSnapshotIdGen)
    listingId  <- listingIdGen
    lastChange <- lastChangeGen
    propertyId <- propertyIdGen
    dateAdded  <- dateAddedGen
    details    <- propertyDetailsGen
  yield ListingSnapshot(
    listingId,
    lastChange,
    propertyId,
    dateAdded,
    details,
    snapshotId
  )

  val listingDetailsGen: Gen[ListingDetails] = for
    price           <- priceGen
    transactionType <- transactionTypeGen
    visible         <- Gen.oneOf(true, false)
    status          <- Gen.option(listingStatusGen)
    rentFrequency   <- Gen.option(Gen.alphaStr)
    latitude        <- Gen.option(Gen.double)
    longitude       <- Gen.option(Gen.double)
    publicSiteUrl   <- uriGen
    thumbnailUrl    <- Gen.option(thumbnailUrlGen)
    sortDate        <- Gen.option(instantGen.map(_.toEpochMilli))
    updateDate      <- instantGen.map(_.toEpochMilli)
  yield ListingDetails(
    price,
    transactionType,
    visible,
    status,
    sortDate,
    updateDate,
    rentFrequency,
    publicSiteUrl,
    thumbnailUrl,
    latitude,
    longitude
  )

  val transactionGen: Gen[Transaction] = for
    price  <- priceGen
    date   <- instantGen.map(_.atZone(ZoneId.of("UTC")).toLocalDate)
    tenure <- Gen.option(Gen.oneOf(Tenure.values))
  yield Transaction(price, date, tenure)

  val scrapeResultGen: Gen[ScrapeResult] = for
    listingId  <- listingIdGen
    propertyId <- propertyIdGen
    dateAdded  <- instantGen
    details    <- propertyDetailsGen
  yield ScrapeResult(listingId, propertyId, DateAdded(dateAdded), details)

  val addressDetailsGen: Gen[AddressDetails] = for
    address      <- Gen.alphaNumStr.map(str => FullAddress(str.take(100)))
    postcode     <- Gen.alphaNumStr.map(str => Postcode(str.take(9)))
    propertyId   <- Gen.option(propertyIdGen)
    transactions <- Gen.listOf(transactionGen)
  yield AddressDetails(address, postcode, propertyId, transactions)

  given Arbitrary[PropertyId]      = Arbitrary(propertyIdGen)
  given Arbitrary[ListingSnapshot] = Arbitrary(listingSnapshotGen)
  given Arbitrary[ScrapeResult]    = Arbitrary(scrapeResultGen)
  given Arbitrary[AddressDetails]  = Arbitrary(addressDetailsGen)
