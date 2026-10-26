package com.darly.alarm

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import java.util.Calendar

/** Minimal native-view design system. No third-party runtime dependencies. */
object Ui {
 var bg=0xff080f1b.toInt()
 var card=0xe8172334.toInt()
 var blue=0xff75a6ff.toInt()
 var gold=0xffe3bd80.toInt()
 var muted=0xffaab9ca.toInt()
 val WHITE=0xfff4f7fc.toInt()
 fun selectedTheme(c:Context)=c.getSharedPreferences("darly",0).getString("theme","Neo Dark")?:"Neo Dark"
 fun theme(c:Context):String {val t=selectedTheme(c);if(t!="Сезонная")return t;return when(Calendar.getInstance().get(Calendar.MONTH)){11,0,1->"Зима";2,3,4->"Весна";5,6,7->"Лето";else->"Осень"}}
 fun background(c:Context):Int=when(theme(c)){"Deadlock"->R.drawable.darly_deadlock;"Minecraft"->R.drawable.darly_minecraft;"Весна"->R.drawable.darly_spring;"Лето"->R.drawable.darly_summer;"Осень"->R.drawable.darly_autumn;"Зима"->R.drawable.darly_winter;else->R.drawable.darly_neo}
 fun applyTheme(c:Context){when(theme(c)){
  "Deadlock"->{bg=0xff11141b.toInt();card=0xe81b242d.toInt();blue=0xffd1a56d.toInt();gold=0xffebc38a.toInt();muted=0xffb8b8bb.toInt()}
  "Minecraft"->{bg=0xff101c18.toInt();card=0xe8192924.toInt();blue=0xff8dca9f.toInt();gold=0xffe2c486.toInt();muted=0xffabc3b8.toInt()}
  "Весна"->{bg=0xff171828.toInt();card=0xe8212437.toInt();blue=0xffb9b1ee.toInt();gold=0xffffcdb1.toInt();muted=0xffc3becf.toInt()}
  "Лето"->{bg=0xff0b1d29.toInt();card=0xe8122c3b.toInt();blue=0xff89d9e6.toInt();gold=0xffffdb95.toInt();muted=0xffb1cedc.toInt()}
  "Осень"->{bg=0xff1d1720.toInt();card=0xe829222c.toInt();blue=0xffeeb17a.toInt();gold=0xffffd4a0.toInt();muted=0xffd0bcb2.toInt()}
  "Зима"->{bg=0xff10192d.toInt();card=0xe818293c.toInt();blue=0xff92bfff.toInt();gold=0xffd9e9fc.toInt();muted=0xffb6cadd.toInt()}
  else->{bg=0xff080f1b.toInt();card=0xe8172334.toInt();blue=0xff75a6ff.toInt();gold=0xffe3bd80.toInt();muted=0xffaab9ca.toInt()}
 }}
 fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density+.5f).toInt()
 fun shape(color:Int,r:Float=20f,stroke:Int?=null)=GradientDrawable().apply{setColor(color);cornerRadius=r;if(stroke!=null)setStroke(1,stroke)}
 fun text(c:Context,s:String,size:Float=16f,color:Int=WHITE,bold:Boolean=false)=TextView(c).apply{
  text=s;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL;includeFontPadding=false
  if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
 }
 fun column(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL}
 fun row(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
 fun space(c:Context,h:Int)=View(c).apply{layoutParams=LinearLayout.LayoutParams(1,dp(c,h))}
 fun pad(c:Context,v:View,x:Int=18,y:Int=16){v.setPadding(dp(c,x),dp(c,y),dp(c,x),dp(c,y))}
 fun feedback(c:Context,v:View){
  val p=c.getSharedPreferences("darly",0)
  if(p.getBoolean("sound_buttons",true))v.playSoundEffect(SoundEffectConstants.CLICK)
  if(p.getBoolean("haptic_buttons",true))v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
 }
 fun interactive(c:Context,v:View,action:()->Unit){
  v.isClickable=true;v.isFocusable=true
  v.setOnTouchListener{view,event->
   if(c.getSharedPreferences("darly",0).getBoolean("animations",true))when(event.actionMasked){
    MotionEvent.ACTION_DOWN->view.animate().scaleX(.965f).scaleY(.965f).setDuration(95).setInterpolator(DecelerateInterpolator()).start()
    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->view.animate().scaleX(1f).scaleY(1f).setDuration(190).setInterpolator(DecelerateInterpolator()).start()
   }
   false
  }
  v.setOnClickListener{feedback(c,v);action()}
 }
 fun button(c:Context,s:String,action:()->Unit)=text(c,s,15f,0xff08111f.toInt(),true).apply{
  gravity=Gravity.CENTER;background=shape(blue,dp(c,17).toFloat());setPadding(dp(c,17),dp(c,15),dp(c,17),dp(c,15));minHeight=dp(c,51)
  interactive(c,this,action)
 }
 fun outlineButton(c:Context,s:String,action:()->Unit)=text(c,s,15f,WHITE,true).apply{
  gravity=Gravity.CENTER;background=shape(0xc81d2b40.toInt(),dp(c,16).toFloat(),0x56678094);setPadding(dp(c,15),dp(c,14),dp(c,15),dp(c,14));minHeight=dp(c,49)
  interactive(c,this,action)
 }
 fun card(c:Context)=column(c).apply{
  background=shape(card,dp(c,20).toFloat(),0x4d7d91ac);elevation=dp(c,3).toFloat();pad(c,this,17,17)
 }
 fun switch(c:Context,on:Boolean)=Switch(c).apply{
  setOnTouchListener{v,event->if(event.actionMasked==MotionEvent.ACTION_UP)feedback(c,v);false}
  isChecked=on;thumbTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(blue,muted))
  trackTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(0xff375a78.toInt(),0xff344054.toInt()))
 }
 fun divider(c:Context)=View(c).apply{setBackgroundColor(0x457f92aa);layoutParams=LinearLayout.LayoutParams(-1,dp(c,1))}
}
