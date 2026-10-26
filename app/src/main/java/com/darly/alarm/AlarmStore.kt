package com.darly.alarm

import android.app.*
import android.content.*
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class DAlarm(val id:Int,val hour:Int,val minute:Int,val days:Int,val sound:String,val enabled:Boolean=true,val title:String="Будильник",val vibrate:Boolean=true,val snooze:Int=5,val fade:Int=30)
object AlarmStore {
 private const val KEY="alarms"
 fun all(c:Context):List<DAlarm> = try { val arr=JSONArray(c.getSharedPreferences("darly",0).getString(KEY,"[]")); (0 until arr.length()).map { val j=arr.getJSONObject(it); DAlarm(j.getInt("id"),j.getInt("h"),j.getInt("m"),j.getInt("days"),j.optString("sound"),j.optBoolean("enabled",true),j.optString("title","Будильник"),j.optBoolean("vibrate",true),j.optInt("snooze",5),j.optInt("fade",30)) } } catch (_:Exception) { emptyList() }
 fun save(c:Context,items:List<DAlarm>) { val a=JSONArray();items.forEach { a.put(JSONObject().put("id",it.id).put("h",it.hour).put("m",it.minute).put("days",it.days).put("sound",it.sound).put("enabled",it.enabled).put("title",it.title).put("vibrate",it.vibrate).put("snooze",it.snooze).put("fade",it.fade)) };c.getSharedPreferences("darly",0).edit().putString(KEY,a.toString()).apply() }
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
 fun reschedule(c:Context)=all(c).forEach{schedule(c,it)}
 fun exactPermission(c:Context){if(Build.VERSION.SDK_INT>=31 && !c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
}
