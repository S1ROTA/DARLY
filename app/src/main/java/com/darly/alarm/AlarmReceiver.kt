package com.darly.alarm
import android.content.*
import android.os.Build

class AlarmReceiver: BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) {
  val id=intent.getIntExtra("id",-1)
  val a=AlarmStore.all(context).firstOrNull{it.id==id}?:return
  val snoozed=intent.getBooleanExtra("snoozed",false)
  if(snoozed) AlarmStore.clearSnooze(context,id)
  if(!snoozed) {
   if(a.days==0) AlarmStore.save(context,AlarmStore.all(context).map{if(it.id==id)it.copy(enabled=false)else it})
   else AlarmStore.schedule(context,a)
  }
  val service=Intent(context,RingService::class.java).putExtra("id",id).putExtra("sound",a.sound)
  try { if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service) else context.startService(service) } catch (_:Exception) {}
 }
}
