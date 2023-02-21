package uk.co.thirdthing.routes

import cats.effect.Sync
import cats.implicits.toFlatMapOps
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.{HistoryService, ThumbnailService}
import cats.effect.IO
import smithy4s.hello.*

object ApiRouteSmithy:

  def apply[F[_]: Sync](historyService: HistoryService[F]) = new PublicApiService[F]:
    override def getHistoryOperation(input: GetHistoryRequest): F[GetHistoryResponse] =
      historyService.historyFor(ListingId(input.listingId)).compile.toList.flatMap {
        case Nil => Sync[F].raiseError(ListingNotFound())
        case l   => Sync[F].pure(GetHistoryResponse(l.map(toHistoryRecord)))
      }

  private def toHistoryRecord(snapshot: ListingSnapshot): HistoryRecord =
    HistoryRecord(
      snapshot.listingId.value,
      smithy4s.Timestamp.fromInstant(snapshot.lastChange.value),
      snapshot.propertyId.value,
      smithy4s.Timestamp.fromInstant(snapshot.dateAdded.value),
      toHistoryRecordDetails(snapshot.details),
      snapshot.listingSnapshotId.map(_.value)
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
