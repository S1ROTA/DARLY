package com.darly.alarm

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmDaySelector(c:Context,initial:Int,private val minutes:()->Int,private val changed:(Int)->Unit):LinearLayout(c){
 private var mask=initial
 private val labels=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
 private val buttons=labels.mapIndexed{i,_->
  Ui.text(c,"",11f,Ui.WHITE,true).apply{
   gravity=Gravity.CENTER;setLineSpacing(Ui.dp(c,5).toFloat(),1f)
   Ui.interactive(c,this){mask=mask xor(1 shl i);refresh();changed(mask)}
   addView(this,LayoutParams(0,Ui.dp(c,64),1f).apply{if(i>0)marginStart=Ui.dp(c,4)})
  }
 }
 init{orientation=HORIZONTAL;refresh()}
 fun refresh(){
  val now=LocalDateTime.now()
  buttons.forEachIndexed{i,button->
   val date=AlarmDayDates.next(i,minutes(),now)
   val selected=mask and(1 shl i)!=0
   button.text="${labels[i]}\n${date.format(DateTimeFormatter.ofPattern("d.MM"))}"
   button.contentDescription="${labels[i]}, ${date.format(DateTimeFormatter.ofPattern("d MMMM",Locale("ru")))}, ${if(selected)"выбрано" else "не выбрано"}"
   button.isSelected=selected
   button.background=Ui.shape(if(selected)Ui.blue else 0xd9283547.toInt(),Ui.dp(context,14).toFloat())
   button.setTextColor(if(selected)0xff0d192b.toInt()else Ui.muted)
  }
 }
}
