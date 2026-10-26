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

/** Low-priority persistent notification: the status bar carries an alarm icon while scheduled. */
object NextAlarmNotifier {
 private const val ID=9090
 fun sync(context:Context){
  val manager=context.getSystemService(NotificationManager::class.java)
  if(Build.VERSION.SDK_INT>=31&&!context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()){manager.cancel(ID);return}
  val next=AlarmStore.all(context).mapNotNull{a->AlarmStore.nextTime(a)?.let{a to it}}.minByOrNull{it.second}
  if(next==null){manager.cancel(ID);return}
  if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
  manager.createNotificationChannel(NotificationChannel("upcoming_alarms_v1","Запланированные будильники",NotificationManager.IMPORTANCE_LOW).apply{setShowBadge(false)})
  val open=PendingIntent.getActivity(context,9090,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val clock=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(next.second))
  val day=SimpleDateFormat("EEE, d MMM",Locale("ru")).format(Date(next.second))
  val notification=Notification.Builder(context,"upcoming_alarms_v1")
   .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
   .setContentTitle("Будильник установлен · $clock")
   .setContentText("${next.first.title} · $day")
   .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
   .setShowWhen(false).setOnlyAlertOnce(true).build()
  manager.notify(ID,notification)
 }
}
