package com.darly.alarm

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.*

class RingActivity:Activity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setShowWhenLocked(true);setTurnScreenOn(true);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);window.statusBarColor=0xff14182a.toInt();val root=Ui.column(this).apply{gravity=Gravity.CENTER;Ui.pad(this@RingActivity,this,26,28);background=GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(0xff091224.toInt(),0xff292b53.toInt(),0xffdb895b.toInt()))};setContentView(root)
  val sun=Ui.text(this,"☀",112f,Ui.gold).apply{gravity=Gravity.CENTER;alpha=0f;translationY=Ui.dp(this@RingActivity,100).toFloat()};root.addView(sun);sun.animate().alpha(1f).translationY(0f).setDuration(9000).start();root.addView(Ui.space(this,24));root.addView(Ui.text(this,SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),65f,Color.WHITE,true).apply{gravity=Gravity.CENTER});root.addView(Ui.text(this,"Доброе утро",26f,Color.WHITE,true).apply{gravity=Gravity.CENTER});root.addView(Ui.space(this,55));root.addView(Ui.button(this,"Отложить"){startService(Intent(this,RingService::class.java).setAction("SNOOZE"));finish()});root.addView(Ui.space(this,18));root.addView(Ui.button(this,"Выключить"){startService(Intent(this,RingService::class.java).setAction("STOP"));finish()})
 }
}
