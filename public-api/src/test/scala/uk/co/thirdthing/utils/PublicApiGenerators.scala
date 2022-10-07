package uk.co.thirdthing.utils

import org.scalacheck.{Arbitrary, Gen}
import uk.co.thirdthing.model.Model.RightmoveListing

object PublicApiGenerators {

  val rightmoveListingGen: Gen[RightmoveListing] = for {
    id <- Generators.rightmoveListingIdGen
    url <- Gen.alphaNumStr
    dateAdded <- Generators.rightmoveDateAddeddGen
  } yield RightmoveListing(id, url, dateAdded)


  implicit val rightmoveListingArb: Arbitrary[RightmoveListing] = Arbitrary(rightmoveListingGen)


}
