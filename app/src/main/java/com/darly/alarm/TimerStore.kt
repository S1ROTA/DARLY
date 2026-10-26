package com.darly.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Persisted, independent countdowns. Android alarms run even when the activity is closed. */
data class DTimer(
 val id:Int,
 val title:String,
 val durationMs:Long,
 val remainingMs:Long,
 val endAtMs:Long=0L,
 val running:Boolean=false
) {
 fun remaining(now:Long=System.currentTimeMillis()):Long =
  if(running) TimerMath.remaining(endAtMs,now) else remainingMs.coerceAtLeast(0L)
}

object TimerStore {
 private const val KEY="timers"
 private fun prefs(c:Context)=c.getSharedPreferences("darly_timers",Context.MODE_PRIVATE)
 fun all(c:Context):List<DTimer> = try {
  val array=JSONArray(prefs(c).getString(KEY,"[]"))
  (0 until array.length()).map{val j=array.getJSONObject(it)
   DTimer(j.getInt("id"),j.optString("title","Таймер"),j.getLong("duration"),j.getLong("remaining"),j.optLong("end",0L),j.optBoolean("running",false))}
 }catch(_:Exception){emptyList()}
 private fun save(c:Context,list:List<DTimer>){
  val a=JSONArray();list.forEach{a.put(JSONObject().put("id",it.id).put("title",it.title).put("duration",it.durationMs).put("remaining",it.remainingMs).put("end",it.endAtMs).put("running",it.running))}
  prefs(c).edit().putString(KEY,a.toString()).apply()
  AlarmStore.notifyChanged(c)
 }
 private fun pending(c:Context,id:Int)=PendingIntent.getBroadcast(c,id,Intent(c,TimerReceiver::class.java).putExtra("timer_id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun cancelAlarm(c:Context,id:Int)=c.getSystemService(AlarmManager::class.java).cancel(pending(c,id))
 /** Returns false if the Android exact-alarm permission is absent. */
 private fun schedule(c:Context,t:DTimer):Boolean {
  val manager=c.getSystemService(AlarmManager::class.java)
  if(Build.VERSION.SDK_INT>=31&&!manager.canScheduleExactAlarms())return false
  return try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.endAtMs,pending(c,t.id));true }
  catch(_:SecurityException){false}
 }
 fun create(c:Context,title:String,durationMs:Long):Boolean {
  if(durationMs<=0L)return false
  val next=(all(c).maxOfOrNull{it.id}?:10000)+1
  val duration=durationMs.coerceAtMost(7*24*3600000L)
  val t=DTimer(next,title.trim().ifBlank{"Таймер $next"},duration,duration)
  save(c,all(c)+t)
  return start(c,next)
 }
 fun start(c:Context,id:Int):Boolean {
  val items=all(c);val old=items.firstOrNull{it.id==id}?:return false
  val ms=old.remaining().takeIf{it>0}?:old.durationMs
  val updated=old.copy(remainingMs=ms,endAtMs=System.currentTimeMillis()+ms,running=true)
  if(!schedule(c,updated))return false
  save(c,items.map{if(it.id==id)updated else it});return true
 }
 fun pause(c:Context,id:Int){val list=all(c);val t=list.firstOrNull{it.id==id}?:return
  cancelAlarm(c,id);save(c,list.map{if(it.id==id)t.copy(remainingMs=t.remaining(),endAtMs=0L,running=false) else it})
 }
 fun reset(c:Context,id:Int){val list=all(c);val t=list.firstOrNull{it.id==id}?:return
  cancelAlarm(c,id);save(c,list.map{if(it.id==id)t.copy(remainingMs=t.durationMs,endAtMs=0L,running=false)else it})
 }
 fun addTime(c:Context,id:Int,extraMs:Long):Boolean {
  val list=all(c);val t=list.firstOrNull{it.id==id}?:return false
  val add=extraMs.coerceAtLeast(0L)
  if(t.running){
   val u=t.copy(endAtMs=t.endAtMs+add)
   if(!schedule(c,u))return false
   save(c,list.map{if(it.id==id)u else it})
  }else save(c,list.map{if(it.id==id)t.copy(remainingMs=t.remainingMs+add)else it})
  return true
 }
 fun delete(c:Context,id:Int){cancelAlarm(c,id);save(c,all(c).filterNot{it.id==id})}
 fun complete(c:Context,id:Int){val list=all(c);val t=list.firstOrNull{it.id==id}?:return
  if(!t.running || t.endAtMs>System.currentTimeMillis()+1000L)return
  save(c,list.map{if(it.id==id)t.copy(remainingMs=0,endAtMs=0,running=false)else it})
  if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
  val manager=c.getSystemService(NotificationManager::class.java)
  val channel=NotificationChannel("timer_complete_v1","Завершение таймера",NotificationManager.IMPORTANCE_HIGH)
  manager.createNotificationChannel(channel)
  val open=PendingIntent.getActivity(c,id,Intent(c,MainActivity::class.java).putExtra("open_tools","timers").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(c,"timer_complete_v1").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
   .setContentTitle("DARLY · Таймер завершён").setContentText(t.title)
   .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).build()
  try{manager.notify(50000+id,notification)}catch(_:SecurityException){}
 }
 fun restore(c:Context){all(c).filter{it.running}.forEach{t->if(t.endAtMs<=System.currentTimeMillis())complete(c,t.id) else schedule(c,t)}}
}

class TimerReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){TimerStore.complete(context,intent.getIntExtra("timer_id",-1))}
}
