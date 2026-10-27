package com.darly.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar

class TrainDetailActivity:Activity(){
 companion object {
  fun open(c:Context,train:YandexRaspApi.Train,from:YandexRaspApi.Station,to:YandexRaspApi.Station,date:String){
   c.startActivity(Intent(c,TrainDetailActivity::class.java).putExtra("train",train).putExtra("from",from).putExtra("to",to).putExtra("date",date))
  }
 }
 private lateinit var train:YandexRaspApi.Train
 private lateinit var from:YandexRaspApi.Station
 private lateinit var to:YandexRaspApi.Station
 private var date=""
 private var journey:YandexRaspApi.Journey?=null
 private var error:String?=null
 private var loading=true
 private var wholeRoute=false
 private var request=0
 private fun <T:java.io.Serializable> extra(key:String,type:Class<T>):T? = if(Build.VERSION.SDK_INT>=33)intent.getSerializableExtra(key,type) else {
  @Suppress("DEPRECATION")
  type.cast(intent.getSerializableExtra(key))
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val t=extra("train",YandexRaspApi.Train::class.java)
  val f=extra("from",YandexRaspApi.Station::class.java)
  val destination=extra("to",YandexRaspApi.Station::class.java)
  if(t==null||f==null||destination==null){finish();return}
  train=t;from=f;to=destination;date=intent.getStringExtra("date").orEmpty()
  wholeRoute=savedInstanceState?.getBoolean("whole_route",false)?:false
  if(savedInstanceState!=null){
   @Suppress("DEPRECATION")
   val stored=savedInstanceState.getSerializable("journey") as? YandexRaspApi.Journey
   journey=stored
  }
  if(journey==null)load() else{loading=false;render()}
 }
 override fun onSaveInstanceState(outState:Bundle){outState.putBoolean("whole_route",wholeRoute);outState.putSerializable("journey",journey);super.onSaveInstanceState(outState)}
 override fun onDestroy(){request++;super.onDestroy()}
 private fun load(){
  loading=true;error=null;render()
  val epoch=++request
  val key=getSharedPreferences("rail_schedules",MODE_PRIVATE).getString("api_key","").orEmpty()
  Thread({
   val result=runCatching{YandexRaspApi.journey(key,train,from,to,date)}
   runOnUiThread{
    if(isDestroyed||isFinishing||epoch!=request)return@runOnUiThread
    loading=false;result.onSuccess{journey=it}.onFailure{error=it.message?:"Не удалось загрузить остановки"};render()
   }
  },"DARLY-journey").start()
 }
 private fun source(){
  val url=Uri.parse("https://rasp.yandex.ru/search/suburban/").buildUpon().appendQueryParameter("fromId",from.code).appendQueryParameter("toId",to.code).appendQueryParameter("when",date).build()
  runCatching{startActivity(Intent(Intent.ACTION_VIEW,url))}
 }
 private fun render(){
  val body=ModernScreen.create(this,if(train.number.isBlank())"О рейсе" else "Электричка № ${train.number}","Остановки и стоимость"){finish()}
  val summary=Ui.card(this)
  summary.addView(Ui.text(this,"${train.labelTime}  →  ${train.arrivalTime}",32f,Ui.WHITE,true))
  summary.addView(Ui.space(this,10));summary.addView(Ui.text(this,"${from.title} → ${to.title}",16f,Ui.WHITE,true))
  summary.addView(Ui.space(this,8))
  val info=listOf(date,train.durationSeconds.takeIf{it>0}?.let{"${it/60} мин в пути"},train.platform.takeIf{it.isNotBlank()}?.let{"Платформа $it"}).filterNotNull().joinToString(" · ")
  summary.addView(Ui.text(this,info,12f,Ui.muted));body.addView(summary);body.addView(Ui.space(this,16))
  val fare=Ui.card(this)
  fare.addView(Ui.text(this,"СТОИМОСТЬ ПРОЕЗДА",11f,Ui.muted,true));fare.addView(Ui.space(this,10))
  fare.addView(Ui.text(this,train.fareLabel,30f,if(train.fares.isEmpty())Ui.WHITE else Ui.blue,true))
  fare.addView(Ui.space(this,8))
  if(train.fares.isEmpty())fare.addView(Ui.text(this,"Яндекс не передал стоимость для этого рейса. Проверь тариф у перевозчика или в Яндекс Расписаниях.",13f,Ui.muted))
  else {
   if(train.fares.size>1)train.fares.forEach{ticket->fare.addView(Ui.text(this,"${ticket.name.ifBlank{"Билет"}} · ${ticket.label}",14f,Ui.WHITE))}
   fare.addView(Ui.text(this,"На выбранном участке. Тариф предоставлен Яндекс Расписаниями; льготы и условия покупки уточняй у перевозчика.",12f,Ui.muted))
  }
  fare.addView(Ui.space(this,12));fare.addView(Ui.outlineButton(this,"Проверить у источника ↗"){source()})
  body.addView(fare);body.addView(Ui.space(this,24))
  val heading=Ui.row(this)
  heading.addView(Ui.text(this,"Остановки",21f,Ui.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
  if(journey!=null)heading.addView(Ui.text(this,if(wholeRoute)"Весь маршрут" else "Ваш участок",12f,Ui.blue,true))
  body.addView(heading);body.addView(Ui.space(this,14))
  if(loading){
   val card=Ui.card(this);card.addView(Ui.text(this,"Загружаем остановки…",15f,Ui.WHITE,true));card.addView(Ui.space(this,12))
   card.addView(ProgressBar(this).apply{isIndeterminate=true;indeterminateTintList=android.content.res.ColorStateList.valueOf(Ui.blue)},LinearLayout.LayoutParams(Ui.dp(this,28),Ui.dp(this,28)));body.addView(card)
  }else if(error!=null){
   val card=Ui.card(this);card.addView(Ui.text(this,error!!,14f,Ui.muted));card.addView(Ui.space(this,14));card.addView(Ui.button(this,"Повторить загрузку"){load()});body.addView(card)
  }else journey?.let{details->
   val bounds=RailJourneyRules.segment(details.stops.map{it.code},from.code,to.code)
   if(bounds==null)body.addView(Ui.text(this,"Участок не удалось выделить — показан полный маршрут по данным Яндекса.",12f,Ui.muted))
   val visible=details.stops.withIndex().filter{wholeRoute || bounds==null || it.index in bounds}
   val card=Ui.card(this)
   if(visible.isEmpty())card.addView(Ui.text(this,"Для этого рейса остановки не переданы.",14f,Ui.muted))
   visible.forEachIndexed{position,entry->
    val stop=entry.value
    val endpoint=stop.code==from.code||stop.code==to.code
    val row=Ui.row(this).apply{gravity=Gravity.TOP;minimumHeight=Ui.dp(this@TrainDetailActivity,78)}
    val times=Ui.column(this)
    times.addView(Ui.text(this,RailJourneyRules.time(stop.departure?:stop.arrival),17f,if(endpoint)Ui.blue else Ui.WHITE,true))
    if(stop.arrival!=null&&stop.departure!=null&&RailJourneyRules.time(stop.arrival)!=RailJourneyRules.time(stop.departure))times.addView(Ui.text(this,"↳ ${RailJourneyRules.time(stop.arrival)}",11f,Ui.muted))
    row.addView(times,LinearLayout.LayoutParams(Ui.dp(this,64),-2))
    row.addView(Timeline(this,position==0,position==visible.lastIndex,endpoint),LinearLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,78)))
    val label=Ui.column(this)
    label.addView(Ui.text(this,stop.title,15f,Ui.WHITE,endpoint))
    val secondary=when(stop.code){from.code->"Посадка";to.code->"Выход";else->stop.platform.takeIf{it.isNotBlank()}?.let{"Платформа $it"}.orEmpty()}
    if(secondary.isNotBlank()){label.addView(Ui.space(this,5));label.addView(Ui.text(this,secondary,12f,if(endpoint)Ui.blue else Ui.muted))}
    row.addView(label,LinearLayout.LayoutParams(0,-2,1f));card.addView(row)
   }
   body.addView(card);body.addView(Ui.space(this,12))
   if(bounds!=null)body.addView(Ui.outlineButton(this,if(wholeRoute)"Только мой участок" else "Показать весь маршрут · ${details.stops.size} станций"){wholeRoute=!wholeRoute;render()})
   val carrier=details.carrier.ifBlank{train.carrier}
   if(carrier.isNotBlank()){body.addView(Ui.space(this,18));body.addView(Ui.text(this,"Перевозчик · $carrier",12f,Ui.muted))}
  }
  body.addView(Ui.space(this,20));body.addView(Ui.text(this,YandexRaspApi.ATTRIBUTION,11f,Ui.muted))
 }
 private class Timeline(c:Context,private val first:Boolean,private val last:Boolean,private val selected:Boolean):View(c){
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  override fun onDraw(canvas:Canvas){
   val x=width/2f;val y=Ui.dp(context,10).toFloat()
   paint.color=0x445c7692;paint.strokeWidth=Ui.dp(context,2).toFloat()
   if(!first)canvas.drawLine(x,0f,x,y,paint)
   if(!last)canvas.drawLine(x,y,x,height.toFloat(),paint)
   paint.color=if(selected)Ui.blue else 0xff7f94ac.toInt();canvas.drawCircle(x,y,Ui.dp(context,if(selected)5 else 3).toFloat(),paint)
  }
 }
}
