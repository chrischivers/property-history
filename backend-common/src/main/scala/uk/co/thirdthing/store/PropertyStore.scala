package uk.co.thirdthing.store

import cats.effect.Resource
import cats.effect.kernel.Sync
import cats.syntax.all.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.store.PropertyStore.PropertyRecord
import uk.co.thirdthing.utils.TimeUtils.*

import java.time.{LocalDateTime, ZoneId}

trait PropertyStore[F[_]]:
  def propertyIdFor(listingId: ListingId): F[Option[PropertyId]]
  def latestListingsFor(propertyId: PropertyId): fs2.Stream[F, ListingSnapshot]
  def putListingSnapshot(listingSnapshot: ListingSnapshot): F[Unit]
  def getMostRecentListing(listingId: ListingId): F[Option[ListingSnapshot]]

object PropertyStore:
  private[store] final case class PropertyRecord(
    recordId: Option[Long],
    listingId: Long,
    propertyId: Long,
    dateAdded: LocalDateTime,
    lastChange: LocalDateTime,
    price: Option[Int],
    transactionTypeId: Option[Int],
    visible: Option[Boolean],
    status: Option[String],
    rentFrequency: Option[String],
    latitude: Option[Double],
    longitude: Option[Double],
    thumbnailUrl: Option[String]
  ):
    // TODO this is not safe
    def toListingSnapshot: ListingSnapshot =
      ListingSnapshot(
        ListingId(this.listingId),
        LastChange(this.lastChange.asInstant),
        PropertyId(this.propertyId),
        DateAdded(this.dateAdded.asInstant),
        PropertyDetails(
          this.price.map(Price(_)),
          this.transactionTypeId.map(TransactionType.withValue),
          this.visible,
          this.status.map(ListingStatus.withValue),
          this.rentFrequency,
          this.latitude,
          this.longitude,
          this.thumbnailUrl.map(ThumbnailUrl(_))
        ),
        this.recordId.map(ListingSnapshotId(_))
      )

object PostgresPropertyStore:

  def apply[F[_]: Sync](pool: Resource[F, Session[F]]) = new PropertyStore[F]:

    private val insertPropertyRecordCommand: Command[PropertyRecord] =
      sql"""
             INSERT INTO properties(listingId, propertyId, dateAdded, lastChange, price, transactionTypeId, visible, listingStatus, rentFrequency, latitude, longitude, thumbnailUrl) VALUES
             ($int8, $int8, $timestamp, $timestamp, ${int4.opt}, ${int4.opt}, ${bool.opt}, ${varchar(
          24
        ).opt}, ${varchar(
          32
        ).opt}, ${float8.opt}, ${float8.opt}, ${varchar(256).opt})
         """.command.contramap { (pr: PropertyRecord) =>
        pr.listingId ~ pr.propertyId ~ pr.dateAdded ~ pr.lastChange ~ pr.price ~ pr.transactionTypeId ~ pr.visible ~ pr.status ~ pr.rentFrequency ~ pr.latitude ~ pr.longitude ~ pr.thumbnailUrl
      }

    private val getMostRecentListingCommand: Query[Long, PropertyRecord] =
      sql"""
           SELECT recordId, listingId, propertyId, dateAdded, lastChange, price, transactionTypeId, visible, listingStatus, rentFrequency, latitude, longitude,  thumbnailUrl
           FROM properties
           WHERE listingId = $int8
           ORDER BY lastChange DESC
           LIMIT 1
         """
        .query(
          int8.opt ~ int8 ~ int8 ~ timestamp ~ timestamp ~ int4.opt ~ int4.opt ~ bool.opt ~ varchar(24).opt ~ varchar(
            32
          ).opt ~ float8.opt ~ float8.opt ~ varchar(256).opt
        )
        .gmap[PropertyRecord]

    private val getPropertyIdCommand: Query[Long, Long] =
      sql"""
           SELECT propertyId
           FROM properties
           WHERE listingId = $int8
           ORDER BY lastChange DESC
           LIMIT 1
         """
        .query(int8)

    private val getMostRecentListingsCommand: Query[Long, PropertyRecord] =
      sql"""
     SELECT DISTINCT ON (listingId) recordId, listingId, propertyId, dateAdded, lastChange, price, transactionTypeId, visible, listingStatus, rentFrequency, latitude, longitude, thumbnailUrl
     FROM properties
     WHERE propertyId = $int8
     ORDER BY listingId DESC, lastChange DESC
   """.query(
        int8.opt ~ int8 ~ int8 ~ timestamp ~ timestamp ~ int4.opt ~ int4.opt ~ bool.opt ~ varchar(24).opt ~ varchar(
          32
        ).opt ~ float8.opt ~ float8.opt ~ varchar(256).opt
      ).gmap[PropertyRecord]

    override def propertyIdFor(listingId: ListingId): F[Option[PropertyId]] =
      pool.use(_.prepare(getPropertyIdCommand).use(_.option(listingId.value).map(_.map(PropertyId(_)))))

    override def latestListingsFor(propertyId: PropertyId): fs2.Stream[F, ListingSnapshot] =
      for
        db               <- fs2.Stream.resource(pool)
        getLatestListing <- fs2.Stream.resource(db.prepare(getMostRecentListingsCommand))
        result           <- getLatestListing.stream(propertyId.value, 16).map(_.toListingSnapshot)
      yield result

    override def putListingSnapshot(listingSnapshot: ListingSnapshot): F[Unit] =
      import listingSnapshot.*
      val propertyRecord = PropertyRecord(
        recordId = None,
        listingId.value,
        propertyId.value,
        dateAdded.value.toLocalDateTime,
        lastChange.value.toLocalDateTime,
        details.price.map(_.value),
        details.transactionTypeId.map(_.value),
        details.visible,
        details.status.map(_.value),
        details.rentFrequency,
        details.latitude,
        details.longitude,
        details.thumbnailUrl.map(_.value)
      )
      pool.use(_.prepare(insertPropertyRecordCommand).use(_.execute(propertyRecord).void))

    override def getMostRecentListing(listingId: ListingId): F[Option[ListingSnapshot]] = pool.use { session =>
      session
        .prepare(getMostRecentListingCommand)
        .use(_.option(listingId.value))
        .map(_.map(_.toListingSnapshot))
    }
