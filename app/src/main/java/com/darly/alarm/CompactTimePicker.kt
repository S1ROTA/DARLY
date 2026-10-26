package com.darly.alarm

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView

/** Compact 24-hour time input with native wheel/fling behavior and tick feedback. */
class CompactTimePicker(context:Context):LinearLayout(context){
 private val hours=NumberPicker(context)
 private val minutes=NumberPicker(context)
 private var timeChanged:(()->Unit)?=null
 var hour:Int
  get()=hours.value
  set(value){hours.value=value.coerceIn(0,23)}
 var minute:Int
  get()=minutes.value
  set(value){minutes.value=value.coerceIn(0,59)}
 fun setOnTimeChangedListener(listener:()->Unit){timeChanged=listener}
 init {
  orientation=VERTICAL
  background=Ui.shape(Ui.card,Ui.dp(context,20).toFloat(),0x4d7d91ac)
  setPadding(Ui.dp(context,12),Ui.dp(context,10),Ui.dp(context,12),Ui.dp(context,9))
  val columns=Ui.row(context).apply{gravity=Gravity.CENTER}
  fun wheel(picker:NumberPicker,min:Int,max:Int){
   picker.minValue=min;picker.maxValue=max;picker.wrapSelectorWheel=true
   picker.displayedValues=Array(max-min+1){"%02d".format(it+min)}
   if(android.os.Build.VERSION.SDK_INT>=29)picker.setTextColor(Ui.WHITE)
   picker.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
   picker.setOnValueChangedListener{view,_,_->Ui.wheelFeedback(context,view);timeChanged?.invoke()}
   picker.setOnScrollListener{_,state->if(state==NumberPicker.OnScrollListener.SCROLL_STATE_IDLE)timeChanged?.invoke()}
   for(i in 0 until picker.childCount){(picker.getChildAt(i) as? EditText)?.apply{
    setTextColor(Ui.WHITE);textSize=23f;gravity=Gravity.CENTER
   }}
  }
  wheel(hours,0,23);wheel(minutes,0,59)
  val lp=LayoutParams(0,Ui.dp(context,137),1f)
  columns.addView(hours,lp)
  columns.addView(Ui.text(context,":",25f,Ui.muted,true).apply{gravity=Gravity.CENTER},LayoutParams(Ui.dp(context,20),-1))
  columns.addView(minutes,LayoutParams(0,Ui.dp(context,137),1f))
  addView(columns)
  val labels=Ui.row(context)
  labels.addView(Ui.text(context,"ЧАСЫ",11f,Ui.muted).apply{gravity=Gravity.CENTER},LayoutParams(0,Ui.dp(context,23),1f))
  labels.addView(Ui.text(context,"МИНУТЫ",11f,Ui.muted).apply{gravity=Gravity.CENTER},LayoutParams(0,Ui.dp(context,23),1f))
  addView(labels)
  contentDescription="Время сигнала: выбор часов и минут"
 }
}
