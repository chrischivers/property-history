package uk.co.thirdthing.pollers

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.co.thirdthing.service.{UpdateAddressDetailsService, UpdatePropertyHistoryService}
import uk.co.thirdthing.store.{JobStore, PostcodeStore}

object AddressDetailsPoller:
  def apply[F[_]: Async](postcodeStore: PostcodeStore[F], updateAddressDetailsService: UpdateAddressDetailsService[F]) =
    new PollingService[F]:

      implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      override def action: F[Unit] = Resource
        .make(postcodeStore.getAndLockNextPostcode)(_.fold(().pure[F])(pc => postcodeStore.updateAndRelease(pc)))
        .use {
          case Some(postcode) =>
            logger.info(s"Picking up 'address details' job for postcode ${postcode.value}") *>
              updateAddressDetailsService.run(postcode)
          case None =>
            logger.warn(s"No address details postcodes available to pick up. Will retry on next poll") *>
              ().pure[F]
        }
