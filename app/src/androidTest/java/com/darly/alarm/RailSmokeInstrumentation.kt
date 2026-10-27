package com.darly.alarm

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

/** Runs against the isolated QA package, with actual Android JsonReader and storage APIs. */
class RailSmokeInstrumentation:Instrumentation(){
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
 override fun onStart(){
  val output=Bundle()
  try{
   check(targetContext.packageName.endsWith(".qa")){"Smoke checks require isolated QA package"}
   val fixture="""{"countries":[{"regions":[{"settlements":[{"stations":[{"codes":{"yandex_code":"s1000"},"title":"Орёл","transport_type":"train"},{"title":"Платформа","transport_type":"suburban","codes":{"yandex_code":"s1001"}},{"title":"Автобус","transport_type":"bus","codes":{"yandex_code":"s1002"}}],"title":"Город"}],"title":"Область"}]}]}"""
   val stations=YandexRaspApi.readStations(fixture.reader())
   check(stations.size==2){"Rail catalog dropped train station or accepted a bus"}
   check(stations.all{it.settlement=="Город" && it.region=="Область"}){"Metadata depends on JSON field order"}
   check(RailStationCache.find(stations,"орел").single().code=="s1000"){"Yo search failed"}
   check(RailStationCache.find(stations,"область орел").single().code=="s1000"){"Multi-word regional search failed"}
   val other=listOf(YandexRaspApi.Station("s2000","Новая"),YandexRaspApi.Station("s2001","Другая"))
   check(RailStationCache.find(other,"новая").single().code=="s2000"){"Same-size index reused for a different list"}
   val real=java.io.File(targetContext.filesDir,"catalog-fixture.json")
   if(real.isFile){
    val all=real.reader(Charsets.UTF_8).use{YandexRaspApi.readStations(it)}
    check(all.size>1000){"Real catalog unexpectedly empty"}
    output.putInt("rail_stations",all.size)
   }
   RailStationCache.replace(targetContext,stations)
   check(RailStationCache.all(targetContext)==stations){"Cache round trip lost metadata"}
   targetContext.getSharedPreferences("darly_runtime",0).edit().putBoolean("ringing",true).commit()
   check(!RingService.active(targetContext)){"Persisted stale ringing state survived process death"}
   targetContext.getSharedPreferences("darly_runtime",0).edit().clear().commit()
   StopwatchStore.reset(targetContext);StopwatchStore.start(targetContext);StopwatchStore.stop(targetContext)
   check(StopwatchStore.get(targetContext).marks.size==1){"Stopwatch split missing"}
   StopwatchStore.pause(targetContext)
   check(!StopwatchStore.get(targetContext).running){"Stopwatch did not pause"}
   StopwatchStore.reset(targetContext)
   val launch=android.content.Intent().setClassName(targetContext.packageName,"com.darly.alarm.MainActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
   var activity=startActivitySync(launch)
   waitForIdleSync()
   fun views(root:android.view.View):List<android.view.View> = listOf(root)+if(root is android.view.ViewGroup)(0 until root.childCount).flatMap{views(root.getChildAt(it))}else emptyList()
   fun click(label:String){runOnMainSync{
    val root=activity.window.decorView
    val candidates=views(root)
    val node=candidates.firstOrNull{it.contentDescription?.toString()==label && it.isClickable}
     ?:candidates.firstOrNull{it is android.widget.TextView && it.text.toString()==label}
     ?:error("Missing control: $label")
    var clickable=node
    while(!clickable.isClickable && clickable.parent is android.view.View)clickable=clickable.parent as android.view.View
    check(clickable.performClick()){"Control did not click: $label"}
   };waitForIdleSync()}
   fun query(value:String){runOnMainSync{
    val edit=views(activity.window.decorView).filterIsInstance<android.widget.EditText>().first{it.hint?.toString()=="Поиск станции или платформы"}
    edit.setText(value)
   };waitForIdleSync()}
   click("Электрички");click("Выбрать станцию");query("орел");click(stations[0].label)
   click("Выбрать станцию");query("плат");click(stations[1].label)
   val selection=targetContext.getSharedPreferences("rail_schedules",0)
   check(selection.getString("selected_from","")!!.contains("s1000")){"Origin selection was not persisted"}
   check(selection.getString("selected_to","")!!.contains("s1001")){"Destination selection was not persisted"}
   runOnMainSync{activity.finish()};waitForIdleSync()
   activity=startActivitySync(launch);waitForIdleSync()
   click("Электрички")
   runOnMainSync{
    val texts=views(activity.window.decorView).filterIsInstance<android.widget.TextView>().map{it.text.toString()}
    check("Орёл" in texts && "Платформа" in texts){"Route lost after reopening"}
   }
   runOnMainSync{activity.finish()};waitForIdleSync()
   // Leave QA with the real catalog when a diagnostic fixture was supplied.
   if(real.isFile){RailStationCache.replace(targetContext,real.reader(Charsets.UTF_8).use{YandexRaspApi.readStations(it)})}
   selection.edit().remove("selected_from").remove("selected_to").commit()
   if(real.isFile){
    activity=startActivitySync(launch);waitForIdleSync();click("Электрички")
    click("Выбрать станцию");query("москва ярославский")
    val origin=RailStationCache.all(targetContext).first{it.code=="s2000002"}
    click(origin.label)
    click("Выбрать станцию");query("мытищи")
    val destination=RailStationCache.find(RailStationCache.all(targetContext),"мытищи").first{it.code=="s9600681"}
    click(destination.label)
    click("Найти электрички")
    val deadline=android.os.SystemClock.elapsedRealtime()+45000
    var complete=false
    while(android.os.SystemClock.elapsedRealtime()<deadline && !complete){
     waitForIdleSync()
     runOnMainSync{complete=views(activity.window.decorView).filterIsInstance<android.widget.TextView>().any{it.text.toString()=="Отправления" || it.text.toString()=="Не удалось загрузить расписание"}}
     if(!complete)Thread.sleep(250)
    }
    check(complete){"Live schedule request did not finish"}
    runOnMainSync{check(views(activity.window.decorView).filterIsInstance<android.widget.TextView>().none{it.text.toString()=="Не удалось загрузить расписание"}){"Live schedule request failed"}}
    uiAutomation.takeScreenshot()?.let{bitmap->
     java.io.File(targetContext.filesDir,"qa-schedule.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
     bitmap.recycle()
    }
   }
   output.putString("stream","Rail parser, region metadata, search and cache checks passed\n")
   finish(Activity.RESULT_OK,output)
  }catch(e:Exception){output.putString("stream","FAILED: ${e.message}\n");finish(Activity.RESULT_CANCELED,output)}
 }
}
