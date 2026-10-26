package com.darly.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Persistent sound identifiers, safe in alarm JSON and preset JSON. */
object BundledSounds {
 const val TRUMPET="darly:sound:trumpet"
 fun uri(context:Context,id:String):Uri=when(id){
  TRUMPET->Uri.parse("android.resource://${context.packageName}/${R.raw.wake_up_trumpet}")
  ""->RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
  else->Uri.parse(id)
 }
}
