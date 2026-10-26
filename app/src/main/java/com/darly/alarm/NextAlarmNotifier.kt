package com.darly.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persistent alarm icon + OS-driven live countdown; does not wake the app every minute. */
object NextAlarmNotifier {
 private const val ID=9090
 fun sync(context:Context){
  val manager=context.getSystemService(NotificationManager::class.java)
  if(Build.VERSION.SDK_INT>=31&&!context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()){
   manager.cancel(ID);return
  }
  val next=AlarmStore.nextUpcoming(context)
  if(next==null){manager.cancel(ID);return}
  if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
   manager.cancel(ID);return
  }
  manager.createNotificationChannel(NotificationChannel("upcoming_alarms_v1","Запланированные будильники",NotificationManager.IMPORTANCE_LOW).apply{setShowBadge(false)})
  val open=PendingIntent.getActivity(context,9090,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val clock=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(next.whenAt))
  val day=SimpleDateFormat("EEE, d MMM",Locale("ru")).format(Date(next.whenAt))
  val type=if(next.snoozed)"Отложенный сигнал" else "Будильник включён"
  val notification=Notification.Builder(context,"upcoming_alarms_v1")
   .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
   .setContentTitle("$type · $clock")
   .setContentText("${CountdownText.until(next.whenAt)} · ${next.alarm.title}")
   .setSubText(day)
   // The platform chronometer updates on its own, including while DARLY is closed.
   .setWhen(next.whenAt).setUsesChronometer(true).setChronometerCountDown(true)
   .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
   .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC).build()
  manager.notify(ID,notification)
 }
}
