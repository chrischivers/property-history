package uk.co.thirdthing.service

import cats.effect.Sync
import cats.kernel.Order
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId, JobState}
import cats.syntax.all.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.clients.RightmoveApiClient
import uk.co.thirdthing.config.JobSeederConfig
import uk.co.thirdthing.model.Types.ListingId
import uk.co.thirdthing.store.JobStore

trait JobSeeder[F[_]]:
  def seed: F[Unit]

object JobSeeder:

  def apply[F[_]: Sync](rightmoveApiClient: RightmoveApiClient[F], jobStore: JobStore[F], config: JobSeederConfig) =
    new JobSeeder[F]:

      implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      private def getLatestListingIdFrom(from: ListingId): F[Option[ListingId]] =

        def helper(
          lastFoundListing: Option[ListingId],
          nextToTry: ListingId,
          chunkStartedAt: ListingId,
          emptyRecordsSince: Long
        ): F[Option[ListingId]] =
          if emptyRecordsSince >= config.emptyRecordsToDetermineLatest then lastFoundListing.pure
          else
            rightmoveApiClient.listingDetails(nextToTry).flatMap {
              case None =>
                (if emptyRecordsSince % 100 == 0 then
                   logger.info(s"Empty records since last record found: $emptyRecordsSince")
                 else ().pure[F]) *> {
                  val next       = nextToTry.value + 1
                  val chunkStart = if next % config.jobChunkSize == 0 then ListingId(next) else chunkStartedAt
                  helper(lastFoundListing, ListingId(next), chunkStart, emptyRecordsSince + 1)
                }
              case Some(_) =>
                logger.info(s"Found listing for ${nextToTry.value}. Continuing to scan") *> {
                  val nextChunk = ListingId(chunkStartedAt.value + config.jobChunkSize)
                  helper(nextToTry.some, nextChunk, nextChunk, emptyRecordsSince = 0)
                }
            }
        logger
          .info(
            s"Attempting to get latest listing id, starting at ${from.value}. Empty records required ${config.emptyRecordsToDetermineLatest}"
          ) *>
          helper(None, ListingId(from.value), ListingId(from.value), 0)

      private def getLatestListingFor(lastJob: CrawlerJob): F[Option[ListingId]] =
        getLatestListingIdFrom(ListingId(lastJob.to.value + 1))

      private def jobsToCreate(from: ListingId, to: ListingId): List[CrawlerJob] =
        (from.value to to.value).foldLeft(List.empty[CrawlerJob]) { case (agg, id) =>
          if (id - 1) % config.jobChunkSize == 0 then
            val jobId = JobId((id - 1) / config.jobChunkSize + 1)
            agg :+ CrawlerJob(
              jobId,
              ListingId(id),
              ListingId(id + (config.jobChunkSize - 1)),
              JobState.NeverRun,
              None,
              None,
              None,
              None
            )
          else agg
        }

      private def persistJobs(jobs: List[CrawlerJob]): F[Unit] =
        fs2.Stream
          .emits(jobs)
          .evalTap(jobStore.put)
          .compile
          .drain

      override def seed: F[Unit] =
        logger.info("Running job seed") *>
          jobStore.getLatestJob
            .flatMap {
              case None =>
                logger.info(s"No latest job retrieved. Creating from scratch") *>
                  jobsToCreate(ListingId(1), ListingId(config.startingMaxListingIdForFirstRun)).pure[F]
              case Some(job) =>
                logger.info(s"Retrieved latest job: $job") *>
                  getLatestListingFor(job)
                    .map(_.fold(List.empty[CrawlerJob])(jobsToCreate(job.to, _)))
                    .flatTap(jobs => logger.info("Jobs to create: " + jobs))
            }
            .flatTap(jobs => logger.info(s"${jobs.size} jobs retrieved for adding"))
            .flatTap(persistJobs)
            .flatTap(_ => logger.info("Job insertion complete"))
            .void
