package com.darly.alarm

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.graphics.drawable.Drawable
import android.graphics.BitmapShader
import android.graphics.Color as AndroidColor
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.animation.PathInterpolator
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import java.util.Calendar

/** Minimal native-view design system. No third-party runtime dependencies. */
object Ui {
 // Prevent background decode on every tab switch; original assets are 720×1440.
 // Cache is memory bounded and decoding never inflates bitmaps to device density.
 private val wallpaperCache=object:LruCache<Int,Bitmap>(16*1024*1024){
  override fun sizeOf(key:Int,value:Bitmap):Int=value.byteCount
 }
 private var customCached:Bitmap?=null
 private var customStamp:Long=Long.MIN_VALUE
 fun clearWallpaperCache(){wallpaperCache.evictAll();customCached=null;customStamp=Long.MIN_VALUE}
 fun hasCustomWallpaper(c:Context)=c.getFileStreamPath("darly_custom_wallpaper.jpg").exists()
 /** The source is copied byte-for-byte; only its *display bitmap* is size-limited.
  * No fixed 2x downsampling: preserves sharp detail on high-resolution phones. */
 fun customWallpaper(c:Context):BitmapDrawable?=try{
  val file=c.getFileStreamPath("darly_custom_wallpaper.jpg")
  if(!file.exists())null else {
   val stamp=file.lastModified() xor file.length()
   var bitmap=customCached?.takeIf{customStamp==stamp && !it.isRecycled}
   if(bitmap==null){
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeFile(file.absolutePath,bounds)
    if(bounds.outWidth<=0||bounds.outHeight<=0)throw IllegalArgumentException("Not an image")
    val dm=c.resources.displayMetrics
    val (tw,th)=WallpaperSize.target(bounds.outWidth,bounds.outHeight,dm.widthPixels,dm.heightPixels)
    bitmap=if(Build.VERSION.SDK_INT>=28){
     val source=ImageDecoder.createSource(file)
     ImageDecoder.decodeBitmap(source){decoder,_,_->
      decoder.setTargetSize(tw,th)
      decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
     }
    }else{
     var sample=1
     while(bounds.outWidth/(sample*2)>=tw && bounds.outHeight/(sample*2)>=th)sample*=2
     BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample;inScaled=false})
    }
    customCached=bitmap;customStamp=stamp
   }
   bitmap?.let{BitmapDrawable(c.resources,it)}
  }
 }catch(_:Exception){null}
 fun wallpaper(c:Context):Drawable{
  if(theme(c)=="Своя фотография")customWallpaper(c)?.let{return it}
  val id=background(c)
  val bitmap=synchronized(wallpaperCache){
   wallpaperCache.get(id)?:BitmapFactory.decodeResource(c.resources,id,BitmapFactory.Options().apply{inScaled=false}).also{wallpaperCache.put(id,it)}
  }
  return BitmapDrawable(c.resources,bitmap)
 }
 var bg=0xff080f1b.toInt()
 var card=0xe8172334.toInt()
 var blue=0xff75a6ff.toInt()
 var gold=0xffe3bd80.toInt()
 var muted=0xffaab9ca.toInt()
 val WHITE=0xfff4f7fc.toInt()
 fun selectedTheme(c:Context)=c.getSharedPreferences("darly",0).getString("theme","Сезонная")?:"Сезонная"
 fun theme(c:Context):String {val t=selectedTheme(c);if(t!="Сезонная")return t;return when(Calendar.getInstance().get(Calendar.MONTH)){11,0,1->"Зима";2,3,4->"Весна";5,6,7->"Лето";else->"Осень"}}
 fun background(c:Context):Int=when(theme(c)){"Deadlock"->R.drawable.darly_deadlock;"Minecraft"->R.drawable.darly_minecraft;"Весна"->R.drawable.darly_spring;"Лето"->R.drawable.darly_summer;"Осень"->R.drawable.darly_autumn;"Зима"->R.drawable.darly_winter;else->R.drawable.darly_neo}
 fun applyTheme(c:Context){when(theme(c)){
  "Своя фотография"->{
   bg=0xff101724.toInt();card=0xe8172435.toInt();blue=0xff91bce8.toInt();gold=0xffe7c79e.toInt();muted=0xffbccbd7.toInt()
   try{
    val file=c.getFileStreamPath("darly_custom_wallpaper.jpg")
    val options=BitmapFactory.Options().apply{inSampleSize=16}
    val bmp=BitmapFactory.decodeFile(file.absolutePath,options)
    if(bmp!=null){
     val colors=mutableListOf<Int>()
     for(y in 0 until bmp.height step 3)for(x in 0 until bmp.width step 3){
      val px=bmp.getPixel(x,y);val hsv=FloatArray(3);Color.colorToHSV(px,hsv)
      if(hsv[1]>.22f && hsv[2]>.25f && hsv[2]<.95f)colors.add(px)
     }
     if(colors.isNotEmpty()){
      val chosen=colors[colors.size/2];val hsv=FloatArray(3);Color.colorToHSV(chosen,hsv)
      hsv[1]=hsv[1].coerceIn(.32f,.68f);hsv[2]=.92f;blue=Color.HSVToColor(hsv)
      hsv[2]=.16f;hsv[1]=.36f;bg=Color.HSVToColor(hsv);card=(0xe8 shl 24) or (bg and 0xffffff)
     }
     bmp.recycle()
    }
   }catch(_:Exception){}
  }
  "Deadlock"->{bg=0xff11141b.toInt();card=0xe81b242d.toInt();blue=0xffd1a56d.toInt();gold=0xffebc38a.toInt();muted=0xffb8b8bb.toInt()}
  "Minecraft"->{bg=0xff101c18.toInt();card=0xe8192924.toInt();blue=0xff8dca9f.toInt();gold=0xffe2c486.toInt();muted=0xffabc3b8.toInt()}
  "Весна"->{bg=0xff171828.toInt();card=0xe8212437.toInt();blue=0xffb9b1ee.toInt();gold=0xffffcdb1.toInt();muted=0xffc3becf.toInt()}
  "Лето"->{bg=0xff0b1d29.toInt();card=0xe8122c3b.toInt();blue=0xff89d9e6.toInt();gold=0xffffdb95.toInt();muted=0xffb1cedc.toInt()}
  "Осень"->{bg=0xff1d1720.toInt();card=0xe829222c.toInt();blue=0xffeeb17a.toInt();gold=0xffffd4a0.toInt();muted=0xffd0bcb2.toInt()}
  "Зима"->{bg=0xff10192d.toInt();card=0xe818293c.toInt();blue=0xff92bfff.toInt();gold=0xffd9e9fc.toInt();muted=0xffb6cadd.toInt()}
  else->{bg=0xff080f1b.toInt();card=0xe8172334.toInt();blue=0xff75a6ff.toInt();gold=0xffe3bd80.toInt();muted=0xffaab9ca.toInt()}
 }}
 fun animateWallpaper(c:Context,image:android.widget.ImageView){
  if(!c.getSharedPreferences("darly",0).getBoolean("animations",true))return
  if(!ValueAnimator.areAnimatorsEnabled())return
  val custom=theme(c)=="Своя фотография"
  val begin=if(custom)1.015f else 1.04f
  val end=if(custom)1.045f else 1.10f
  image.scaleX=begin;image.scaleY=begin
  image.animate().cancel()
  image.animate().scaleX(end).scaleY(end).setDuration(28000L)
   .setInterpolator(LinearInterpolator()).withEndAction{
    if(image.isAttachedToWindow)image.animate().scaleX(begin).scaleY(begin).setDuration(28000L).start()
   }.start()
 }
 fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density+.5f).toInt()
 fun shape(color:Int,r:Float=20f,stroke:Int?=null)=GradientDrawable().apply{setColor(color);cornerRadius=r;if(stroke!=null)setStroke(1,stroke)}
 fun text(c:Context,s:String,size:Float=16f,color:Int=WHITE,bold:Boolean=false)=TextView(c).apply{
  text=s;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL;includeFontPadding=false
  if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
 }
 fun column(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL}
 fun row(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
 fun space(c:Context,h:Int)=View(c).apply{layoutParams=LinearLayout.LayoutParams(1,dp(c,h))}
 /** Horizontal gaps must have a width, not the 1px vertical spacer's width. */
 fun hspace(c:Context,w:Int)=View(c).apply{layoutParams=LinearLayout.LayoutParams(dp(c,w),1)}
 fun pad(c:Context,v:View,x:Int=18,y:Int=16){v.setPadding(dp(c,x),dp(c,y),dp(c,x),dp(c,y))}
 fun feedback(c:Context,v:View){
  val p=c.getSharedPreferences("darly",0)
  if(p.getBoolean("sound_buttons",true))v.playSoundEffect(SoundEffectConstants.CLICK)
  if(p.getBoolean("haptic_buttons",true))v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
 }
 private var lastWheelFeedback=0L
 fun wheelFeedback(c:Context,v:View){
  val now=android.os.SystemClock.uptimeMillis()
  if(now-lastWheelFeedback<65L)return
  lastWheelFeedback=now
  val p=c.getSharedPreferences("darly",0)
  if(p.getBoolean("sound_buttons",true))v.playSoundEffect(SoundEffectConstants.CLICK)
  if(p.getBoolean("haptic_buttons",true))v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
 }
 fun interactive(c:Context,v:View,action:()->Unit){
  v.isClickable=true;v.isFocusable=true
  v.setOnTouchListener{view,event->
   if(c.getSharedPreferences("darly",0).getBoolean("animations",true))when(event.actionMasked){
    MotionEvent.ACTION_DOWN->{
     view.animate().cancel()
     view.animate().scaleX(.985f).scaleY(.985f).setDuration(150)
      .setInterpolator(PathInterpolator(.25f,.1f,.25f,1f)).start()
    }
    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
     view.animate().cancel()
     view.animate().scaleX(1f).scaleY(1f).setDuration(300)
      .setInterpolator(PathInterpolator(.16f,1f,.3f,1f)).start()
    }
   }
   false
  }
  v.setOnClickListener{feedback(c,v);action()}
 }
 fun button(c:Context,s:String,action:()->Unit)=text(c,s,15f,0xff08111f.toInt(),true).apply{
  gravity=Gravity.CENTER;background=shape(blue,dp(c,17).toFloat());setPadding(dp(c,15),dp(c,14),dp(c,15),dp(c,14));minHeight=dp(c,54)
  interactive(c,this,action)
 }
 fun outlineButton(c:Context,s:String,action:()->Unit)=text(c,s,15f,WHITE,true).apply{
  gravity=Gravity.CENTER;background=shape(0xc81d2b40.toInt(),dp(c,16).toFloat(),0x56678094);setPadding(dp(c,15),dp(c,14),dp(c,15),dp(c,14));minHeight=dp(c,54)
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
