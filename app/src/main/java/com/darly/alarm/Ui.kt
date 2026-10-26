package com.darly.alarm

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

object Ui {
 val bg=Color.rgb(7,12,24);val card=Color.rgb(17,27,45);val blue=Color.rgb(96,151,255);val gold=Color.rgb(255,194,107);val muted=Color.rgb(151,166,190)
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
