package uk.co.thirdthing.service

import cats.Parallel
import cats.effect.kernel.{Async, Clock, Sync}
import cats.syntax.all._
import monix.newtypes.NewtypeWrapped
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.CrawlerJob.{LastChange, LastRunCompleted}
import uk.co.thirdthing.model.Model.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Model._
import uk.co.thirdthing.service.RetrievalService.RetrievalResult
import uk.co.thirdthing.store.{JobStore, PropertyListingStore}
import uk.co.thirdthing.utils.Hasher
import uk.co.thirdthing.utils.Hasher.Hash

import java.time.Instant

trait JobRunnerService[F[_]] {
  def run(jobId: JobId): F[Unit]
}
object JobRunnerService {

  private type UpdateTimestamp = UpdateTimestamp.Type
  private object UpdateTimestamp extends NewtypeWrapped[Instant]

  def apply[F[_]: Async](
    jobStore: JobStore[F],
    propertyListingStore: PropertyListingStore[F],
    retrievalService: RetrievalService[F]
  )(implicit clock: Clock[F]) =
    new JobRunnerService[F] {

      implicit val logger = Slf4jLogger.getLogger[F]

      override def run(jobId: JobId): F[Unit] =
        jobStore.get(jobId).flatMap {
          case None                               => handleJobNotExisting(jobId)
          case Some(job) if job.state.schedulable => logger.warn(s"Job for ${jobId.value} is not in a runnable state. Ignoring")
          case Some(job)                          => runJob(job)
        }

      private def handleJobNotExisting(jobId: JobId): F[Unit] = {
        val msg = s"Job for ${jobId.value} does not exist in store"
        logger.error(msg) *> new IllegalStateException(msg).raiseError[F, Unit]
      }

      private def runJob(job: CrawlerJob): F[Unit] =
        fs2.Stream
          .emits[F, Long](job.from.value to job.to.value)
          .evalTap(i => if (i % 1000 == 0) logger.info(s"Handling listing $i for job ${job.jobId.value}") else ().pure[F])
          .map(ListingId(_))
          .parEvalMap(5)(runAndUpdate)
          .compile
          .toList
          .flatMap(handleRunResults(job, _))

      private def handleRunResults(job: CrawlerJob, results: List[Option[UpdateTimestamp]]) = results match {
        case Nil =>
          val error = s"No listings in range for crawler job ${job.jobId.value}"
          logger.error(error) *>
            new IllegalStateException(s"No listings in range for crawler job ${job.jobId.value}").raiseError[F, Unit]
        case result =>
          val updateTimestamps = result.collect {
            case Some(timestamp) => timestamp
          }
          if (updateTimestamps.isEmpty) {
            logger.info(s"No records updated for crawler job ${job.jobId.value}") *>
              updateJobStoreWhenNoDataChanges(job)
          } else {
            logger.info(s"${updateTimestamps.size} of ${job.to.value - job.from.value} records updated for crawler job ${job.jobId.value}") *>
              updateJobStoreWhenDataChanges(job, LastChange(updateTimestamps.maxBy(_.value).value))
          }

      }

      private def updateJobStoreWhenDataChanges(job: CrawlerJob, lastChange: LastChange): F[Unit] =
        clock.realTimeInstant.flatMap { now =>
          jobStore.put(job.copy(lastRunCompleted = LastRunCompleted(now).some, lastChange = lastChange.some, state = JobState.Completed))
        }

      private def updateJobStoreWhenNoDataChanges(job: CrawlerJob) =
        clock.realTimeInstant.flatMap(now => jobStore.put(job.copy(lastRunCompleted = LastRunCompleted(now).some, state = JobState.Completed)))

      private def runAndUpdate(listingId: ListingId): F[Option[UpdateTimestamp]] =
        propertyListingStore.get(listingId).flatMap { existingPropertyRecord =>
          retrievalService.retrieve(listingId).flatMap { retrievedRecord =>
            (existingPropertyRecord, retrievedRecord) match {
              case (None, None) =>
                logger.debug(s"No change for non-existing listing ${listingId.value}").as(Option.empty[UpdateTimestamp])
              case (Some(existing), Some(retrieved)) =>
                handleBothExisting(existing, retrieved)
              case (Some(existing), None) => handleDelete(existing).map(Some(_))
              case (None, Some(retrieved)) =>
                logger.debug(s"New listing ${listingId.value} found. Adding to store") *>
                  Hasher.hash(retrieved.propertyDetails).flatMap(updateStores(retrieved, _)).map(Some(_))
            }

          }
        }

      private def handleBothExisting(existingRecord: Property, retrievalResult: RetrievalResult): F[Option[UpdateTimestamp]] =
        Hasher.hash(retrievalResult.propertyDetails).flatMap { retrievalResultDetailsChecksum =>
          if (existingRecord.detailsChecksum.value == retrievalResultDetailsChecksum.value) {
            logger.debug(s"No change for existing listing ${retrievalResult.listingId.value}").as(Option.empty[UpdateTimestamp])
          } else {
            logger.debug(
              s"Listing ${retrievalResult.listingId.value} has changed. Updating store."
            ) *>
              updateStores(retrievalResult, retrievalResultDetailsChecksum).map(Some(_))
          }
        }

      private def handleDelete(existingRecord: Property): F[UpdateTimestamp] =
        clock.realTimeInstant.flatMap { now =>
          val listingSnapshotId = ListingSnapshotId.generate
          val snapshotToUpdate =
            ListingSnapshot(existingRecord.listingId, LastChange(now), existingRecord.propertyId, existingRecord.dateAdded, listingSnapshotId, None)
          logger.debug(s"Previously existing listing ${existingRecord.listingId.value} no longer exists. Deleting from store.") *>
            propertyListingStore.delete(snapshotToUpdate) *>
            UpdateTimestamp(now).pure[F]
        }

      private def updateStores(result: RetrievalResult, detailsHash: Hash): F[UpdateTimestamp] =
        clock.realTimeInstant.flatMap { now =>
          val listingSnapshotId = ListingSnapshotId.generate
          val propertyRecord    = Property(result.listingId, result.propertyId, result.dateAdded, listingSnapshotId, detailsHash)
          val listingSnapshot =
            ListingSnapshot(result.listingId, LastChange(now), result.propertyId, result.dateAdded, listingSnapshotId, result.propertyDetails.some)

          propertyListingStore.put(propertyRecord, listingSnapshot) *>
            UpdateTimestamp(now).pure[F]
        }

    }

}
