package com.darly.alarm

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*

/** Shared quiet chrome for focused secondary screens. */
object ModernScreen {
 fun create(a:Activity,title:String,subtitle:String="",back:()->Unit):LinearLayout {
  Ui.applyTheme(a)
  a.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
  a.window.statusBarColor=Color.TRANSPARENT;a.window.navigationBarColor=0xff0b1220.toInt()
  @Suppress("DEPRECATION")
  a.window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
  if(Build.VERSION.SDK_INT>=29)a.window.isStatusBarContrastEnforced=false
  val root=FrameLayout(a)
  root.addView(ImageView(a).apply{setImageDrawable(Ui.wallpaper(a));scaleType=ImageView.ScaleType.CENTER_CROP;Ui.animateWallpaper(a,this)},FrameLayout.LayoutParams(-1,-1))
  root.addView(View(a).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xb3091323.toInt(),0xec0b1220.toInt(),0xff0b1220.toInt()))},FrameLayout.LayoutParams(-1,-1))
  val scroll=ScrollView(a).apply{isVerticalScrollBarEnabled=false;clipToPadding=false}
  val body=Ui.column(a).apply{setPadding(Ui.dp(a,20),Ui.dp(a,18),Ui.dp(a,20),Ui.dp(a,32))}
  scroll.addView(body);root.addView(scroll,FrameLayout.LayoutParams(-1,-1))
  if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener{_,insets->val b=insets.getInsets(WindowInsets.Type.systemBars());scroll.setPadding(0,b.top,0,b.bottom);insets}
  else scroll.fitsSystemWindows=true
  val header=Ui.row(a)
  header.addView(Ui.text(a,"‹",32f,Ui.WHITE).apply{gravity=Gravity.CENTER;contentDescription="Назад";background=Ui.shape(0x331d3049,Ui.dp(a,16).toFloat());Ui.interactive(a,this,back)},LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)))
  header.addView(Ui.hspace(a,14))
  val heading=Ui.column(a)
  heading.addView(Ui.text(a,title,23f,Ui.WHITE,true))
  if(subtitle.isNotBlank()){heading.addView(Ui.space(a,4));heading.addView(Ui.text(a,subtitle,12f,Ui.muted))}
  header.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
  body.addView(header);body.addView(Ui.space(a,24));a.setContentView(root)
  return body
 }
}
