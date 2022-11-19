package uk.co.thirdthing.utils

import org.scalacheck.{Arbitrary, Gen}
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._

object Generators {

  val listingIdGen: Gen[ListingId] = Gen.long.map(ListingId(_))
  val propertyIdGen: Gen[PropertyId] = Gen.long.map(PropertyId(_))
  val dateAddeddGen: Gen[DateAdded] = Gen.calendar.map(_.toInstant).map(DateAdded(_))
  val listingSnapshotIdGen: Gen[ListingSnapshotId] = Gen.const(ListingSnapshotId.generate)

  implicit val rightmovePropertyIdArb: Arbitrary[PropertyId] = Arbitrary(propertyIdGen)

}
