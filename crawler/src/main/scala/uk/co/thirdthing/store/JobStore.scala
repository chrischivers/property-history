package uk.co.thirdthing.store

import cats.effect.Resource
import cats.effect.kernel.Sync
import cats.syntax.all._
import fs2.Pipe
import skunk._
import skunk.codec.all._
import skunk.implicits._
import uk.co.thirdthing.model.Model.CrawlerJob.{LastRunCompleted, LastRunScheduled}
import uk.co.thirdthing.model.Model.{CrawlerJob, JobId, JobState}
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.store.PropertyStore.PropertyRecord
import uk.co.thirdthing.utils.TimeUtils._
import JobStore.JobStoreRecord

import java.time.{LocalDateTime, ZoneId}

trait JobStore[F[_]] {
  def put(job: CrawlerJob): F[Unit]
  def get(jobId: JobId): F[Option[CrawlerJob]]
  def jobs: fs2.Stream[F, CrawlerJob]
  def getLatestJob: F[Option[CrawlerJob]]
  def getNextJob: F[Option[CrawlerJob]]
}

object JobStore {
  private[store] final case class JobStoreRecord(
    jobId: Long,
    from: Long,
    to: Long,
    state: String,
    lastRunScheduled: Option[LocalDateTime],
    lastRunCompleted: Option[LocalDateTime],
    lastChange: Option[LocalDateTime],
    latestDateAdded: Option[LocalDateTime]
  ) {
    // TODO this is not safe
    def toCrawlerJob: CrawlerJob =
      CrawlerJob(
        JobId(this.jobId),
        ListingId(this.from),
        ListingId(this.to),
        JobState.withName(this.state),
        this.lastRunScheduled.map(v => LastRunScheduled(v.asInstant)),
        this.lastRunCompleted.map(v => LastRunCompleted(v.asInstant)),
        this.lastChange.map(v => LastChange(v.asInstant)),
        this.latestDateAdded.map(v => DateAdded(v.asInstant))
      )
  }

  private[store] def jobStoreRecordFrom(job: CrawlerJob): JobStoreRecord =
    JobStoreRecord(
      job.jobId.value,
      job.from.value,
      job.to.value,
      job.state.entryName,
      job.lastRunScheduled.map(_.value.toLocalDateTime),
      job.lastRunCompleted.map(_.value.toLocalDateTime),
      job.lastChange.map(_.value.toLocalDateTime),
      job.latestDateAdded.map(_.value.toLocalDateTime)
    )
}

object PostgresJobStore {

  def apply[F[_]: Sync](pool: Resource[F, Session[F]]) = new JobStore[F] {

    private val insertJobCommand: Command[JobStoreRecord] =
      sql"""
             INSERT INTO jobs(jobId, from, to, state, lastRunScheduled, lastRunCompleted, lastChange, latestDateAdded) VALUES
             ($int8, $int8, $int8, ${varchar(24)}, ${timestamp.opt}, ${timestamp.opt}, ${timestamp.opt}, ${timestamp.opt})
         """.command.contramap { (js: JobStoreRecord) =>
        js.jobId ~ js.from ~ js.to ~ js.state ~ js.lastRunScheduled ~ js.lastRunCompleted ~ js.lastChange ~ js.latestDateAdded
      }

    private val getJobQuery: Query[Long, JobStoreRecord] =
      sql"""
             SELECT jobId, from, to, state, lastRunScheduled, lastRunCompleted, lastChange, latestDateAdded
             FROM jobs
             WHERE jobId = $int8
             """.query(int8 ~ int8 ~ int8 ~ varchar(24) ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt).gmap[JobStoreRecord]

    private val getJobsQuery: Query[Void, JobStoreRecord] =
      sql"""
         SELECT jobId, from, to, state, lastRunScheduled, lastRunCompleted, lastChange, latestDateAdded
         FROM jobs
         """.query(int8 ~ int8 ~ int8 ~ varchar(24) ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt).gmap[JobStoreRecord]

    private val getLatestJobQuery: Query[Void, JobStoreRecord] =
      sql"""
             SELECT jobId, from, to, state, lastRunScheduled, lastRunCompleted, lastChange, latestDateAdded
             FROM jobs
             ORDER BY to DESC
             LIMIT 1
             """.query(int8 ~ int8 ~ int8 ~ varchar(24) ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt).gmap[JobStoreRecord]

    private val getNextJobQuery: Query[LocalDateTime, JobStoreRecord] =
      sql"""
             SELECT jobId, from, to, state, lastRunScheduled, lastRunCompleted, lastChange, latestDateAdded, (now - lastDateChange) / 12 AS requiredTimeBetweenRuns
             FROM jobs
             WHERE state IN ('NeverRun', 'Completed') OR (state = 'Pending' AND lastRunScheduled < $timestamp)
             ORDER BY lastRunCompleted
             LIMIT 1
             """.query(int8 ~ int8 ~ int8 ~ varchar(24) ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt ~ timestamp.opt).gmap[JobStoreRecord]

    /*

    private val getMostRecentListingsCommand: Query[Long, PropertyRecord] =
      sql"""
     SELECT DISTINCT ON (listingId) recordId, listingId, propertyId, dateAdded, lastChange, price, transactionTypeId, visible, listingStatus, rentFrequency, latitude, longitude
     FROM properties
     WHERE propertyId = $int8
     ORDER BY listingId DESC, lastChange DESC
   """.query(
        int8.opt ~ int8 ~ int8 ~ timestamp ~ timestamp ~ int4.opt ~ int4.opt ~ bool.opt ~ varchar(24).opt ~ varchar(32).opt ~ float8.opt ~ float8.opt
      ).gmap[PropertyRecord]

     */

    override def put(job: CrawlerJob): F[Unit] =
      pool.use(_.prepare(insertJobCommand).use(_.execute(JobStore.jobStoreRecordFrom(job)).void))

    override def get(jobId: JobId): F[Option[CrawlerJob]] =
      pool.use(_.prepare(getJobQuery).use(_.option(jobId.value))).map(_.map(_.toCrawlerJob))

    override def jobs: fs2.Stream[F, CrawlerJob] =
      for {
        db      <- fs2.Stream.resource(pool)
        getJobs <- fs2.Stream.resource(db.prepare(getJobsQuery))
        result  <- getJobs.stream(Void, 16).map(_.toCrawlerJob)
      } yield result

    override def getLatestJob: F[Option[CrawlerJob]] =
      pool.use(_.prepare(getLatestJobQuery).use(_.option(Void))).map(_.map(_.toCrawlerJob))

    override def getNextJob: F[Option[CrawlerJob]] = {
      ???
    }
  }

}
