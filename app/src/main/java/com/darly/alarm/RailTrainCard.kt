package com.darly.alarm

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

object RailTrainCard {
 fun create(c:Context,train:YandexRaspApi.Train,tracking:Boolean=false,track:(()->Unit)?=null,open:()->Unit):LinearLayout=Ui.card(c).apply{
  contentDescription="Рейс ${train.number}, ${train.labelTime} — ${train.arrivalTime}, ${train.fareLabel}. Остановки и цена"
  val times=Ui.row(c)
  times.addView(Ui.text(c,train.labelTime,26f,Ui.WHITE,true))
  times.addView(Ui.text(c,"   →   ",13f,Ui.muted))
  times.addView(Ui.text(c,train.arrivalTime,26f,Ui.WHITE,true))
  times.addView(Ui.text(c,if(train.durationSeconds>0)"${train.durationSeconds/60} мин" else "",12f,Ui.muted).apply{gravity=Gravity.END},LinearLayout.LayoutParams(0,-2,1f))
  addView(times);addView(Ui.space(c,10))
  val title=listOf(train.number.takeIf{it.isNotBlank()}?.let{"№ $it"},train.title.takeIf{it.isNotBlank()}).filterNotNull().joinToString(" · ")
  addView(Ui.text(c,title.ifBlank{"Пригородный поезд"},13f,Ui.muted))
  addView(Ui.space(c,16));addView(Ui.divider(c));addView(Ui.space(c,13))
  val bottom=Ui.row(c)
  bottom.addView(Ui.text(c,train.fareLabel,15f,Ui.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
  bottom.addView(Ui.text(c,"Остановки и цена  ›",12f,Ui.blue,true))
  addView(bottom)
  if(track!=null){addView(Ui.space(c,12));addView(Ui.outlineButton(c,if(tracking)"✓ Отслеживается · отменить" else "Отслеживать",track))}
  Ui.interactive(c,this,open)
 }
}
