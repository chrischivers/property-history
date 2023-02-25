package uk.co.thirdthing.utils

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}

object TimeUtils:

  private val zoneId = ZoneId.of("UTC")

  extension (self: Instant) def toLocalDateTime: LocalDateTime = self.atZone(zoneId).toLocalDateTime
  extension (self: Instant) def toLocalDate: LocalDate         = self.atZone(zoneId).toLocalDate
  extension (self: LocalDateTime) def asInstant: Instant = self.atZone(zoneId).toInstant
