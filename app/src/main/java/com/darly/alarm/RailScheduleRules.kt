package com.darly.alarm

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/** Pure validation logic: no dependency on alarms, UI, network or Android runtime. */
object RailScheduleRules {
 fun stationCode(value:String):String? {
  val normalized=value.trim().lowercase(Locale.ROOT)
  return normalized.takeIf { Regex("s[0-9]{3,12}").matches(it) }
 }
 fun date(value:String):LocalDate?=try{LocalDate.parse(value)}catch(_:DateTimeParseException){null}
 fun validRoute(from:String,to:String)=stationCode(from)!=null && stationCode(to)!=null && stationCode(from)!=stationCode(to)
}
