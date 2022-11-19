package uk.co.thirdthing.utils

import org.scalacheck.{Arbitrary, Gen}
import uk.co.thirdthing.model.Types.PropertyListing
import uk.co.thirdthing.utils.Hasher.Hash

object PublicApiGenerators {

  val propertyListingGen: Gen[PropertyListing] = for {
    listingId <- Generators.listingIdGen
    propertyId <- Generators.propertyIdGen
    dateAdded <- Generators.dateAddeddGen
    listingSnapshotId <- Generators.listingSnapshotIdGen
  } yield PropertyListing(listingId, propertyId, dateAdded, listingSnapshotId, Hash("blah"))


  implicit val propertyListingArb: Arbitrary[PropertyListing] = Arbitrary(propertyListingGen)


}
