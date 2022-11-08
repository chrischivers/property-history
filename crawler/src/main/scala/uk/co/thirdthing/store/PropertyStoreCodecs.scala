package uk.co.thirdthing.store

import meteor.codec.{Decoder, Encoder}
import meteor.syntax._
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, PropertyId}
import uk.co.thirdthing.model.Model.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Model.Property
import uk.co.thirdthing.utils.Hasher.Hash

import java.time.Instant

object PropertyStoreCodecs {

  implicit val propertyDecoder: Decoder[Property] = Decoder.instance { av =>
    for {
      listingId         <- av.getAs[Long]("listingId").map(ListingId(_))
      propertyId        <- av.getAs[Long]("propertyId").map(PropertyId(_))
      dateAdded         <- av.getAs[Instant]("dateAdded").map(DateAdded(_))
      listingSnapshotId <- av.getAs[String]("listingSnapshotId").map(ListingSnapshotId(_))
      detailsChecksum   <- av.getAs[String]("detailsChecksum").map(Hash(_))
    } yield Property(listingId, propertyId, dateAdded, listingSnapshotId, detailsChecksum)
  }

  implicit val propertyEncoder: Encoder[Property] = Encoder.instance { property =>
    Map(
      "listingId"         -> property.listingId.value.asAttributeValue,
      "propertyId"        -> property.propertyId.value.asAttributeValue,
      "dateAdded"         -> property.dateAdded.value.asAttributeValue,
      "listingSnapshotId" -> property.listingSnapshotId.value.asAttributeValue,
      "detailsChecksum"   -> property.detailsChecksum.value.asAttributeValue
    ).asAttributeValue
  }

}
