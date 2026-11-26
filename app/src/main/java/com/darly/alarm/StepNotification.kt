package com.darly.alarm

import android.app.*
import android.content.*
import java.time.LocalDate
import java.text.NumberFormat

object StepNotification {
 fun build(c:Context):Notification {
  val steps=StepStore.ledger(c).days.firstOrNull{it.date==LocalDate.now().toString()}?.steps?:0
  val goal=StepStore.goal(c);val format=NumberFormat.getIntegerInstance(java.util.Locale("ru"))
  val open=PendingIntent.getActivity(c,StepsService.ID,Intent(c,MainActivity::class.java).putExtra("open_activity",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val pause=PendingIntent.getService(c,StepsService.ID,Intent(c,StepsService::class.java).setAction("PAUSE"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(c,"daily_steps_v1").setSmallIcon(R.drawable.ic_activity_nav)
   .setContentTitle("Активность · ${format.format(steps)} шагов")
   .setContentText("Цель ${format.format(goal)} · ${(steps*100/goal).coerceAtMost(100)}% за сегодня")
   .setProgress(goal,steps.coerceAtMost(goal.toLong()).toInt(),false).setContentIntent(open)
   .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PUBLIC)
   .setCategory(Notification.CATEGORY_PROGRESS).addAction(android.R.drawable.ic_media_pause,"Пауза",pause).build()
 }
}
