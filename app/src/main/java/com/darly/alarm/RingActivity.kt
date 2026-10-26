package com.darly.alarm
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
class RingActivity: Activity() {
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setShowWhenLocked(true); setTurnScreenOn(true); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(30,30,30,30); background=GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(0xff17131d.toInt(),0xff493025.toInt(),0xffd79757.toInt())) }
  setContentView(root)
  val sun=TextView(this).apply{text="☀";textSize=110f;gravity=Gravity.CENTER;setTextColor(0xffffd18b.toInt());alpha=0f;translationY=180f};root.addView(sun);sun.animate().alpha(1f).translationY(0f).setDuration(12000).start()
  val time=TextView(this).apply{text=java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date());textSize=65f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)};root.addView(time)
  val msg=TextView(this).apply{text="Доброе утро ✨";textSize=25f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)};root.addView(msg)
  val stop=Button(this).apply{text="Выключить";setOnClickListener{startService(Intent(this@RingActivity,RingService::class.java).setAction("STOP"));finish()}};root.addView(stop)
  val snooze=Button(this).apply{text="Отложить на 5 минут";setOnClickListener{startService(Intent(this@RingActivity,RingService::class.java).setAction("SNOOZE"));finish()}};root.addView(snooze)
 }
}
