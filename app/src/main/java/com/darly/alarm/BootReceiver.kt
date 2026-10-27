package com.darly.alarm
import android.content.*
class BootReceiver: BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) {
  if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED))return
  AlarmStore.reschedule(context);TimerStore.restore(context)
 }
}
