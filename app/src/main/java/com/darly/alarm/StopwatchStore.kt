package com.darly.alarm

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray

/** Monotonic stopwatch; STOP records a split without pausing. PAUSE freezes time. */
data class StopwatchState(
 val accrued:Long=0L,
 val startedAt:Long=0L,
 val running:Boolean=false,
 val marks:List<Long> = emptyList()
){
 fun elapsed(now:Long=SystemClock.elapsedRealtime()):Long=(accrued+if(running)(now-startedAt).coerceAtLeast(0L)else 0L).coerceAtLeast(0L)
}
object StopwatchStore {
 private fun prefs(c:Context)=c.getSharedPreferences("darly_stopwatch",0)
 fun get(c:Context):StopwatchState {val p=prefs(c)
  val marks=try{val a=JSONArray(p.getString("marks","[]"));(0 until a.length()).map{a.getLong(it)}}catch(_:Exception){emptyList()}
  val stamp=p.getLong("started",0);val running=p.getBoolean("running",false)
  val now=SystemClock.elapsedRealtime()
  // Monotonic time resets after reboot; never display negative or runaway values.
  val boot=android.provider.Settings.Global.getInt(c.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)
  val savedBoot=p.getInt("boot",-1)
  val valid=stamp in 1..now && (savedBoot<0 || savedBoot==boot)
  return StopwatchState(p.getLong("accrued",0).coerceAtLeast(0),if(valid)stamp else 0L,running&&valid,marks)
 }
 private fun save(c:Context,s:StopwatchState){val marks=JSONArray();s.marks.forEach{marks.put(it)}
  val boot=android.provider.Settings.Global.getInt(c.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)
  prefs(c).edit().putInt("boot",boot).putLong("accrued",s.accrued).putLong("started",s.startedAt).putBoolean("running",s.running).putString("marks",marks.toString()).apply()
 }
 fun start(c:Context){val s=get(c);if(!s.running)save(c,s.copy(running=true,startedAt=SystemClock.elapsedRealtime()))}
 fun pause(c:Context){val s=get(c);if(s.running)save(c,s.copy(accrued=s.elapsed(),startedAt=0,running=false))}
 fun stop(c:Context){val s=get(c);if(s.running)save(c,s.copy(marks=s.marks+s.elapsed()))}
 fun reset(c:Context){save(c,StopwatchState())}
}
