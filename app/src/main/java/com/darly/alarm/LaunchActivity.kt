package com.darly.alarm

import android.app.Activity
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Gravity
import android.view.View
import android.widget.*

class LaunchActivity:Activity(){
 private val handler=Handler(Looper.getMainLooper())
 private var finished=false
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  if(savedInstanceState!=null||!isTaskRoot||RingService.active(this)){openApp();return}
  window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=0xff0b1423.toInt()
  @Suppress("DEPRECATION")
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
  val root=FrameLayout(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xff142c3d.toInt(),0xff0b1423.toInt(),0xff172037.toInt()))}
  val photo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;alpha=0f}
  root.addView(photo,FrameLayout.LayoutParams(-1,-1))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x8c0b1423.toInt(),0xeb0b1423.toInt()))},FrameLayout.LayoutParams(-1,-1))
  val content=Ui.column(this).apply{gravity=Gravity.CENTER;setPadding(Ui.dp(this@LaunchActivity,28),0,Ui.dp(this@LaunchActivity,28),0)}
  content.addView(BrandOrbitView(this).apply{contentDescription="Анимация DARLY"},LinearLayout.LayoutParams(Ui.dp(this,190),Ui.dp(this,190)))
  content.addView(Ui.text(this,"DARLY",34f,Ui.WHITE,true).apply{gravity=Gravity.CENTER;letterSpacing=.17f})
  content.addView(Ui.space(this,12));content.addView(Ui.text(this,"Твоё время. Легче каждый день.",14f,0xffbed0dd.toInt()).apply{gravity=Gravity.CENTER})
  root.addView(content,FrameLayout.LayoutParams(-1,-1))
  val caption=Ui.text(this,"Готовим твой день",12f,0xff9aafc1.toInt()).apply{gravity=Gravity.CENTER;contentDescription="Подготовка приложения"}
  root.addView(caption,FrameLayout.LayoutParams(-1,Ui.dp(this,48),Gravity.BOTTOM).apply{bottomMargin=Ui.dp(this@LaunchActivity,32)})
  setContentView(root)
  val animated=ValueAnimator.areAnimatorsEnabled()&&getSharedPreferences("darly",0).getBoolean("animations",true)
  val began=SystemClock.uptimeMillis()
  Thread({
   val wallpaper=runCatching{Ui.wallpaper(applicationContext)}.getOrNull()
   runOnUiThread{
    if(isDestroyed||finished)return@runOnUiThread
    if(wallpaper!=null){photo.setImageDrawable(wallpaper);if(animated)photo.animate().alpha(.7f).setDuration(450).start()else photo.alpha=.7f}
    val remaining=if(animated)(700-(SystemClock.uptimeMillis()-began)).coerceAtLeast(0)else 0
    handler.postDelayed({openApp()},remaining)
   }
  },"DARLY-startup").start()
 }
 private fun openApp(){if(finished||isDestroyed)return;finished=true;startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);finish()}
 override fun onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy()}
}
