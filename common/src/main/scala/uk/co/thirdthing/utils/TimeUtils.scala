package uk.co.thirdthing.utils

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}

object TimeUtils {

  private val zoneId = ZoneId.of("UTC")

  implicit class InstantOps(self: Instant) {
    def toLocalDateTime: LocalDateTime = self.atZone(zoneId).toLocalDateTime
    def toLocalDate: LocalDate = self.atZone(zoneId).toLocalDate
  }

  implicit class LocalDateTimeOps(self: LocalDateTime) {
    def asInstant: Instant = self.atZone(zoneId).toInstant
  }
}
