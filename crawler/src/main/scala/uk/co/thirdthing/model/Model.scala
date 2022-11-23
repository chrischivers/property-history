package uk.co.thirdthing.model

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import monix.newtypes.NewtypeWrapped
import monix.newtypes.integrations.DerivedCirceCodec
import uk.co.thirdthing.model.Model.CrawlerJob._
import uk.co.thirdthing.model.Types.{DateAdded, LastChange, ListingId}

import java.time.Instant

object Model {


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
                               lastChange: Option[LastChange],
                               latestDateAdded: Option[DateAdded]
  )

  object CrawlerJob {
    type LastRunScheduled = LastRunScheduled.Type
    object LastRunScheduled extends NewtypeWrapped[Instant] with DerivedCirceCodec

    type LastRunCompleted = LastRunCompleted.Type
    object LastRunCompleted extends NewtypeWrapped[Instant] with DerivedCirceCodec

  }

  final case class RunJobCommand(jobId: JobId)

  object RunJobCommand {
    implicit val codec: Codec[RunJobCommand] = deriveCodec
  }



}
