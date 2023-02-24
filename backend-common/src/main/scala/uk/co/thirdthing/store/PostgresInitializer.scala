package uk.co.thirdthing.store

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import skunk.Session
import skunk.implicits.*
import uk.co.thirdthing.model.Types.ListingId

import java.time.Instant

object PostgresInitializer:

  def initializeAll[F[_]: Sync](pool: Resource[F, Session[F]]) =
    createPropertiesTableIfNotExisting(pool) *>
      createJobsTableIfNotExisting(pool) *>
      createAddressesTableIfNotExisting(pool) *>
      createPostcodesTableIfNotExisting(pool)

  def createPropertiesTableIfNotExisting[F[_]: Sync](pool: Resource[F, Session[F]]) =
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
         thumbnailUrl VARCHAR(256),
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

    pool.use { session =>
      session.execute(createPropertiesTable) *>
        session.execute(createPropertyIdIndex) *>
        session.execute(createListingIdIndex)
    }

  def createJobsTableIfNotExisting[F[_]: Sync](pool: Resource[F, Session[F]]) =
    val createJobsTable =
      sql"""CREATE TABLE IF NOT EXISTS jobs(
         jobId BIGINT NOT NULL PRIMARY KEY,
         fromJob BIGINT NOT NULL,
         toJob BIGINT NOT NULL,
         state VARCHAR(24) NOT NULL,
         lastRunStarted TIMESTAMP,
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

    val createLastRunStartedIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS lastRunStarted_idx
              ON jobs (lastRunStarted);
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
        session.execute(createLastRunStartedIndex) *>
        session.execute(createStateIndex)
    }

  def createAddressesTableIfNotExisting[F[_]: Sync](pool: Resource[F, Session[F]]) =
    val createAddressesTable =
      sql"""CREATE TABLE IF NOT EXISTS addresses(
         address VARCHAR(300) PRIMARY KEY,
         propertyId BIGINT,
         postcode VARCHAR(10) NOT NULL,
         transactions JSON,
         updated TIMESTAMP NOT NULL,
         UNIQUE(propertyId)
         )""".command

    val createPropertyIdIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS property_id_idx
              ON addresses (propertyId);
              """.command

    val createPostcodeIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS postcode_idx
              ON addresses (postcode);
              """.command

    pool.use { session =>
      session.execute(createAddressesTable) *>
        session.execute(createPropertyIdIndex) *>
        session.execute(createPostcodeIndex)
    }

  def createPostcodesTableIfNotExisting[F[_] : Sync](pool: Resource[F, Session[F]]) =
    val createPostcodesTable =
      sql"""CREATE TABLE IF NOT EXISTS postcodes(
         postcode VARCHAR(10) PRIMARY KEY,
         inuse BOOLEAN NOT NULL,
         lockedat TIMESTAMP,
         lastscraped TIMESTAMP
         )""".command

    val createInUseIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS inuse_idx
              ON postcodes (inuse);
              """.command

    val createlockedAtIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS lockedat_idx
              ON postcodes (lockedat);
              """.command


    val createLastScrapedIndex =
      sql"""
             CREATE INDEX IF NOT EXISTS lastscraped_idx
              ON postcodes (lastscraped);
              """.command


    pool.use { session =>
      session.execute(createPostcodesTable) *>
        session.execute(createInUseIndex) *>
        session.execute(createlockedAtIndex) *>
        session.execute(createLastScrapedIndex)
    }