package uk.co.thirdthing.service

import cats.effect.kernel.{Clock, Sync}
import cats.syntax.all._
import com.softwaremill.diffx.compare
import com.softwaremill.diffx.generic.auto._
import monix.newtypes.NewtypeWrapped
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.CrawlerJob.{LastDataChange, LastRunCompleted}
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId}
import uk.co.thirdthing.store.{JobStore, PropertyStore}

trait JobRunnerService[F[_]] {
  def run(jobId: JobId): F[Unit]
}
object JobRunnerService {

  private type HasUpdated = HasUpdated.Type
  object HasUpdated extends NewtypeWrapped[Boolean]

  def apply[F[_]: Sync: Clock](jobStore: JobStore[F], propertyStore: PropertyStore[F], retrievalService: RetrievalService[F]) =
    new JobRunnerService[F] {

      implicit val logger = Slf4jLogger.getLogger[F]

      override def run(jobId: JobId): F[Unit] =
        jobStore.get(jobId).flatMap {
          case None                                => handleJobNotExisting(jobId)
          case Some(job) if !job.state.schedulable => logger.warn(s"Job for ${jobId.value} is not in schedulable state. Ignoring")
          case Some(job)                           => runJob(job)
        }

      private def handleJobNotExisting(jobId: JobId): F[Unit] = {
        val msg = s"Job for ${jobId.value} does not exist in store"
        logger.error(msg) *> new IllegalStateException(msg).raiseError[F, Unit]
      }

      private def runJob(job: CrawlerJob): F[Unit] =
        (job.from.value to job.to.value).map(ListingId(_)).toList.traverse(runAndUpdate).flatMap {
          case Nil =>
            val error = s"No listings in range for crawler job $job"
            logger.error(error) *>
              new IllegalStateException(s"No listings in range for crawler job ${job.jobId.value}").raiseError[F, Unit]
          case result if result.exists(_.value) =>
            logger.info(s"${result.count(_.value)} of ${result.size} records updated for crawler job ${job.jobId.value}") *>
              updateJobStoreWhenDataChanges(job)
          case result =>
            logger.info(s"No records updated for crawler job ${job.jobId.value}") *>
              updateJobStoreWhenNoDataChanges(job)

        }

      private def updateJobStoreWhenDataChanges(job: CrawlerJob) =
        Clock[F].realTimeInstant.flatMap(now => jobStore.put(job.copy(lastRunCompleted = LastRunCompleted(now).some, lastDataChange = LastDataChange(now).some)))

      private def updateJobStoreWhenNoDataChanges(job: CrawlerJob) =
        Clock[F].realTimeInstant.flatMap(now => jobStore.put(job.copy(lastRunCompleted = LastRunCompleted(now).some)))

      private def runAndUpdate(listingId: ListingId): F[HasUpdated] =
        propertyStore.get(listingId).flatMap { existingPropertyRecord =>
          retrievalService.retrieve(listingId).flatMap { currentPropertyRecord =>
            (existingPropertyRecord, currentPropertyRecord) match {
              case (None, None) =>
                logger.debug(s"No change for non-existing listing ${listingId.value}").as(HasUpdated(false))
              case (Some(existing), Some(current)) if existing == current =>
                logger.debug(s"No change for existing listing ${listingId.value}").as(HasUpdated(false))
              case (Some(_), None) =>
                //Need to keep track of this in a new history service
                logger.debug(s"Previously existing listing ${listingId.value} no longer exists. Deleting from store.") *>
                  propertyStore.delete(listingId).as(HasUpdated(true))
              case (None, Some(property)) =>
                logger.debug(s"New listing ${listingId.value} found. Adding to store") *>
                  propertyStore.put(property).as(HasUpdated(true))
              case (Some(existing), Some(current)) =>
                logger.debug(
                  s"Listing ${listingId.value} has changed. Updating store.\n" +
                    s"${compare(existing, current).show()}\n"
                ) *>
                  propertyStore.put(current).as(HasUpdated(true))
            }

          }
        }

    }

}
