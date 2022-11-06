package uk.co.thirdthing.service

import cats.effect.Sync
import cats.kernel.Order
import uk.co.thirdthing.Rightmove.ListingId
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId, JobState}
import cats.syntax.all._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.clients.RightmoveApiClient
import uk.co.thirdthing.config.JobSeederConfig
import uk.co.thirdthing.store.JobStore

trait JobSeeder[F[_]] {
  def seed: F[Unit]
}

object JobSeeder {

  def apply[F[_]: Sync](rightmoveApiClient: RightmoveApiClient[F], jobStore: JobStore[F], config: JobSeederConfig) = new JobSeeder[F] {

    implicit val logger = Slf4jLogger.getLogger[F]

    private def getLatestListingIdFrom(from: ListingId): F[Option[ListingId]] = {
      def helper(lastFoundListing: Option[ListingId], nextToTry: ListingId, emptyRecordsSince: Long): F[Option[ListingId]] =
        if (emptyRecordsSince >= config.emptyRecordsToDetermineLatest) lastFoundListing.pure
        else {
          rightmoveApiClient.listingDetails(nextToTry).flatMap {
            case None =>
              (if (emptyRecordsSince % 100 == 0) logger.info(s"Empty records since last record found: $emptyRecordsSince") else ().pure[F]) *>
                helper(lastFoundListing, ListingId(nextToTry.value + 1), emptyRecordsSince + 1)
            case Some(_) =>
              logger.info(s"Found listing for ${nextToTry.value}. Continuing to scan") *>
                helper(nextToTry.some, ListingId(nextToTry.value + 1), emptyRecordsSince = 0)
          }
        }
      logger
        .info(s"Attempting to get latest listing id, starting at ${from.value}. Empty records required ${config.emptyRecordsToDetermineLatest}") *>
        helper(None, ListingId(from.value + 1), 0)
    }

    private def getLatestListingFor(lastJob: CrawlerJob): F[Option[ListingId]] =
      getLatestListingIdFrom(lastJob.to)

    private def jobsToCreate(from: ListingId, to: ListingId): List[CrawlerJob] =
      (from.value to to.value).foldLeft(List.empty[CrawlerJob]) {
        case (agg, id) =>
          if (id % config.jobChunkSize == 0) {
            val jobId = JobId(id / config.jobChunkSize + 1)
            agg :+ CrawlerJob(jobId, ListingId(id), ListingId(id + config.jobChunkSize), JobState.NeverRun, None, None, None)
          } else agg
      }

    override def seed: F[Unit] =
      logger.info("Running job seed") *>
        jobStore.getLatestJob
          .flatMap {
            case None =>
              logger.info(s"No latest job retrieved. Creating from scratch") *>
                jobsToCreate(ListingId(0), ListingId(config.startingMaxListingIdForFirstRun)).pure[F]
            case Some(job) =>
              logger.info(s"Retrieved latest job: $job") *>
                getLatestListingFor(job).map(_.fold(List.empty[CrawlerJob])(jobsToCreate(job.to, _)))
          }
          .flatTap(jobs => logger.info(s"${jobs.size} jobs retrieved for adding"))
          .flatMap(jobs => fs2.Stream.emits(jobs).through(jobStore.streamPut).compile.drain)
          .flatTap(_ => logger.info("Job insertion complete"))

  }

}
