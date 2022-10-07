package uk.co.thirdthing

import monix.newtypes.NewtypeWrapped
import monix.newtypes.integrations.DerivedCirceCodec

import java.time.Instant

object Rightmove {

  type ListingId = ListingId.Type
  object ListingId extends NewtypeWrapped[Long] with DerivedCirceCodec

  type PropertyId = PropertyId.Type
  object PropertyId extends NewtypeWrapped[Long] with DerivedCirceCodec

  type DateAdded = DateAdded.Type
  object DateAdded extends NewtypeWrapped[Instant] with DerivedCirceCodec

  type Price = Price.Type
  object Price extends NewtypeWrapped[Int] with DerivedCirceCodec

}
