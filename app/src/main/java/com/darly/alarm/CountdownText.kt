package com.darly.alarm

/** Shared, side-effect-free countdown labels for alarm cards and notifications.
 * Round partial minutes UP so "через 0 мин" is never displayed before a bell. */
object CountdownText {
 fun until(targetMillis:Long,nowMillis:Long=System.currentTimeMillis()):String {
  val difference=(targetMillis-nowMillis).coerceAtLeast(0L)
  if(difference==0L)return "Сейчас"
  if(difference<60000L){val seconds=(difference+999L)/1000L;return "Через $seconds сек"}
  val minutes=(difference+59999L)/60000L
  val days=minutes/1440L
  val hours=(minutes%1440L)/60L
  val rest=minutes%60L
  val text=buildList{
   if(days>0)add("$days д")
   if(hours>0)add("$hours ч")
   if(rest>0 || isEmpty())add("$rest мин")
  }.joinToString(" ")
  return "Через $text"
 }
}
