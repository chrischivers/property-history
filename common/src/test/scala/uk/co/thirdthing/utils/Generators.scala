package uk.co.thirdthing.utils

import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, PropertyId}
import org.scalacheck.{Arbitrary, Gen}

object Generators {

  val rightmoveListingIdGen: Gen[ListingId] = Gen.long.map(ListingId(_))
  val rightmovePropertyIdGen: Gen[PropertyId] = Gen.long.map(PropertyId(_))
  val rightmoveDateAddeddGen: Gen[DateAdded] = Gen.calendar.map(_.toInstant).map(DateAdded(_))

  implicit val rightmovePropertyIdArb: Arbitrary[PropertyId] = Arbitrary(rightmovePropertyIdGen)

}
