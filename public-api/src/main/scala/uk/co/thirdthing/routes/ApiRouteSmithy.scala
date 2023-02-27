package uk.co.thirdthing.routes

import cats.effect.Sync
import cats.implicits.toFlatMapOps
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.{PropertyLookupService, ThumbnailService}
import cats.effect.IO
import smithy4s.hello.*

import java.time.ZoneId

object ApiRouteSmithy:

  def apply[F[_]: Sync](lookupService: PropertyLookupService[F]) = new PublicApiService[F]:
    override def getHistoryOperation(input: GetHistoryRequest): F[GetHistoryResponse] =
      lookupService.detailsFor(ListingId(input.listingId)).flatMap {
        case None    => Sync[F].raiseError(ListingNotFound())
        case Some(l) => Sync[F].pure(toResponse(l))
      }

  private def toResponse(details: PropertyLookupDetails) =
    GetHistoryResponse(
      details.listingRecords.map(toListingRecord),
      details.transactions.map(toTransactionRecord),
      details.fullAddress.map(_.value),
      details.postcode.map(_.value)
    )

  private def toListingRecord(snapshot: ListingSnapshot): ListingRecord =
    ListingRecord(
      snapshot.listingId.value,
      smithy4s.Timestamp.fromInstant(snapshot.lastChange.value),
      snapshot.propertyId.value,
      smithy4s.Timestamp.fromInstant(snapshot.dateAdded.value),
      toHistoryRecordDetails(snapshot.details),
      snapshot.listingSnapshotId.map(_.value)
    )

  private def toTransactionRecord(transaction: Transaction): TransactionRecord =
    TransactionRecord(
      transaction.price.value,
      transaction.date.toString,
      transaction.tenure.map(_.value)
    )

  private def toHistoryRecordDetails(details: PropertyDetails): HistoryRecordDetails =
    HistoryRecordDetails(
      details.price.map(_.value),
      details.transactionTypeId.map(_.value),
      details.visible,
      details.status.map(_.value),
      details.rentFrequency,
      details.latitude,
      details.longitude,
      details.thumbnailUrl.map(_.value)
    )
