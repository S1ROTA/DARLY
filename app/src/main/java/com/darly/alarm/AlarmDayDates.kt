package com.darly.alarm

import java.time.LocalDate
import java.time.LocalDateTime

object AlarmDayDates {
 fun next(weekday:Int,minutes:Int,now:LocalDateTime=LocalDateTime.now()):LocalDate {
  require(weekday in 0..6 && minutes in 0..1439)
  val offset=(weekday-(now.dayOfWeek.value-1)+7)%7
  var date=now.toLocalDate().plusDays(offset.toLong())
  if(!date.atStartOfDay().plusMinutes(minutes.toLong()).isAfter(now))date=date.plusWeeks(1)
  return date
 }
}
