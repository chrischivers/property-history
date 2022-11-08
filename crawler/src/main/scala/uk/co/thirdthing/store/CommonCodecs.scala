package uk.co.thirdthing.store

import cats.implicits.{catsSyntaxOptionId, toBifunctorOps}
import meteor.codec.{Decoder, Encoder}
import meteor.errors.DecoderError
import meteor.syntax._
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.model.Model.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Model.{ListingStatus, Property, PropertyDetails, TransactionType}

import java.time.Instant

object CommonCodecs {

  implicit val propertyIdDecoder: Decoder[PropertyId] = Decoder.instance(_.getAs[Long]("propertyId").map(PropertyId.apply))
  implicit val listingIdEncoder: Encoder[ListingId] = Encoder.instance(_.value.asAttributeValue)




}
