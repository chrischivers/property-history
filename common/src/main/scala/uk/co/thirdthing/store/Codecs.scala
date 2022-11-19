package uk.co.thirdthing.store

import cats.implicits.{catsSyntaxOptionId, toBifunctorOps}
import meteor.codec.{Decoder, Encoder}
import meteor.errors.DecoderError
import meteor.syntax._
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.utils.Hasher.Hash

import java.time.Instant

object Codecs {

  implicit val propertyIdEncoder: Encoder[PropertyId] = Encoder.instance(_.value.asAttributeValue)
  implicit val propertyIdDecoder: Decoder[PropertyId] = Decoder.instance(_.getAs[Long]("propertyId").map(PropertyId.apply))
  implicit val listingIdEncoder: Encoder[ListingId]   = Encoder.instance(_.value.asAttributeValue)

  implicit val propertyDecoder: Decoder[PropertyListing] = Decoder.instance { av =>
    for {
      listingId         <- av.getAs[Long]("listingId").map(ListingId(_))
      propertyId        <- av.getAs[Long]("propertyId").map(PropertyId(_))
      dateAdded         <- av.getAs[Instant]("dateAdded").map(DateAdded(_))
      listingSnapshotId <- av.getAs[String]("listingSnapshotId").map(ListingSnapshotId(_))
      detailsChecksum   <- av.getAs[String]("detailsChecksum").map(Hash(_))
    } yield PropertyListing(listingId, propertyId, dateAdded, listingSnapshotId, detailsChecksum)
  }

  implicit val propertyEncoder: Encoder[PropertyListing] = Encoder.instance { property =>
    Map(
      "listingId"         -> property.listingId.value.asAttributeValue,
      "propertyId"        -> property.propertyId.value.asAttributeValue,
      "dateAdded"         -> property.dateAdded.value.asAttributeValue,
      "listingSnapshotId" -> property.listingSnapshotId.value.asAttributeValue,
      "detailsChecksum"   -> property.detailsChecksum.value.asAttributeValue
    ).asAttributeValue
  }

  implicit val lastChangeEncoder: Encoder[LastChange] = Encoder.instance(_.value.asAttributeValue)

  implicit val propertyDetailsDecoder: Decoder[PropertyDetails] = Decoder.instance { av =>
    for {
      price <- av.getAs[Int]("price").map(Price(_))
      transactionTypeId <- av
                            .getAs[Int]("transactionTypeId")
                            .flatMap(id =>
                              TransactionType.withValueEither(id).leftMap(err => DecoderError(s"cannot map $id to transaction type", err.some))
                            )
      visible <- av.getAs[Boolean]("visible")
      status <- av
                 .getAs[String]("status")
                 .flatMap(status =>
                   ListingStatus
                     .withValueEither(status)
                     .leftMap(err => DecoderError(s"cannot map $status to listing status type"))
                 )
      rentFrequency <- av.getOpt[String]("rentFrequency")
      latitude      <- av.getOpt[Double]("latitude")
      longitude     <- av.getOpt[Double]("longitude")
    } yield PropertyDetails(price, transactionTypeId, visible, status, rentFrequency, latitude, longitude)
  }

  implicit val listingSnapshotDecoder: Decoder[ListingSnapshot] = Decoder.instance { av =>
    for {
      listingId         <- av.getAs[Long]("listingId").map(ListingId(_))
      lastChange        <- av.getAs[Instant]("lastChange").map(LastChange(_))
      propertyId        <- av.getAs[Long]("propertyId").map(PropertyId(_))
      dateAdded         <- av.getAs[Instant]("dateAdded").map(DateAdded(_))
      listingSnapshotId <- av.getAs[String]("listingSnapshotId").map(ListingSnapshotId(_))
      details           <- av.getOpt[PropertyDetails]("details")
    } yield ListingSnapshot(listingId, lastChange, propertyId, dateAdded, listingSnapshotId, details)
  }

  implicit val propertyDetailsEncoder: Encoder[PropertyDetails] = Encoder.instance { details =>
    Map(
      "price"             -> details.price.value.asAttributeValue,
      "transactionTypeId" -> details.transactionTypeId.value.asAttributeValue,
      "visible"           -> details.visible.asAttributeValue,
      "status"            -> details.status.value.asAttributeValue,
      "rentFrequency"     -> details.rentFrequency.asAttributeValue,
      "latitude"          -> details.latitude.asAttributeValue,
      "longitude"         -> details.longitude.asAttributeValue
    ).asAttributeValue
  }

  implicit val listingSnapshotEncoder: Encoder[ListingSnapshot] = Encoder.instance { listingSnapshot =>
    Map(
      "listingId"         -> listingSnapshot.listingId.value.asAttributeValue,
      "lastChange"        -> listingSnapshot.lastChange.value.asAttributeValue,
      "propertyId"        -> listingSnapshot.propertyId.value.asAttributeValue,
      "dateAdded"         -> listingSnapshot.dateAdded.value.asAttributeValue,
      "listingSnapshotId" -> listingSnapshot.listingSnapshotId.value.asAttributeValue,
      "details"           -> listingSnapshot.details.asAttributeValue
    ).asAttributeValue
  }

}
