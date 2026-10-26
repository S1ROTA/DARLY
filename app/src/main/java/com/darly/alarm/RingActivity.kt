package com.darly.alarm

import android.app.Activity
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import java.text.SimpleDateFormat
import java.util.*

class RingActivity:Activity(){
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);recreate()}
 override fun onResume(){super.onResume();if(!RingService.active(this))finish()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  if(!RingService.active(this)){finish();return}
  // Activity.setShowWhenLocked / setTurnScreenOn were introduced in API 27.
  // Android 8.0 (API 26) needs the legacy window flags instead.
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
   setShowWhenLocked(true)
   setTurnScreenOn(true)
  } else {
   @Suppress("DEPRECATION")
   window.addFlags(
    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
     WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
   )
  }
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  Ui.applyTheme(this)
  // Preserve the selected wallpaper behind the status bar while ringing.
  window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
  window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
  window.statusBarColor=Color.TRANSPARENT
  window.navigationBarColor=Ui.bg
  @Suppress("DEPRECATION")
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
  if(Build.VERSION.SDK_INT>=29)window.isStatusBarContrastEnforced=false
  val root=FrameLayout(this)
  root.addView(ImageView(this).apply{setImageDrawable(Ui.wallpaper(this@RingActivity));scaleType=ImageView.ScaleType.CENTER_CROP;Ui.animateWallpaper(this@RingActivity,this)},FrameLayout.LayoutParams(-1,-1))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xb30b1220.toInt(),0xc70a1525.toInt(),0xf508101b.toInt()))},FrameLayout.LayoutParams(-1,-1))
  val content=Ui.column(this).apply{gravity=Gravity.CENTER;Ui.pad(this@RingActivity,this,28,30)}
  root.addView(content,FrameLayout.LayoutParams(-1,-1))
  if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener{_,w->val s=w.getInsets(WindowInsets.Type.systemBars());content.setPadding(Ui.dp(this,28),s.top+Ui.dp(this,30),Ui.dp(this,28),s.bottom+Ui.dp(this,30));w}else content.fitsSystemWindows=true
  setContentView(root)
  val id=RingService.activeId(this)
  val alarm=AlarmStore.all(this).firstOrNull{it.id==id}
  val heading=Ui.text(this,"DARLY  /  СИГНАЛ",14f,Ui.gold,true).apply{gravity=Gravity.CENTER}
  content.addView(heading);content.addView(Ui.space(this,22))
  val pulse=Ui.text(this,"◉",46f,Ui.gold,true).apply{gravity=Gravity.CENTER;contentDescription="Звонит будильник"}
  content.addView(pulse);content.addView(Ui.space(this,12))
  if(ValueAnimator.areAnimatorsEnabled())ObjectAnimator.ofFloat(pulse,View.ALPHA,.55f,1f).apply{duration=900L;repeatCount=ValueAnimator.INFINITE;repeatMode=ValueAnimator.REVERSE;start()}
  val clock=Ui.text(this,SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),77f,Ui.WHITE,true).apply{gravity=Gravity.CENTER}
  content.addView(clock);content.addView(Ui.space(this,12))
  content.addView(Ui.text(this,alarm?.title?:"Будильник",24f,Ui.WHITE,true).apply{gravity=Gravity.CENTER});content.addView(Ui.space(this,18))
  content.addView(Ui.text(this,"Пора начинать день",15f,Ui.muted).apply{gravity=Gravity.CENTER})
  content.addView(Ui.space(this,70))
  content.addView(Ui.button(this,"Отложить на ${alarm?.snooze?:5} мин"){
   startService(Intent(this,RingService::class.java).setAction("SNOOZE"));finish()
  });content.addView(Ui.space(this,13))
  content.addView(Ui.outlineButton(this,"Остановить"){
   startService(Intent(this,RingService::class.java).setAction("STOP"));finish()
  })
 }
}
