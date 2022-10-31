package uk.co.thirdthing.store

import cats.implicits.{catsSyntaxOptionId, toBifunctorOps}
import meteor.codec.{Decoder, Encoder}
import meteor.errors.DecoderError
import meteor.syntax._
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.model.Model.{JobId, ListingStatus, Property, PropertyDetails, TransactionType}
import meteor.codec._
import meteor.errors.DecoderError
import meteor.syntax._

import java.time.Instant

object PropertyStoreCodecs {

  implicit val propertyIdDecoder: Decoder[PropertyId] = Decoder.instance(_.getAs[Long]("propertyId").map(PropertyId.apply))
  implicit val listingIdEncoder: Encoder[ListingId] = Encoder.instance(_.value.asAttributeValue)

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
      latitude      <- av.getAs[Double]("latitude")
      longitude     <- av.getAs[Double]("longitude")
    } yield PropertyDetails(price, transactionTypeId, visible, status, rentFrequency, latitude, longitude)
  }

  implicit val propertyDecoder: Decoder[Property] = Decoder.instance { av =>
    for {
      listingId  <- av.getAs[Long]("listingId").map(ListingId(_))
      propertyId <- av.getAs[Long]("propertyId").map(PropertyId(_))
      dateAdded  <- av.getAs[Instant]("dateAdded").map(DateAdded(_))
      details    <- av.getAs[PropertyDetails]("details")
    } yield Property(listingId, propertyId, dateAdded, details)
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
  implicit val propertyEncoder: Encoder[Property] = Encoder.instance { property =>
    Map(
      "listingId"  -> property.listingId.value.asAttributeValue,
      "propertyId" -> property.propertyId.value.asAttributeValue,
      "dateAdded"  -> property.dateAdded.value.asAttributeValue,
      "details"    -> property.details.asAttributeValue
    ).asAttributeValue
  }

}
