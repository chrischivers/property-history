package uk.co.thirdthing.store

import cats.implicits.{catsSyntaxOptionId, toBifunctorOps}
import meteor.codec.{Decoder, Encoder}
import meteor.errors.DecoderError
import meteor.syntax._
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import uk.co.thirdthing.model.Model._

import java.time.Instant

object JobStoreCodecs {

  implicit val jobIdEncoder: Encoder[JobId] = Encoder.instance(_.value.asAttributeValue)

  implicit val listingIdEncoder: Encoder[ListingId] = Encoder.instance(_.value.asAttributeValue)

  implicit val crawlerJobEncoder: Encoder[CrawlerJob] = Encoder.instance { job =>
    Map(
      "jobId"   -> job.jobId.value.asAttributeValue,
      "from"    -> job.from.value.asAttributeValue,
      "to"      -> job.to.value.asAttributeValue,
      "lastRun" -> job.lastRun.asAttributeValue,
      "type"    -> "JOB".asAttributeValue
    ).asAttributeValue
  }

  implicit val crawlerJobDecoder: Decoder[CrawlerJob] = Decoder.instance { job =>
    for {
      jobId    <- job.getAs[Int]("jobId").map(JobId(_))
      from     <- job.getAs[Long]("from").map(ListingId(_))
      to       <- job.getAs[Long]("to").map(ListingId(_))
      lastRun  <- job.getOpt[Instant]("lastRun")
      jobState <- job.getAs[String]("state").flatMap(JobState.withNameEither(_).leftMap(err => DecoderError("Unable to decode state", err.some)))
    } yield CrawlerJob(jobId, from, to, lastRun, jobState)
  }

}
