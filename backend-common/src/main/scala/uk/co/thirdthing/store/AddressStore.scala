package uk.co.thirdthing.store

import cats.data.NonEmptyList
import cats.effect.Resource
import cats.effect.kernel.{Clock, Sync}
import cats.syntax.all.*
import io.circe.Json
import io.circe.parser.*
import io.circe.syntax.*
import skunk.*
import skunk.codec.all.*
import skunk.circe.codec.all.*
import skunk.implicits.*
import uk.co.thirdthing.model.Types.ListingSnapshot.ListingSnapshotId
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.model.Types.Transaction as PropertyTransaction
import uk.co.thirdthing.store.AddressStore.AddressRecord
import uk.co.thirdthing.utils.TimeUtils.*

import java.time.{Instant, LocalDateTime, ZoneId}

trait AddressStore[F[_]]:
  def putAddresses(addressDetails: NonEmptyList[AddressDetails]): F[Unit]
  def getAddressesFor(propertyId: PropertyId): fs2.Stream[F, AddressDetails]

object AddressStore:
  private[store] final case class AddressRecord(
    address: String,
    propertyId: Option[Long],
    postcode: String,
    transactions: Json,
    updated: LocalDateTime
  )

object PostgresAddressStore:

  def apply[F[_]: Sync: Clock](pool: Resource[F, Session[F]]) = new AddressStore[F]:

    /*
    def insertExactly(ps: List[(String, Short)]): Command[ps.type] = {
      val enc = (varchar ~ int2).values.list(ps)
      sql"INSERT INTO pets VALUES $enc".command
    }
     */
    private def insertAddressRecordCommands(ars: List[AddressRecord]): Command[ars.type] =
      val enc = (varchar(300) ~ int8.opt ~ varchar(10) ~ json ~ timestamp).gcontramap[AddressRecord].values.list(ars)
      sql"""
             INSERT INTO addresses(address, propertyId, postcode, transactions, updated) VALUES $enc
               ON CONFLICT(address) DO UPDATE SET propertyId = excluded.propertyId, postcode = excluded.postcode, transactions = excluded.transactions, updated = excluded.updated
         """.command

    private val getAddressByPropertyId: Query[Long, AddressRecord] =
      sql"""
           SELECT address, propertyId, postcode, transactions, updated
           FROM addresses
           WHERE propertyId = $int8
     """
        .query(
          varchar(300) ~ int8.opt ~ varchar(10) ~ json ~ timestamp
        )
        .gmap[AddressRecord]

    override def putAddresses(addressDetails: NonEmptyList[AddressDetails]): F[Unit] =
      Clock[F].realTimeInstant.flatMap { now =>
        val addressRecords = addressDetails.map { details =>
          AddressRecord(
            details.address.value,
            details.propertyId.map(_.value),
            details.postcode.value,
            transactions = details.transactions.asJson,
            now.toLocalDateTime
          )
        }.toList
        pool.use(_.prepare(insertAddressRecordCommands(addressRecords)).flatMap(_.execute(addressRecords).void))
      }

    override def getAddressesFor(propertyId: PropertyId): fs2.Stream[F, AddressDetails] =
      fs2.Stream
        .eval(pool.use(_.prepare(getAddressByPropertyId)))
        .flatMap(_.stream(propertyId.value, 100))
        .evalMap(addressDetailsFrom)

    private def addressDetailsFrom(addressRecord: AddressRecord): F[AddressDetails] =
      Sync[F]
        .fromEither(addressRecord.transactions.as[List[PropertyTransaction]]).map { transactions =>
        AddressDetails(
          FullAddress(addressRecord.address),
          Postcode(addressRecord.postcode),
          addressRecord.propertyId.map(PropertyId(_)),
          transactions
        )
      }
