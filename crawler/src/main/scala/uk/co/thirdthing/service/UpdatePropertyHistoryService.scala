package uk.co.thirdthing.service

import cats.effect.kernel.{Async, Clock}
import cats.syntax.all.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.metrics.MetricsRecorder
import uk.co.thirdthing.model.Model.CrawlerJob.LastRunCompleted
import uk.co.thirdthing.model.Model.*
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.RetrievalService.RetrievalResult
import uk.co.thirdthing.store.{JobStore, PropertyStore}

trait UpdatePropertyHistoryService[F[_]]:
  def run(jobId: JobId): F[Unit]
  
object UpdatePropertyHistoryService:

  private final case class Result(lastChange: LastChange, dateAdded: DateAdded)

  def apply[F[_]: Async: Clock](
    jobStore: JobStore[F],
    propertyStore: PropertyStore[F],
    retrievalService: RetrievalService[F],
    metricsRecorder: MetricsRecorder[F]
  ) =
    new UpdatePropertyHistoryService[F]:

      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      override def run(jobId: JobId): F[Unit] =
        jobStore.getAndLock(jobId).flatMap {
          case None => handleJobNotExisting(jobId)
          case Some(job) if job.state.schedulable =>
            logger.warn(s"Job for ${jobId.value} is not in a runnable state. Ignoring")
          case Some(job) => withDurationMetricReporting(jobId)(runJob(job))
        }

      private def withDurationMetricReporting[T](jobId: JobId)(f: F[T]): F[T] =
        Clock[F].realTime.flatMap { startTime =>
          f.flatMap(r =>
            Clock[F].realTime.flatMap { endTime =>
              val duration = endTime - startTime
              logger.info(s"${jobId.value} finished in ${duration.toMinutes} minutes") *>
                metricsRecorder.recordJobDuration("property-history-crawler")(duration).as(r)
            }
          )
        }

      private def handleJobNotExisting(jobId: JobId): F[Unit] =
        val msg = s"Job for ${jobId.value} does not exist in store"
        logger.error(msg) *> new IllegalStateException(msg).raiseError[F, Unit]

      private def runJob(job: CrawlerJob): F[Unit] =

        val totalJobs         = job.to.value - job.from.value
        val reportingSegments = totalJobs / 5

        def handleProgressReporting(listingId: Long) =
          val jobNumber = listingId - job.from.value
          if jobNumber % reportingSegments == 0 && jobNumber != 0 then
            val percentage = Math.round((jobNumber / totalJobs.toDouble) * 100)
            logger.info(s"$percentage% complete of job id ${job.jobId.value}")
          else ().pure[F]

        fs2.Stream
          .emits[F, Long](job.from.value to job.to.value)
          .evalTap(handleProgressReporting)
          .map(ListingId(_))
          .parEvalMap(5)(runAndUpdate)
          .compile
          .toList
          .flatMap(results => handleRunResults(job, results.flatten))

      private def handleRunResults(job: CrawlerJob, results: List[Result]) =
        if results.isEmpty then
          logger.info(s"No records updated for crawler job ${job.jobId.value}") *>
            updateJobStoreWhenNoDataChanges(job)
        else
          logger.info(
            s"${results.size} of ${job.to.value - job.from.value} records updated for crawler job ${job.jobId.value}"
          ) *>
            updateJobStoreWhenDataChanges(job, results)

      private def updateJobStoreWhenDataChanges(job: CrawlerJob, results: List[Result]): F[Unit] =
        val latestLastChange = results.maxBy(_.lastChange.value).lastChange
        val latestDateAdded  = results.maxBy(_.dateAdded.value).dateAdded

        Clock[F].realTimeInstant.flatMap { now =>
          jobStore.put(
            job.copy(
              lastRunCompleted = LastRunCompleted(now).some,
              lastChange = latestLastChange.some,
              latestDateAdded = latestDateAdded.some,
              state = JobState.Completed
            )
          )
        }

      private def updateJobStoreWhenNoDataChanges(job: CrawlerJob) =
        Clock[F].realTimeInstant.flatMap(now =>
          jobStore.put(job.copy(lastRunCompleted = LastRunCompleted(now).some, state = JobState.Completed))
        )

      private def runAndUpdate(listingId: ListingId): F[Option[Result]] =
        propertyStore
          .getMostRecentListing(listingId)
          .flatMap { existingPropertyRecord =>
            retrievalService.retrieve(listingId).flatMap[Option[Result]] { retrievedRecord =>
              (existingPropertyRecord, retrievedRecord) match
                case (None, None) =>
                  logger.debug(s"No change for non-existing listing ${listingId.value}").as(none)
                case (Some(existing), Some(retrieved)) =>
                  handleBothExisting(existing, retrieved)
                case (Some(existing), None) => handleDelete(existing).map(_.some)
                case (None, Some(retrieved)) =>
                  logger.debug(s"New listing ${listingId.value} found. Adding to store") *>
                    updateStores(retrieved).map(_.some)
            }
          }
          .handleErrorWith(err =>
            logger.error(err)(s"Error encountered when running listing Id $listingId") *> err.raiseError
          )

      private def handleBothExisting(
        existingRecord: ListingSnapshot,
        retrievalResult: RetrievalResult
      ): F[Option[Result]] =
        if existingRecord.details == retrievalResult.propertyDetails then
          logger.debug(s"No change for existing listing ${retrievalResult.listingId.value}").as(Option.empty[Result])
        else
          logger.debug(
            s"Listing ${retrievalResult.listingId.value} has changed. Updating store."
          ) *>
            updateStores(retrievalResult).map(Some(_))

      private def handleDelete(existingRecord: ListingSnapshot): F[Result] =
        Clock[F].realTimeInstant.flatMap { now =>
          val snapshotToUpdate =
            ListingSnapshot(
              existingRecord.listingId,
              LastChange(now),
              existingRecord.propertyId,
              existingRecord.dateAdded,
              PropertyDetails.Deleted
            )
          logger.debug(s"Previously existing listing ${existingRecord.listingId.value} no longer exists") *>
            propertyStore.putListingSnapshot(snapshotToUpdate) *>
            Result(LastChange(now), snapshotToUpdate.dateAdded).pure[F]
        }

      private def updateStores(result: RetrievalResult): F[Result] =
        Clock[F].realTimeInstant.flatMap { now =>
          val listingSnapshot =
            ListingSnapshot(
              result.listingId,
              LastChange(now),
              result.propertyId,
              result.dateAdded,
              result.propertyDetails
            )

          propertyStore.putListingSnapshot(listingSnapshot) *>
            Result(LastChange(now), listingSnapshot.dateAdded).pure[F]
        }
