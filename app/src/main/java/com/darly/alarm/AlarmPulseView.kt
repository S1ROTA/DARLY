package com.darly.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

class AlarmPulseView(c:Context):View(c){
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private val mark=c.getDrawable(R.drawable.ic_darly_brand)!!
 private var phase=0f
 private var animation:ValueAnimator?=null
 fun start(){
  if(animation!=null||!ValueAnimator.areAnimatorsEnabled()||!context.getSharedPreferences("darly",0).getBoolean("animations",true))return
  animation=ValueAnimator.ofFloat(0f,1f).apply{duration=2400;repeatCount=ValueAnimator.INFINITE;interpolator=LinearInterpolator();addUpdateListener{phase=it.animatedValue as Float;invalidate()};start()}
 }
 fun stop(){animation?.cancel();animation=null}
 override fun onDetachedFromWindow(){stop();super.onDetachedFromWindow()}
 override fun onDraw(canvas:Canvas){
  val x=width/2f;val y=height/2f;val radius=min(width,height)*.46f
  paint.style=Paint.Style.FILL;paint.shader=RadialGradient(x,y,radius,intArrayOf(0x554fc3b4,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
  canvas.drawCircle(x,y,radius,paint);paint.shader=null
  paint.style=Paint.Style.STROKE;paint.strokeWidth=Ui.dp(context,2).toFloat()
  repeat(3){i->val p=(phase+i/3f)%1f;paint.color=Color.argb(((1-p)*130).toInt(),145,221,209);canvas.drawCircle(x,y,radius*(.45f+.55f*p),paint)}
  val size=(radius*1.22f).toInt();mark.setBounds(x.toInt()-size/2,y.toInt()-size/2,x.toInt()+size/2,y.toInt()+size/2);mark.draw(canvas)
 }
}
