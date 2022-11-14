package uk.co.thirdthing.utils

import java.time.{Instant, LocalDateTime, ZoneId}

object TimeUtils {

  private val zoneId = ZoneId.systemDefault()

  implicit class InstantOps(self: Instant) {
    def toLocalDateTime: LocalDateTime = self.atZone(zoneId).toLocalDateTime
  }

  implicit class LocalDateTimeOps(self: LocalDateTime) {
    def asInstant: Instant = self.atZone(zoneId).toInstant
  }
}
