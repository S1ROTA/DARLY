package com.darly.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/** Native, cancellable brand animation; no video, network or extra animation library. */
class BrandOrbitView(c:Context):View(c){
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private val logo=c.getDrawable(R.drawable.ic_darly_brand)!!
 private var phase=0f
 private var animator:ValueAnimator?=null
 override fun onAttachedToWindow(){super.onAttachedToWindow()
  if(ValueAnimator.areAnimatorsEnabled()&&context.getSharedPreferences("darly",0).getBoolean("animations",true)){
   animator=ValueAnimator.ofFloat(0f,360f).apply{duration=4200;repeatCount=ValueAnimator.INFINITE;interpolator=LinearInterpolator();addUpdateListener{phase=it.animatedValue as Float;invalidate()};start()}
  }
 }
 override fun onDetachedFromWindow(){animator?.cancel();animator=null;super.onDetachedFromWindow()}
 override fun onDraw(canvas:Canvas){
  val cx=width/2f;val cy=height/2f;val radius=min(width,height)*.43f
  paint.style=Paint.Style.FILL
  paint.shader=RadialGradient(cx,cy,radius,intArrayOf(0x365fcfc3,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
  canvas.drawCircle(cx,cy,radius,paint);paint.shader=null
  paint.style=Paint.Style.STROKE;paint.strokeWidth=Ui.dp(context,1).toFloat();paint.color=0x337ecfc7
  canvas.drawCircle(cx,cy,radius*.88f,paint)
  paint.strokeWidth=Ui.dp(context,2).toFloat();paint.strokeCap=Paint.Cap.ROUND;paint.color=0xff96ddd2.toInt()
  val orbit=RectF(cx-radius*.88f,cy-radius*.88f,cx+radius*.88f,cy+radius*.88f)
  canvas.drawArc(orbit,phase-90f,64f,false,paint)
  paint.style=Paint.Style.FILL;paint.color=0xffeec79c.toInt()
  val angle=(phase-26)*PI/180
  canvas.drawCircle(cx+cos(angle).toFloat()*radius*.88f,cy+sin(angle).toFloat()*radius*.88f,Ui.dp(context,3).toFloat(),paint)
  val size=(radius*1.35f).toInt();logo.setBounds(cx.toInt()-size/2,cy.toInt()-size/2,cx.toInt()+size/2,cy.toInt()+size/2);logo.draw(canvas)
 }
}
