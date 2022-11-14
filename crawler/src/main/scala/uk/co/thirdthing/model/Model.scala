package uk.co.thirdthing.model

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import enumeratum.values._
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder}
import monix.newtypes.NewtypeWrapped
import monix.newtypes.integrations.DerivedCirceCodec
import org.apache.commons.lang3.RandomStringUtils
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.model.Model.CrawlerJob._
import uk.co.thirdthing.model.Model.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.utils.Hasher.Hash

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

  final case class Property(listingId: ListingId, propertyId: PropertyId, dateAdded: DateAdded, listingSnapshotId: ListingSnapshotId, detailsChecksum: Hash)

  final case class PropertyDetails(
    price: Price,
    transactionTypeId: TransactionType,
    visible: Boolean,
    status: ListingStatus,
    rentFrequency: Option[String],
    latitude: Option[Double],
    longitude: Option[Double]
  )

  object PropertyDetails {
    implicit val codec: Codec[PropertyDetails] = deriveCodec
  }

  type JobId = JobId.Type
  object JobId extends NewtypeWrapped[Long] with DerivedCirceCodec

  sealed abstract class JobState(val schedulable: Boolean) extends EnumEntry with Lowercase

  object JobState extends Enum[JobState] {
    final case object NeverRun  extends JobState(schedulable = true)
    final case object Pending   extends JobState(schedulable = false)
    final case object Completed extends JobState(schedulable = true)

    override def values: IndexedSeq[JobState] = findValues
  }

  final case class CrawlerJob(
                               jobId: JobId,
                               from: ListingId,
                               to: ListingId,
                               state: JobState,
                               lastRunScheduled: Option[LastRunScheduled],
                               lastRunCompleted: Option[LastRunCompleted],
                               lastChange: Option[LastChange]
  )

  object CrawlerJob {
    type LastRunScheduled = LastRunScheduled.Type
    object LastRunScheduled extends NewtypeWrapped[Instant] with DerivedCirceCodec

    type LastRunCompleted = LastRunCompleted.Type
    object LastRunCompleted extends NewtypeWrapped[Instant] with DerivedCirceCodec

    type LastChange = LastChange.Type
    object LastChange extends NewtypeWrapped[Instant] with DerivedCirceCodec
  }

  final case class RunJobCommand(jobId: JobId)

  object RunJobCommand {
    implicit val codec: Codec[RunJobCommand] = deriveCodec
  }


  final case class ListingSnapshot(listingId: ListingId, lastChange: LastChange, propertyId: PropertyId, dateAdded: DateAdded, listingSnapshotId: ListingSnapshotId, details: Option[PropertyDetails])

  object ListingSnapshot {
    type ListingSnapshotId = ListingSnapshotId.Type
    object ListingSnapshotId extends NewtypeWrapped[String] with DerivedCirceCodec {
      def generate: ListingSnapshotId = ListingSnapshotId(RandomStringUtils.randomAlphanumeric(12))
    }
  }
}
