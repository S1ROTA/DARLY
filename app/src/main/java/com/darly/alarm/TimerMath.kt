package com.darly.alarm

/** Pure time arithmetic, suitable for JVM unit tests (no Android dependency). */
object TimerMath {
 fun remaining(endAtMs:Long,nowMs:Long):Long=(endAtMs-nowMs).coerceAtLeast(0L)
 fun format(ms:Long,centis:Boolean=false):String {
  val t=ms.coerceAtLeast(0L);val seconds=t/1000L
  val base="%02d:%02d:%02d".format(seconds/3600L,(seconds/60L)%60L,seconds%60L)
  return if(centis)"$base.%02d".format((t%1000L)/10L) else base
 }
 fun split(mark:Long,previous:Long):Long=(mark-previous).coerceAtLeast(0L)
}
