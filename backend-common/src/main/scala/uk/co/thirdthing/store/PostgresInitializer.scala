package uk.co.thirdthing.store

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all._
import skunk.Session
import skunk.implicits._
import uk.co.thirdthing.model.Types.ListingId

import java.time.Instant

object PostgresInitializer {

  def createPostgresTablesIfNotExisting[F[_]: Sync](pool: Resource[F, Session[F]]) = {
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

    pool.use(_.execute(createPropertiesTable)) *>
      pool.use(_.execute(createPropertyIdIndex)) *>
      pool.use(_.execute(createListingIdIndex))
  }

}
