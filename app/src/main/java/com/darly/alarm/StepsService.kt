package com.darly.alarm

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.*
import android.os.*
import android.provider.Settings
import java.time.*
import java.text.NumberFormat

/** Low-power hardware counter, no GPS, cloud account, polling API or wake lock. */
class StepsService:Service(),SensorEventListener {
 companion object {
  const val ID=9093
  @Volatile var active=false;private set
  fun allowed(c:Context)=Build.VERSION.SDK_INT<29||c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED
  fun sensor(c:Context):Sensor?=c.getSystemService(SensorManager::class.java).let{it.getDefaultSensor(Sensor.TYPE_STEP_COUNTER,true)?:it.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)}
  fun restore(c:Context){if(StepStore.enabled(c)&&allowed(c)&&!active)runCatching{c.startForegroundService(Intent(c,StepsService::class.java))}.onFailure{StepStore.setError(c,"Не удалось запустить учёт из фона. Открой «Активность» и нажми «Продолжить».")}}
  fun start(c:Context){StepStore.enable(c);restore(c)}
 }
 private lateinit var sensors:SensorManager
 private var sensorThread:HandlerThread?=null
 private val main=Handler(Looper.getMainLooper())
 private var registered=false
 @Volatile private var publishPending=false
 private val minute=object:Runnable{override fun run(){if(!active)return;StepStore.ensureToday(this@StepsService);notifyProgress();StepStore.changed(this@StepsService);main.postDelayed(this,60000)}}
 override fun onBind(i:Intent?)=null
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
  if(i?.action=="PAUSE"){StepStore.pause(this);stopSelf();return START_NOT_STICKY}
  if(!StepStore.enabled(this)||!allowed(this)){stopSelf();return START_NOT_STICKY}
  if(active){notifyProgress();return START_STICKY}
  sensors=getSystemService(SensorManager::class.java)
  val counter=sensor(this)
  if(counter==null){StepStore.setError(this,"В телефоне нет доступного датчика шагов.");StepStore.pause(this);stopSelf();return START_NOT_STICKY}
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("daily_steps_v1","Шаги за день",NotificationManager.IMPORTANCE_LOW).apply{lockscreenVisibility=Notification.VISIBILITY_PUBLIC;setShowBadge(false)})
  StepStore.ensureToday(this)
  try{
   if(Build.VERSION.SDK_INT>=34)startForeground(ID,notification(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)else startForeground(ID,notification())
   sensorThread=HandlerThread("DARLY-step-counter").apply{start()}
   registered=sensors.registerListener(this,counter,SensorManager.SENSOR_DELAY_NORMAL,10000000,Handler(sensorThread!!.looper))
   if(!registered)throw IllegalStateException("Датчик шагов недоступен. Попробуй включить учёт ещё раз.")
   active=true;StepStore.setError(this,null);main.post(minute)
  }catch(_:Exception){StepStore.setError(this,"Не удалось включить учёт. Проверь разрешение «Физическая активность».");StepStore.pause(this);stopSelf();return START_NOT_STICKY}
  return START_STICKY
 }
 override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
 override fun onSensorChanged(event:SensorEvent){
  val value=event.values.firstOrNull()?:return
  if(!value.isFinite()||value<0||!StepStore.enabled(this))return
  val wall=System.currentTimeMillis()-(SystemClock.elapsedRealtimeNanos()-event.timestamp).coerceAtLeast(0)/1000000
  val date=Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()).toLocalDate().toString()
  val boot=Settings.Global.getInt(contentResolver,Settings.Global.BOOT_COUNT,-1)
  StepStore.ensureToday(this);StepStore.record(this,value.toLong(),boot,event.timestamp,date,false)
  if(!active||!StepStore.enabled(this))return
  if(!publishPending){publishPending=true;main.postDelayed({publishPending=false;if(active&&StepStore.enabled(this)){notifyProgress();StepStore.changed(this)}},700)}
 }
 private fun notification():Notification=StepNotification.build(this)
 private fun notifyProgress(){runCatching{getSystemService(NotificationManager::class.java).notify(ID,notification())}}
 override fun onDestroy(){
  active=false;main.removeCallbacksAndMessages(null)
  if(registered)sensors.unregisterListener(this)
  sensorThread?.quitSafely();stopForeground(STOP_FOREGROUND_REMOVE);StepStore.changed(this);super.onDestroy()
 }
}
