package uk.co.thirdthing.model

import enumeratum.values.*
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder}
import monix.newtypes.NewtypeWrapped
import monix.newtypes.integrations.DerivedCirceCodec
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId

import java.time.{Instant, LocalDate}

object Types:

  type ListingId = ListingId.Type
  object ListingId extends NewtypeWrapped[Long] with DerivedCirceCodec

  type PropertyId = PropertyId.Type
  object PropertyId extends NewtypeWrapped[Long] with DerivedCirceCodec
  
  type DateAdded = DateAdded.Type
  object DateAdded extends NewtypeWrapped[Instant] with DerivedCirceCodec

  type Price = Price.Type
  object Price extends NewtypeWrapped[Int] with DerivedCirceCodec

  type ThumbnailUrl = ThumbnailUrl.Type
  object ThumbnailUrl extends NewtypeWrapped[String] with DerivedCirceCodec

  type Postcode = Postcode.Type
  object Postcode extends NewtypeWrapped[String] with DerivedCirceCodec

  type FullAddress = FullAddress.Type
  object FullAddress extends NewtypeWrapped[String] with DerivedCirceCodec

  final case class ListingSnapshot(
    listingId: ListingId,
    lastChange: LastChange,
    propertyId: PropertyId,
    dateAdded: DateAdded,
    details: PropertyDetails,
    listingSnapshotId: Option[ListingSnapshotId] = None
  )

  object ListingSnapshot:
    type ListingSnapshotId = ListingSnapshotId.Type
    object ListingSnapshotId extends NewtypeWrapped[Long] with DerivedCirceCodec

    implicit val codec: Codec[ListingSnapshot] = deriveCodec

  type LastChange = LastChange.Type
  object LastChange extends NewtypeWrapped[Instant] with DerivedCirceCodec

  sealed abstract class TransactionType(override val value: Int, val string: String) extends IntEnumEntry

  object TransactionType extends IntEnum[TransactionType] with IntCirceEnum[TransactionType]:
    case object Sale extends TransactionType(1, "Sale")

    case object Rental extends TransactionType(2, "Rental")

    override def values: IndexedSeq[TransactionType] = findValues

  sealed abstract class ListingStatus(override val value: String) extends StringEnumEntry

  object ListingStatus extends StringEnum[ListingStatus] with StringCirceEnum[ListingStatus]:
    case object SoldSTC extends ListingStatus("Sold STC")

    case object SoldSTCM extends ListingStatus("Sold STCM")

    case object LetAgreed extends ListingStatus("Under Offer")

    case object UnderOffer extends ListingStatus("Let Agreed")

    case object Reserved extends ListingStatus("Reserved")

    case object Deleted extends ListingStatus("Deleted")

    case object Hidden extends ListingStatus("Hidden")

    case object Other extends ListingStatus("Other")

    case object Unknown extends ListingStatus("Unknown")

    override def values: IndexedSeq[ListingStatus] = findValues

    override implicit val circeDecoder: Decoder[ListingStatus] = Decoder.instance { cursor =>
      cursor.as[String].map(c => valuesToEntriesMap.getOrElse(c, Other))
    }

  final case class PropertyDetails(
    price: Option[Price],
    transactionTypeId: Option[TransactionType],
    visible: Option[Boolean],
    status: Option[ListingStatus],
    rentFrequency: Option[String],
    latitude: Option[Double],
    longitude: Option[Double],
    thumbnailUrl: Option[ThumbnailUrl]
  )

  object PropertyDetails:
    implicit val codec: Codec[PropertyDetails] = deriveCodec
    val Deleted = PropertyDetails(None, None, None, Some(ListingStatus.Deleted), None, None, None, None)
    val Empty   = PropertyDetails(None, None, None, None, None, None, None, None)
    def from(
      price: Price,
      transactionTypeId: TransactionType,
      visible: Boolean,
      status: ListingStatus,
      rentFrequency: String,
      latitude: Double,
      longitude: Double,
      thumbnailUrl: ThumbnailUrl
    ): PropertyDetails =
      PropertyDetails(
        Some(price),
        Some(transactionTypeId),
        Some(visible),
        Some(status),
        Some(rentFrequency),
        Some(latitude),
        Some(longitude),
        Some(thumbnailUrl)
      )

  sealed abstract class Tenure(override val value: String) extends StringEnumEntry
  object Tenure extends StringEnum[Tenure] with StringCirceEnum[Tenure]:
    case object Leasehold extends Tenure("Leasehold")

    case object Freehold extends Tenure("Freehold")

    override def values: IndexedSeq[Tenure] = findValues

  final case class Transaction(
    price: Price,
    date: LocalDate,
    tenure: Option[Tenure]
  )
  
  object Transaction {
    implicit val codec: Codec[Transaction] = deriveCodec
  }

  final case class AddressDetails(
    address: FullAddress,
    postcode: Postcode,
    propertyId: Option[PropertyId],
    transactions: List[Transaction]
  )
