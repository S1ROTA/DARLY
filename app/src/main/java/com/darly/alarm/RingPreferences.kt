package com.darly.alarm

import android.content.Context

object RingPreferences {
 const val MOVING_SNOOZE="moving_snooze"
 const val POWER_SNOOZE="power_snooze"
 fun moving(c:Context)=c.getSharedPreferences("darly",0).getBoolean(MOVING_SNOOZE,true)
 fun power(c:Context)=c.getSharedPreferences("darly",0).getBoolean(POWER_SNOOZE,false)
}
