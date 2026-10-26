package com.darly.alarm
import android.app.*
import android.content.*
import android.media.*
import android.net.Uri
import android.os.*
class RingService: Service() {
 private var player:MediaPlayer?=null
 private var id=-1
 private var started=false
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="STOP") { stopSelf(); return START_NOT_STICKY }
  if(intent?.action=="SNOOZE") {
   val pi=PendingIntent.getBroadcast(this,id xor 0x5A5A5A5A,Intent(this,AlarmReceiver::class.java).putExtra("id",id).putExtra("snoozed",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   val am=getSystemService(AlarmManager::class.java)
   if(Build.VERSION.SDK_INT<31 || am.canScheduleExactAlarms()) am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis()+5*60_000,pi),pi)
   stopSelf(); return START_NOT_STICKY
  }
  if(started) return START_NOT_STICKY
  id=intent?.getIntExtra("id",-1)?:-1
  val channel=NotificationChannel("ring","Звонок будильника",NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility=Notification.VISIBILITY_PUBLIC; setSound(null,null) }
  getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  val show=PendingIntent.getActivity(this,0,Intent(this,RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(this,"ring").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("DARLY — пора просыпаться").setContentText("Твоё утро начинается ☀").setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setContentIntent(show).setFullScreenIntent(show,true).build()
  startForeground(42,notification)
  started=true
  try { val custom=intent?.getStringExtra("sound").orEmpty(); val uri=if(custom.isNotBlank())Uri.parse(custom) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM); player=MediaPlayer().apply { setDataSource(this@RingService,uri); setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()); isLooping=true; prepare(); start() } } catch (_:Exception) { try { player?.release(); val uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM); player=MediaPlayer().apply{setDataSource(this@RingService,uri);setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());isLooping=true;prepare();start()} } catch (_:Exception) {} }
  return START_NOT_STICKY
 }
 override fun onDestroy() { player?.stop(); player?.release(); player=null; super.onDestroy() }
}
