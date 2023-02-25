package uk.co.thirdthing.service

import cats.effect.kernel.{Async, Clock}
import cats.syntax.all.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.clients.RightmovePostcodeSearchHtmlClient
import uk.co.thirdthing.clients.RightmovePostcodeSearchHtmlClient.{
  RightmovePostcodeSearchResult,
  RightmovePostcodeSearchTransaction
}
import uk.co.thirdthing.metrics.MetricsRecorder
import uk.co.thirdthing.model.Model.CrawlerJob.LastRunCompleted
import uk.co.thirdthing.model.Model.*
import uk.co.thirdthing.model.Types.*
import uk.co.thirdthing.service.RetrievalService.RetrievalResult
import uk.co.thirdthing.store.{AddressStore, JobStore, PropertyStore}

trait UpdateAddressDetailsService[F[_]]:
  def run(postcode: Postcode): F[Unit]

object UpdateAddressDetailsService:

  def apply[F[_]: Async: Clock](
    addressStore: AddressStore[F],
    propertyStore: PropertyStore[F],
    rightmovePostcodeSearchHtmlClient: RightmovePostcodeSearchHtmlClient[F],
    metricsRecorder: MetricsRecorder[F]
  ) =
    new UpdateAddressDetailsService[F]:
      override def run(postcode: Postcode): F[Unit] =
        withDurationMetricReporting(postcode) {
          rightmovePostcodeSearchHtmlClient.scrapeDetails(postcode).flatMap { results =>
            deduplicateByAddress(results.toList).toNel
              .fold(logger.warn(s"No properties retrieved for postcode ${postcode.value}"))(
                _.traverse(addressDetailsFrom).flatMap(addressStore.putAddresses)
              )
          }
        }

      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      private def deduplicateByAddress(
        results: List[RightmovePostcodeSearchResult]
      ): List[RightmovePostcodeSearchResult] =
        results
          .groupBy(_.fullAddress)
          .values
          .map { results =>
            val primaryResult        = results.head
            val combinedTransactions = primaryResult.transactions ++ results.tail.flatMap(_.transactions)
            primaryResult.copy(transactions = combinedTransactions)
          }
          .toList

      private def addressDetailsFrom(result: RightmovePostcodeSearchResult): F[AddressDetails] =
        result.listingId.flatTraverse(propertyStore.propertyIdFor).map { propertyIdOpt =>
          AddressDetails(
            result.fullAddress,
            result.postcode,
            propertyIdOpt,
            result.transactions.flatMap(transactionDetailsFrom)
          )
        }

      private def withDurationMetricReporting[T](postcode: Postcode)(f: F[T]): F[T] =
        Clock[F].realTime.flatMap { startTime =>
          f.flatMap(r =>
            Clock[F].realTime.flatMap { endTime =>
              val duration = endTime - startTime
              logger.info(s"${postcode.value} finished in ${duration.toMinutes} minutes") *>
                metricsRecorder.recordJobDuration("property-history-postcode-crawler")(duration).as(r)
            }
          )
        }

      private def transactionDetailsFrom(result: RightmovePostcodeSearchTransaction): Option[Transaction] =
        for
          price <- result.price
          date  <- result.date
        yield Transaction(price, date, result.tenure)
