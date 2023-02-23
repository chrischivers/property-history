package uk.co.thirdthing.store

import cats.effect.Resource
import cats.effect.kernel.Sync
import cats.syntax.all.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import uk.co.thirdthing.model.Model.CrawlerJob.{LastRunCompleted, LastRunStarted}
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId, JobState}
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.store.JobStore.JobStoreRecord
import uk.co.thirdthing.utils.TimeUtils.*
import uk.co.thirdthing.config.JobSchedulingConfig
import cats.effect.kernel.Clock

import java.time.{Instant, LocalDateTime, ZoneId}

trait PostcodeStore[F[_]]:
  def getAndLockNextPostcode: F[Option[Postcode]]

object PostgresPostcodeStore:

  def apply[F[_]: Sync: Clock](pool: Resource[F, Session[F]], jobSchedulingConfig: JobSchedulingConfig): PostcodeStore[F] = new PostcodeStore[F]:

    private val getAndLockNextPostcodeQuery: Query[(LocalDateTime, LocalDateTime), String] =
      sql"""
          UPDATE postcodes
          SET lockedAt=$timestamp
          WHERE postcode IN (
             SELECT postcode
             FROM postcodes
             WHERE inUse=true AND (lockedAt IS NULL OR lockedAt < $timestamp)
             ORDER BY lastScraped ASC NULLS FIRST
             LIMIT 1
           )
         RETURNING postcode""".query(varchar(12))

    override def getAndLockNextPostcode: F[Option[Postcode]] =
      Clock[F].realTimeInstant.flatMap { now =>
        val expiredCutoff =
          now.minusMillis(jobSchedulingConfig.jobExpiryTimeSinceScheduled.toMillis).toLocalDateTime
        pool
          .use(_.prepare(getAndLockNextPostcodeQuery))
          .flatMap(_.option((now.toLocalDateTime, expiredCutoff)))
          .map(_.map(Postcode(_)))
      }
