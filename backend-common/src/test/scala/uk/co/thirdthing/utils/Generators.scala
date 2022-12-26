package uk.co.thirdthing.utils

import org.scalacheck.{Arbitrary, Gen}
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._

object Generators {

  val listingSnapshotIdGen: Gen[ListingSnapshotId] = Gen.long.map(ListingSnapshotId(_))
  val listingIdGen: Gen[ListingId]                 = Gen.long.map(ListingId(_))
  val propertyIdGen: Gen[PropertyId]               = Gen.long.map(PropertyId(_))
  val lastChangeGen: Gen[LastChange]               = Gen.calendar.map(_.toInstant).map(LastChange(_))
  val dateAddedGen: Gen[DateAdded]                 = Gen.calendar.map(_.toInstant).map(DateAdded(_))
  val priceGen: Gen[Price]                         = Gen.posNum[Int].map(Price(_))
  val transactionTypeGen: Gen[TransactionType]     = Gen.oneOf(TransactionType.values)
  val listingStatusGen: Gen[ListingStatus]         = Gen.oneOf(ListingStatus.values)

  val propertyDetailsGen: Gen[PropertyDetails] = for {
    price           <- Gen.option(priceGen)
    transactionType <- Gen.option(transactionTypeGen)
    visible         <- Gen.option(Gen.oneOf(true, false))
    status          <- Gen.option(listingStatusGen)
    rentFrequency   <- Gen.option(Gen.alphaStr)
    latitude        <- Gen.option(Gen.double)
    longitude       <- Gen.option(Gen.double)
  } yield PropertyDetails(price, transactionType, visible, status, rentFrequency, latitude, longitude)

  val listingSnapshotGen: Gen[ListingSnapshot] = for {
    snapshotId <- Gen.option(listingSnapshotIdGen)
    listingId  <- listingIdGen
    lastChange <- lastChangeGen
    propertyId <- propertyIdGen
    dateAdded  <- dateAddedGen
    details    <- propertyDetailsGen

  } yield ListingSnapshot(
    listingId,
    lastChange,
    propertyId,
    dateAdded,
    details,
    snapshotId
  )

  implicit val propertyIdArb: Arbitrary[PropertyId]           = Arbitrary(propertyIdGen)
  implicit val listingSnapshotArb: Arbitrary[ListingSnapshot] = Arbitrary(listingSnapshotGen)

}
