package com.darly.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import kotlin.random.Random

/** Only snooze moves. Stop and all system controls stay outside this bounded area. */
class MovingSnoozeLayout(c:Context,private val button:View,private val enabled:Boolean):FrameLayout(c){
 private var motion:ValueAnimator?=null
 private var running=false
 private val step=object:Runnable{override fun run(){
  if(!running||!isShown||!canMove())return
  val x0=button.translationX;val y0=button.translationY
  val x1=Random.nextFloat()*(width-button.width).coerceAtLeast(0)
  val y1=Random.nextFloat()*(height-button.height).coerceAtLeast(0)
  motion=ValueAnimator.ofFloat(0f,1f).apply{
   duration=480;interpolator=AccelerateDecelerateInterpolator()
   addUpdateListener{val t=it.animatedValue as Float;button.translationX=x0+(x1-x0)*t;button.translationY=y0+(y1-y0)*t};start()
  }
  postDelayed(this,1450)
 }}
 init{addView(button,LayoutParams(Ui.dp(c,236),LayoutParams.WRAP_CONTENT));clipChildren=true}
 private fun canMove()=enabled&&ValueAnimator.areAnimatorsEnabled()&&context.getSharedPreferences("darly",0).getBoolean("animations",true)&&!context.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
 override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
  val buttonWidth=minOf(Ui.dp(context,236),width)
  button.measure(MeasureSpec.makeMeasureSpec(buttonWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.AT_MOST))
  button.layout(0,0,button.measuredWidth,button.measuredHeight)
  if(changed){motion?.cancel();button.translationX=(width-button.width)/2f;button.translationY=(height-button.height)/2f}
 }
 fun setRunning(value:Boolean){running=value;removeCallbacks(step);motion?.cancel();motion=null;if(value&&canMove())postDelayed(step,850)}
 override fun onDetachedFromWindow(){setRunning(false);super.onDetachedFromWindow()}
}
