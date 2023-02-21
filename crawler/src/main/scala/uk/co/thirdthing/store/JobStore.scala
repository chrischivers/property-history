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

import java.time.LocalDateTime
import cats.effect.kernel.Clock

trait JobStore[F[_]]:
  def put(job: CrawlerJob): F[Unit]
  def getAndLock(jobId: JobId): F[Option[CrawlerJob]]
  def jobs: fs2.Stream[F, CrawlerJob]
  def getLatestJob: F[Option[CrawlerJob]]
  def nextJobToRun: F[Option[CrawlerJob]]

object JobStore:
  private[store] final case class JobStoreRecord(
    jobId: Long,
    from: Long,
    to: Long,
    state: String,
    lastRunStarted: Option[LocalDateTime],
    lastRunCompleted: Option[LocalDateTime],
    lastChange: Option[LocalDateTime],
    latestDateAdded: Option[LocalDateTime]
  ):
    // TODO this is not safe
    def toCrawlerJob: CrawlerJob =
      CrawlerJob(
        JobId(this.jobId),
        ListingId(this.from),
        ListingId(this.to),
        JobState.withName(this.state),
        this.lastRunStarted.map(v => LastRunStarted(v.asInstant)),
        this.lastRunCompleted.map(v => LastRunCompleted(v.asInstant)),
        this.lastChange.map(v => LastChange(v.asInstant)),
        this.latestDateAdded.map(v => DateAdded(v.asInstant))
      )

  private[store] def jobStoreRecordFrom(job: CrawlerJob): JobStoreRecord =
    JobStoreRecord(
      job.jobId.value,
      job.from.value,
      job.to.value,
      job.state.entryName,
      job.lastRunStarted.map(_.value.toLocalDateTime),
      job.lastRunCompleted.map(_.value.toLocalDateTime),
      job.lastChange.map(_.value.toLocalDateTime),
      job.latestDateAdded.map(_.value.toLocalDateTime)
    )

object PostgresJobStore:

  def apply[F[_]: Sync: Clock](pool: Resource[F, Session[F]], jobSchedulingConfig: JobSchedulingConfig) =
    new JobStore[F]:

      private val insertJobCommand: Command[JobStoreRecord] =
        sql"""
             INSERT INTO jobs(jobId, fromJob, toJob, state, lastRunStarted, lastRunCompleted, lastChange, latestDateAdded) VALUES
             ($int8, $int8, $int8, ${varchar(
            24
          )}, ${timestamp.opt}, ${timestamp.opt}, ${timestamp.opt}, ${timestamp.opt})
         """.command.contramap { (js: JobStoreRecord) =>
          js.jobId ~ js.from ~ js.to ~ js.state ~ js.lastRunStarted ~ js.lastRunCompleted ~ js.lastChange ~ js.latestDateAdded
        }

      private val updateJobCommand: Command[JobStoreRecord] =
        sql"""
             UPDATE jobs
             SET jobId=$int8, fromJob=$int8, toJob=$int8, state=${varchar(
            24
          )}, lastRunStarted=${timestamp.opt}, lastRunCompleted=${timestamp.opt}, lastChange=${timestamp.opt}, latestDateAdded=${timestamp.opt}
             WHERE jobId=$int8
         """.command.contramap { (js: JobStoreRecord) =>
          js.jobId ~ js.from ~ js.to ~ js.state ~ js.lastRunStarted ~ js.lastRunCompleted ~ js.lastChange ~ js.latestDateAdded ~ js.jobId
        }

      private val updateJobStateToPendingCommand: Query[((LocalDateTime, Long), LocalDateTime), JobStoreRecord] =
        sql"""
             UPDATE jobs 
             SET state='pending', lastRunStarted=$timestamp
             WHERE jobId = $int8 AND (state IN ('neverrun', 'completed') OR (state = 'pending' AND lastRunStarted < $timestamp))
             RETURNING jobId, fromJob, toJob, state, lastRunStarted, lastRunCompleted, lastChange, latestDateAdded
             """
          .query(int8 ~ int8 ~ int8 ~ varchar(24) ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt)
          .gmap[JobStoreRecord]

      private val getJobsQuery: Query[Void, JobStoreRecord] =
        sql"""
         SELECT jobId, fromJob, toJob, state, lastRunStarted, lastRunCompleted, lastChange, latestDateAdded
         FROM jobs
         """
          .query(int8 ~ int8 ~ int8 ~ varchar(24) ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt)
          .gmap[JobStoreRecord]

      private val getLatestJobQuery: Query[Void, JobStoreRecord] =
        sql"""
             SELECT jobId, fromJob, toJob, state, lastRunStarted, lastRunCompleted, lastChange, latestDateAdded
             FROM jobs
             ORDER BY toJob DESC
             LIMIT 1
             """
          .query(int8 ~ int8 ~ int8 ~ varchar(24) ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt)
          .gmap[JobStoreRecord]

      private val getNextJobQuery: Query[LocalDateTime, JobStoreRecord] =
        sql"""
            SELECT jobId, fromJob, toJob, state, lastRunStarted, lastRunCompleted, lastChange, latestDateAdded
            FROM jobs
            WHERE state IN ('neverrun', 'completed') OR (state = 'pending' AND lastRunStarted < $timestamp)
            ORDER BY lastRunCompleted ASC NULLS FIRST, toJob DESC
            LIMIT 1
            """
          .query(int8 ~ int8 ~ int8 ~ varchar(24) ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt)
          .gmap[JobStoreRecord]

      override def put(job: CrawlerJob): F[Unit] =
        pool.use { session =>
          val jobRecord = JobStore.jobStoreRecordFrom(job)
          session
            .prepare(insertJobCommand)
            .use(_.execute(jobRecord).void)
            .recoverWith { case SqlState.UniqueViolation(ex) =>
              session.prepare(updateJobCommand).use(_.execute(jobRecord).void)
            }
        }

      override def getAndLock(jobId: JobId): F[Option[CrawlerJob]] = Clock[F].realTimeInstant.flatMap { now =>
        val pendingJobExpiredCutoff =
          now.minusMillis(jobSchedulingConfig.jobExpiryTimeSinceScheduled.toMillis).toLocalDateTime
        pool
          .use(
            _.prepare(updateJobStateToPendingCommand)
              .use(_.option((now.toLocalDateTime, jobId.value), pendingJobExpiredCutoff))
          )
          .map(_.map(_.toCrawlerJob))
      }

      override def jobs: fs2.Stream[F, CrawlerJob] =
        for
          db      <- fs2.Stream.resource(pool)
          getJobs <- fs2.Stream.resource(db.prepare(getJobsQuery))
          result  <- getJobs.stream(Void, 16).map(_.toCrawlerJob)
        yield result

      override def getLatestJob: F[Option[CrawlerJob]] =
        pool.use(_.prepare(getLatestJobQuery).use(_.option(Void))).map(_.map(_.toCrawlerJob))

      override def nextJobToRun: F[Option[CrawlerJob]] = Clock[F].realTimeInstant.flatMap { now =>
        val pendingJobExpiredCutoff =
          now.minusMillis(jobSchedulingConfig.jobExpiryTimeSinceScheduled.toMillis).toLocalDateTime
        pool.use(_.prepare(getNextJobQuery).use(_.option(pendingJobExpiredCutoff))).map(_.map(_.toCrawlerJob))
      }
