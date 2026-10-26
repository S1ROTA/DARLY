package com.darly.alarm
import android.content.*
class BootReceiver: BroadcastReceiver() { override fun onReceive(context:Context,intent:Intent) { AlarmStore.reschedule(context);TimerStore.restore(context) } }
