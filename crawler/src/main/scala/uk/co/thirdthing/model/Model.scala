package uk.co.thirdthing.model

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import enumeratum.values._
import io.circe.Decoder
import monix.newtypes.NewtypeWrapped
import monix.newtypes.integrations.DerivedCirceCodec
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}

import java.time.Instant

object Model {

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

  final case class Property(listingId: ListingId, propertyId: PropertyId, dateAdded: DateAdded, details: PropertyDetails)

  final case class PropertyDetails(
                              price: Price,
                              transactionTypeId: TransactionType,
                              visible: Boolean,
                              status: ListingStatus,
                              rentFrequency: Option[String],
                              latitude: Double,
                              longitude: Double
                            )

  type JobId = JobId.Type
  object JobId extends NewtypeWrapped[Long] with DerivedCirceCodec


  sealed abstract class JobState extends EnumEntry with Lowercase

  object JobState extends Enum[JobState] {
    final case object NeverRun extends JobState
    final case object Pending extends JobState
    final case object Completed extends JobState

    override def values: IndexedSeq[JobState] = findValues
  }


  final case class CrawlerJob(jobId: JobId, from: ListingId, to: ListingId, lastRun: Option[Instant], state: JobState)


}
