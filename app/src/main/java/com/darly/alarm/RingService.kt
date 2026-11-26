package com.darly.alarm

import android.app.*
import android.content.*
import android.media.*
import android.net.Uri
import android.os.*

class RingService:Service(){
 private var player:MediaPlayer?=null
 private var vibrator:Vibrator?=null
 private var id=-1
 private var alarm:DAlarm?=null
 private var started=false
 private val handler=Handler(Looper.getMainLooper())
 private val notificationId=42
 private var screenReceiverRegistered=false
 private val screenReceiver=object:BroadcastReceiver(){override fun onReceive(c:Context,intent:Intent){
  if(intent.action==Intent.ACTION_SCREEN_OFF && started && RingPreferences.power(this@RingService)){
   startService(Intent(this@RingService,RingService::class.java).setAction("SNOOZE"))
  }
 }}
 override fun onCreate(){
  super.onCreate()
  if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED)else registerScreenReceiverLegacy()
  screenReceiverRegistered=true
 }
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 private fun registerScreenReceiverLegacy(){registerReceiver(screenReceiver,IntentFilter(Intent.ACTION_SCREEN_OFF))}
 companion object {
  @Volatile private var ringing=false
  fun active(c:Context):Boolean=ringing
  fun activeId(c:Context):Int=c.getSharedPreferences("darly_runtime",0).getInt("id",-1)
 }
 private fun setActive(on:Boolean){ringing=on;getSharedPreferences("darly_runtime",0).edit().putBoolean("ringing",on).putInt("id",if(on)id else -1).apply();AlarmStore.notifyChanged(this)}
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  when(intent?.action){
   "STOP"->{stopSelf();return START_NOT_STICKY}
   "SNOOZE"->{
    val current=alarm
    if(current==null){stopSelf();return START_NOT_STICKY}
    val until=System.currentTimeMillis()+current.snooze*60000L
    val pi=PendingIntent.getBroadcast(this,current.id xor 0x5A5A5A5A,Intent(this,AlarmReceiver::class.java).putExtra("id",current.id).putExtra("snoozed",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    try{
     getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(until,PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)),pi)
    }catch(_:Exception){
     android.widget.Toast.makeText(this,"Не удалось отложить сигнал. Проверь разрешение на точные будильники.",android.widget.Toast.LENGTH_LONG).show()
     return START_NOT_STICKY
    }
    AlarmStore.setSnooze(this,current.id,until)
    stopSelf();return START_NOT_STICKY
   }
  }
  val incomingId=intent?.getIntExtra("id",-1)?:-1
  if(incomingId<0)return START_NOT_STICKY
  if(started && incomingId==id)return START_NOT_STICKY
  // A new cascade alarm must not be silently dropped while an earlier alarm rings.
  if(started){handler.removeCallbacksAndMessages(null);try{player?.stop()}catch(_:Exception){};player?.release();player=null;vibrator?.cancel()}
  id=incomingId;alarm=AlarmStore.all(this).firstOrNull{it.id==id}
  if(alarm==null){stopSelf();return START_NOT_STICKY}
  val channel=NotificationChannel("ring_v2","Активный будильник",NotificationManager.IMPORTANCE_HIGH).apply{lockscreenVisibility=Notification.VISIBILITY_PUBLIC;setSound(null,null);enableVibration(false)}
  getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  val launchOptions=if(Build.VERSION.SDK_INT>=35)ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()else null
  val show=PendingIntent.getActivity(this,1,Intent(this,RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,launchOptions)
  fun action(which:String,request:Int)=PendingIntent.getService(this,request,Intent(this,RingService::class.java).setAction(which),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(this,"ring_v2").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("DARLY · ${alarm?.title?:"Будильник"}").setContentText("Пора просыпаться").setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setContentIntent(show).setFullScreenIntent(show,true).addAction(android.R.drawable.ic_media_pause,"Отложить",action("SNOOZE",2)).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Выключить",action("STOP",3)).build()
  // FSI may start the Activity immediately: publish the active alarm first.
  started=true;setActive(true)
  try{startForeground(notificationId,notification)}catch(_:Exception){stopSelf();return START_NOT_STICKY}
  // Explicit launch: full-screen notification alone can be suppressed by OEM/Android settings.
  try { startActivity(Intent(this,RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }catch(_:Exception){}
  if(alarm?.vibrate!=false){try{vibrator=if(Build.VERSION.SDK_INT>=31)getSystemService(VibratorManager::class.java).defaultVibrator else getSystemService(VIBRATOR_SERVICE) as Vibrator;vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,650,400,650,900),0))}catch(_:Exception){}}
  val uri=BundledSounds.uri(this,alarm?.sound.orEmpty())
  try{startSound(uri)}catch(_:Exception){try{player?.release();startSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))}catch(_:Exception){}}
  return START_NOT_STICKY
 }
 private fun startSound(uri:Uri){val fade=(alarm?.fade?:30).coerceAtLeast(0);player=MediaPlayer().apply{setDataSource(this@RingService,uri);setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());isLooping=true;prepare();setVolume(if(fade>0)0.05f else 1f,if(fade>0)0.05f else 1f);start()};if(fade>0){val start=SystemClock.uptimeMillis();val task=object:Runnable{override fun run(){val p=player?:return;val volume=((SystemClock.uptimeMillis()-start).toFloat()/(fade*1000)).coerceIn(0.05f,1f);try{p.setVolume(volume,volume)}catch(_:Exception){};if(volume<1f)handler.postDelayed(this,500)}};handler.post(task)}}
 override fun onDestroy(){if(screenReceiverRegistered)unregisterReceiver(screenReceiver);handler.removeCallbacksAndMessages(null);try{player?.stop()}catch(_:Exception){};player?.release();player=null;vibrator?.cancel();started=false;setActive(false);super.onDestroy()}
}
