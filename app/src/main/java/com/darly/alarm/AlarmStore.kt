package com.darly.alarm

import android.app.*
import android.content.*
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class DAlarm(val id:Int,val hour:Int,val minute:Int,val days:Int,val sound:String,val enabled:Boolean=true,val title:String="Будильник",val vibrate:Boolean=true,val snooze:Int=5,val fade:Int=30,val groupId:Int=0,val seriesIndex:Int=0)
/** A planned alarm may be regular or a one-time snoozed occurrence. */
data class UpcomingAlarm(val alarm:DAlarm,val whenAt:Long,val snoozed:Boolean)

object AlarmStore {
 private const val KEY="alarms"
 const val ACTION_CHANGED="com.darly.alarm.ALARMS_CHANGED"
 fun notifyChanged(c:Context){c.sendBroadcast(Intent(ACTION_CHANGED).setPackage(c.packageName))}
 fun all(c:Context):List<DAlarm> = try { val arr=JSONArray(c.getSharedPreferences("darly",0).getString(KEY,"[]")); (0 until arr.length()).map { val j=arr.getJSONObject(it); DAlarm(j.getInt("id"),j.getInt("h"),j.getInt("m"),j.getInt("days"),j.optString("sound"),j.optBoolean("enabled",true),j.optString("title","Будильник"),j.optBoolean("vibrate",true),j.optInt("snooze",5),j.optInt("fade",30),j.optInt("groupId",0),j.optInt("seriesIndex",0)) } } catch (_:Exception) { emptyList() }
 fun save(c:Context,items:List<DAlarm>) { val a=JSONArray();items.forEach { a.put(JSONObject().put("id",it.id).put("h",it.hour).put("m",it.minute).put("days",it.days).put("sound",it.sound).put("enabled",it.enabled).put("title",it.title).put("vibrate",it.vibrate).put("snooze",it.snooze).put("fade",it.fade).put("groupId",it.groupId).put("seriesIndex",it.seriesIndex)) };c.getSharedPreferences("darly",0).edit().putString(KEY,a.toString()).apply();notifyChanged(c);NextAlarmNotifier.sync(c) }
 fun schedule(c:Context,a:DAlarm):Boolean {
  val am=c.getSystemService(AlarmManager::class.java)
  val pi=PendingIntent.getBroadcast(c,a.id,Intent(c,AlarmReceiver::class.java).putExtra("id",a.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  am.cancel(pi);if(!a.enabled)return true
  val cal=Calendar.getInstance();val now=cal.timeInMillis
  cal.set(Calendar.HOUR_OF_DAY,a.hour);cal.set(Calendar.MINUTE,a.minute);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0)
  repeat(8) { if(cal.timeInMillis>now && (a.days==0 || a.days and (1 shl ((cal.get(Calendar.DAY_OF_WEEK)+5)%7))!=0)) { if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms())return false
   try { am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis,PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)),pi) } catch (_:SecurityException) {return false};return true };cal.add(Calendar.DAY_OF_YEAR,1) }
  return false
 }
 /** Cancel both normal and snoozed PendingIntents before deleting an alarm. */
 fun cancel(c:Context,a:DAlarm){
  val disabled=a.copy(enabled=false)
  schedule(c,disabled)
  cancelSnooze(c,a.id)
 }
 /** Actual next occurrence, not numeric HH:mm sort. */
 fun nextTime(a:DAlarm,now:Long=System.currentTimeMillis()):Long? {
  if(!a.enabled)return null
  val cal=Calendar.getInstance().apply{timeInMillis=now;set(Calendar.HOUR_OF_DAY,a.hour);set(Calendar.MINUTE,a.minute);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
  repeat(8){
   if(cal.timeInMillis>now && (a.days==0 || a.days and (1 shl ((cal.get(Calendar.DAY_OF_WEEK)+5)%7))!=0))return cal.timeInMillis
   cal.add(Calendar.DAY_OF_YEAR,1)
  }
  return null
 }
 /** Picks the actual earliest ring, including snoozes (which can outlive a disabled one-shot). */
 fun nextUpcoming(c:Context,now:Long=System.currentTimeMillis()):UpcomingAlarm? {
  val alarms=all(c)
  val regular=alarms.mapNotNull{a->nextTime(a,now)?.let{UpcomingAlarm(a,it,false)}}
  val snoozed=alarms.mapNotNull{a->snoozeUntil(c,a.id).takeIf{it>now}?.let{UpcomingAlarm(a,it,true)}}
  return (regular+snoozed).minByOrNull{it.whenAt}
 }
 private fun prefs(c:Context)=c.getSharedPreferences("darly_snooze",0)
 fun snoozeUntil(c:Context,id:Int):Long=prefs(c).getLong(id.toString(),0L)
 fun setSnooze(c:Context,id:Int,until:Long){prefs(c).edit().putLong(id.toString(),until).apply();notifyChanged(c);NextAlarmNotifier.sync(c)}
 fun clearSnooze(c:Context,id:Int){setSnooze(c,id,0L)}
 fun snoozed(c:Context):List<Pair<DAlarm,Long>> = all(c).mapNotNull { a -> val until=snoozeUntil(c,a.id);if(until>System.currentTimeMillis()) a to until else null }
 fun cancelSnooze(c:Context,id:Int){
  val pi=PendingIntent.getBroadcast(c,id xor 0x5A5A5A5A,Intent(c,AlarmReceiver::class.java).putExtra("id",id).putExtra("snoozed",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  c.getSystemService(AlarmManager::class.java).cancel(pi);clearSnooze(c,id)
 }
 fun reschedule(c:Context){
  all(c).forEach{schedule(c,it)}
  NextAlarmNotifier.sync(c)
  snoozed(c).forEach{(a,until)->
   val pi=PendingIntent.getBroadcast(c,a.id xor 0x5A5A5A5A,Intent(c,AlarmReceiver::class.java).putExtra("id",a.id).putExtra("snoozed",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   try{c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(until,PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)),pi)}catch(_:Exception){}
  }
 }
 fun exactPermission(c:Context){if(Build.VERSION.SDK_INT>=31 && !c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
}
