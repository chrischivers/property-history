package uk.co.thirdthing.store

import cats.implicits.{catsSyntaxOptionId, toBifunctorOps}
import meteor.codec.{Decoder, Encoder}
import meteor.errors.DecoderError
import meteor.syntax._
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.model.Model.CrawlerJob.{LastChange, LastRunCompleted, LastRunScheduled}
import uk.co.thirdthing.model.Model._

import java.time.Instant

object JobStoreCodecs {

  implicit val jobIdEncoder: Encoder[JobId] = Encoder.instance(_.value.asAttributeValue)

  implicit val crawlerJobEncoder: Encoder[CrawlerJob] = Encoder.instance { job =>
    Map(
      "jobId"            -> job.jobId.value.asAttributeValue,
      "from"             -> job.from.value.asAttributeValue,
      "to"               -> job.to.value.asAttributeValue,
      "lastRunCompleted" -> job.lastRunCompleted.map(_.value).asAttributeValue,
      "lastRunScheduled" -> job.lastRunScheduled.map(_.value).asAttributeValue,
      "type"             -> "JOB".asAttributeValue,
      "state"            -> job.state.entryName.asAttributeValue,
      "lastChange"       -> job.lastChange.map(_.value).asAttributeValue
    ).asAttributeValue
  }

  implicit val crawlerJobDecoder: Decoder[CrawlerJob] = Decoder.instance { job =>
    for {
      jobId            <- job.getAs[Int]("jobId").map(JobId(_))
      from             <- job.getAs[Long]("from").map(ListingId(_))
      to               <- job.getAs[Long]("to").map(ListingId(_))
      lastRunScheduled <- job.getOpt[Instant]("lastRunScheduled")
      lastRunCompleted <- job.getOpt[Instant]("lastRunCompleted")
      lastChange       <- job.getOpt[Instant]("lastChange")
      jobState         <- job.getAs[String]("state").flatMap(JobState.withNameEither(_).leftMap(err => DecoderError("Unable to decode state", err.some)))
    } yield CrawlerJob(
      jobId,
      from,
      to,
      jobState,
      lastRunScheduled.map(LastRunScheduled(_)),
      lastRunCompleted.map(LastRunCompleted(_)),
      lastChange.map(LastChange(_))
    )
  }

}
