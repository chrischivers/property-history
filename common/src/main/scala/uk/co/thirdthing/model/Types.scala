package uk.co.thirdthing.model

import enumeratum.values._
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder}
import monix.newtypes.NewtypeWrapped
import monix.newtypes.integrations.DerivedCirceCodec
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId

import java.time.Instant

object Types {

  type ListingId = ListingId.Type
  object ListingId extends NewtypeWrapped[Long] with DerivedCirceCodec

  type PropertyId = PropertyId.Type
  object PropertyId extends NewtypeWrapped[Long] with DerivedCirceCodec

  type DateAdded = DateAdded.Type
  object DateAdded extends NewtypeWrapped[Instant] with DerivedCirceCodec

  type Price = Price.Type
  object Price extends NewtypeWrapped[Int] with DerivedCirceCodec

  final case class ListingSnapshot(
    listingId: ListingId,
    lastChange: LastChange,
    propertyId: PropertyId,
    dateAdded: DateAdded,
    details: PropertyDetails,
    listingSnapshotId: Option[ListingSnapshotId] = None
  )

  object ListingSnapshot {
    type ListingSnapshotId = ListingSnapshotId.Type
    object ListingSnapshotId extends NewtypeWrapped[Long] with DerivedCirceCodec

    implicit val codec: Codec[ListingSnapshot] = deriveCodec
  }

  type LastChange = LastChange.Type
  object LastChange extends NewtypeWrapped[Instant] with DerivedCirceCodec

  sealed abstract class TransactionType(val value: Int) extends IntEnumEntry

  object TransactionType extends IntEnum[TransactionType] with IntCirceEnum[TransactionType] {
    final case object Sale extends TransactionType(1)

    final case object Rental extends TransactionType(2)

    override def values: IndexedSeq[TransactionType] = findValues
  }

  sealed abstract class ListingStatus(override val value: String) extends StringEnumEntry

  object ListingStatus extends StringEnum[ListingStatus] with StringCirceEnum[ListingStatus] {
    final case object SoldSTC extends ListingStatus("Sold STC")

    final case object SoldSTCM extends ListingStatus("Sold STCM")

    final case object LetAgreed extends ListingStatus("Under Offer")

    final case object UnderOffer extends ListingStatus("Let Agreed")

    final case object Reserved extends ListingStatus("Reserved")

    final case object Deleted extends ListingStatus("Deleted")

    final case object Hidden extends ListingStatus("Hidden")

    final case object Other extends ListingStatus("Other")

    final case object Unknown extends ListingStatus("Unknown")

    override def values: IndexedSeq[ListingStatus] = findValues

    override implicit val circeDecoder: Decoder[ListingStatus] = Decoder.instance { cursor =>
      cursor.as[String].map(c => valuesToEntriesMap.getOrElse(c, Other))
    }
  }

  final case class PropertyDetails(
    price: Option[Price],
    transactionTypeId: Option[TransactionType],
    visible: Option[Boolean],
    status: Option[ListingStatus],
    rentFrequency: Option[String],
    latitude: Option[Double],
    longitude: Option[Double]
  )

  object PropertyDetails {
    implicit val codec: Codec[PropertyDetails] = deriveCodec
    val Deleted                                = PropertyDetails(None, None, None, Some(ListingStatus.Deleted), None, None, None)
    def from(
      price: Price,
      transactionTypeId: TransactionType,
      visible: Boolean,
      status: ListingStatus,
      rentFrequency: String,
      latitude: Double,
      longitude: Double
    ): PropertyDetails =
      PropertyDetails(Some(price), Some(transactionTypeId), Some(visible), Some(status), Some(rentFrequency), Some(latitude), Some(longitude))
  }

}
