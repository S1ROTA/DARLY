package com.darly.alarm

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Calendar

object Ui {
 var bg=Color.rgb(7,12,24);var card=Color.rgb(17,27,45);var blue=Color.rgb(96,151,255);var gold=Color.rgb(255,194,107);var muted=Color.rgb(151,166,190)
 fun selectedTheme(c:Context):String=c.getSharedPreferences("darly",0).getString("theme","Neo Dark")?:"Neo Dark"
 fun theme(c:Context):String { val selected=selectedTheme(c); if(selected!="Сезонная")return selected; return when(Calendar.getInstance().get(Calendar.MONTH)){11,0,1->"Зима";2,3,4->"Весна";5,6,7->"Лето";else->"Осень"} }
 fun background(c:Context):Int=when(theme(c)){"Deadlock"->R.drawable.darly_deadlock;"Minecraft"->R.drawable.darly_minecraft;"Весна"->R.drawable.darly_spring;"Лето"->R.drawable.darly_summer;"Осень"->R.drawable.darly_autumn;"Зима"->R.drawable.darly_winter;else->R.drawable.darly_neo}
 fun backdrop(c:Context):android.graphics.drawable.Drawable=android.graphics.drawable.BitmapDrawable(c.resources,android.graphics.BitmapFactory.decodeResource(c.resources,background(c))).apply{gravity=Gravity.FILL}

 fun applyTheme(c:Context){when(theme(c)){
  "Deadlock"->{bg=0xff111018.toInt();card=0xff25232d.toInt();blue=0xffb89255.toInt();gold=0xffe3bd76.toInt();muted=0xffb6a9a0.toInt()}
  "Minecraft"->{bg=0xff102119.toInt();card=0xff203c29.toInt();blue=0xff70b856.toInt();gold=0xfff2d278.toInt();muted=0xffb1c8aa.toInt()}
  "Весна"->{bg=0xff171d39.toInt();card=0xff272c46.toInt();blue=0xffdd8cb8.toInt();gold=0xffffd9b2.toInt();muted=0xffc3b9d2.toInt()}
  "Лето"->{bg=0xff0c2941.toInt();card=0xff183a4e.toInt();blue=0xff4ebbc5.toInt();gold=0xffffdf86.toInt();muted=0xffb1d4d9.toInt()}
  "Осень"->{bg=0xff2a1923.toInt();card=0xff382932.toInt();blue=0xffe18c48.toInt();gold=0xffffc979.toInt();muted=0xffd3b8a8.toInt()}
  "Зима"->{bg=0xff0b203a.toInt();card=0xff192d49.toInt();blue=0xff86bfff.toInt();gold=0xffdcecff.toInt();muted=0xffafc6dc.toInt()}
  else->{bg=0xff070c18.toInt();card=0xff111b2d.toInt();blue=0xff6097ff.toInt();gold=0xffffc26b.toInt();muted=0xff97a6be.toInt()}
 }}
 fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density).toInt()
 fun shape(color:Int,r:Float=22f,stroke:Int?=null):GradientDrawable=GradientDrawable().apply{setColor(color);cornerRadius=r;if(stroke!=null)setStroke(2,stroke)}
 fun text(c:Context,s:String,size:Float=16f,color:Int=Color.WHITE,bold:Boolean=false):TextView=TextView(c).apply{text=s;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL;if(bold)typeface=Typeface.DEFAULT_BOLD}
 fun column(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL}
 fun row(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
 fun space(c:Context,h:Int)=View(c).apply{layoutParams=LinearLayout.LayoutParams(1,dp(c,h))}
 fun pad(c:Context,v:View,x:Int=18,y:Int=16){v.setPadding(dp(c,x),dp(c,y),dp(c,x),dp(c,y))}
 fun button(c:Context,s:String,action:()->Unit):TextView=text(c,s,16f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=shape(blue,dp(c,18).toFloat());setPadding(dp(c,18),dp(c,15),dp(c,18),dp(c,15));setOnClickListener{action()}}
 fun switch(c:Context,on:Boolean)=Switch(c).apply{isChecked=on;thumbTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(blue,muted));trackTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(0xff37568e.toInt(),0xff344054.toInt()))}
}
