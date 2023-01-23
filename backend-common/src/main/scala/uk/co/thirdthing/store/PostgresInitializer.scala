package uk.co.thirdthing.store

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all._
import skunk.Session
import skunk.implicits._
import uk.co.thirdthing.model.Types.ListingId

import java.time.Instant

object PostgresInitializer {

  def createPropertiesTableIfNotExisting[F[_]: Sync](pool: Resource[F, Session[F]]) = {
    val createPropertiesTable =
      sql"""CREATE TABLE IF NOT EXISTS properties(
         recordId BIGSERIAL NOT NULL PRIMARY KEY,
         listingId BIGINT NOT NULL,
         propertyId BIGINT NOT NULL,
         dateAdded TIMESTAMP NOT NULL,
         lastChange TIMESTAMP NOT NULL,
         price INTEGER,
         transactionTypeId INTEGER,
         visible BOOLEAN,
         listingStatus VARCHAR(24),
         rentFrequency VARCHAR(32),
         latitude DOUBLE PRECISION,
         longitude DOUBLE PRECISION,
         CONSTRAINT listingId_lastChange_unique UNIQUE (listingId, lastChange)
         )""".command

    val createPropertyIdIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS property_id_last_change_idx
              ON properties (propertyId, lastChange);
              """.command

    val createListingIdIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS listing_id_last_change_idx
              ON properties (listingId, lastChange);
              """.command

              pool.use {session =>

                    session.execute(createPropertiesTable) *>
      session.execute(createPropertyIdIndex) *>
      session.execute(createListingIdIndex)
                }

  }

  def createJobsTableIfNotExisting[F[_]: Sync](pool: Resource[F, Session[F]]) = {
    val createJobsTable =
      sql"""CREATE TABLE IF NOT EXISTS jobs(
         jobId BIGINT NOT NULL PRIMARY KEY,
         fromJob BIGINT NOT NULL,
         toJob BIGINT NOT NULL,
         state VARCHAR(24) NOT NULL,
         lastRunScheduled TIMESTAMP,
         lastRunCompleted TIMESTAMP,
         lastChange TIMESTAMP,
         latestDateAdded TIMESTAMP
         )""".command

    val createToJobIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS toJob_idx
              ON jobs (toJob);
              """.command

    val createLastRunCompletedIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS lastRunCompleted_idx
              ON jobs (lastRunCompleted);
              """.command

    val createLastRunScheduledIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS lastRunScheduled_idx
              ON jobs (lastRunScheduled);
              """.command

    val createStateIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS state_idx
              ON jobs (state);
              """.command

    pool.use { session =>
      session.execute(createJobsTable) *>
        session.execute(createToJobIndex) *>
        session.execute(createLastRunCompletedIndex) *>
        session.execute(createLastRunScheduledIndex) *>
        session.execute(createStateIndex)
    }

  }

}
