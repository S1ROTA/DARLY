package com.darly.alarm

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import java.time.*

/** Deterministic UI and notification checks, without network or access to other apps. */
class RailTrackingInstrumentation:Instrumentation(){
 private var live=false
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);live=arguments?.getString("mode")=="live";start()}
 override fun onStart(){
  val output=Bundle();var code=Activity.RESULT_CANCELED
  if(!targetContext.packageName.endsWith(".qa")){finish(code,output);return}
  val prefs=targetContext.getSharedPreferences("rail_tracking",0);val backup=prefs.getString("journey",null)
  var activity:Activity?=null
  fun ui(block:()->Unit){var error:Throwable?=null;runOnMainSync{try{block()}catch(e:Throwable){error=e}};error?.let{throw it}}
  fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
  fun texts()=views(activity!!.window.decorView).filterIsInstance<TextView>().map{it.text.toString()}
  fun click(label:String){ui{var v=views(activity!!.window.decorView).first{it is TextView&&it.text.toString()==label};while(!v.isClickable&&v.parent is View)v=v.parent as View;check(v.performClick())};waitForIdleSync()}
  try{
   activity=startActivitySync(Intent().setClassName(targetContext.packageName,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync()
   if(live){
    ui{
     fun field(name:String,value:Any){MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)}
     field("railFrom",YandexRaspApi.Station("s2000002","Москва (Ярославский вокзал)"));field("railTo",YandexRaspApi.Station("s9600681","Мытищи"))
     field("railDate",LocalDate.now().toString())
     MainActivity::class.java.getDeclaredMethod("railSearch",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(activity,false)
    }
    val end=android.os.SystemClock.elapsedRealtime()+90000
    var loading=true
    while(loading&&android.os.SystemClock.elapsedRealtime()<end){Thread.sleep(100);ui{loading=MainActivity::class.java.getDeclaredField("railLoading").apply{isAccessible=true}.getBoolean(activity)}}
    check(!loading){"Live query timeout"}
    ui{
     val error=MainActivity::class.java.getDeclaredField("railError").apply{isAccessible=true}.get(activity)
     check(error==null){"Live query failed: $error"}
     @Suppress("UNCHECKED_CAST")
     val trains=MainActivity::class.java.getDeclaredField("railTrains").apply{isAccessible=true}.get(activity) as List<YandexRaspApi.Train>
     val now=System.currentTimeMillis();val upcoming=trains.count{it.departureAt>=now};val past=trains.size-upcoming
     val cards=views(activity!!.window.decorView).count{it.contentDescription?.toString()?.startsWith("Рейс ")==true}
     check(cards==upcoming){"Past trains shown by default"}
     output.putString("stream","Live timetable passed: ${trains.size} loaded, $upcoming upcoming, $past past hidden\n")
    }
    finish(Activity.RESULT_OK,output);return
   }
   val now=System.currentTimeMillis()
   fun train(at:Long,name:String)=YandexRaspApi.Train(name,name,Instant.ofEpochMilli(at).atOffset(ZoneOffset.UTC).toString(),Instant.ofEpochMilli(at+1800000).atOffset(ZoneOffset.UTC).toString(),at,1800,uid=name)
   val past=train(now-600000,"Ушедший тестовый рейс");val future=train(now+600000,"Ближайший тестовый рейс")
   RailTracking.clear(targetContext)
   ui{
    fun field(name:String,value:Any){MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)}
    field("railFrom",YandexRaspApi.Station("s2000002","Москва"));field("railTo",YandexRaspApi.Station("s9600681","Мытищи"))
    field("railTrains",listOf(past,future));field("railHasSearched",true);field("railSearchTotal",2);field("railNextOffset",50)
    MainActivity::class.java.getDeclaredMethod("railPage").apply{isAccessible=true}.invoke(activity)
    check(texts().none{it.contains("Ушедший тестовый рейс")});check(texts().any{it.contains("Ближайший тестовый рейс")})
   }
   click("Развернуть ушедшие электрички · 1")
   ui{check(texts().any{it.contains("Ушедший тестовый рейс")})}
   click("Свернуть ушедшие электрички");click("Отслеживать")
   check(RailTracking.selected(targetContext,future))
   check(!RailTracking.selected(targetContext,future,YandexRaspApi.Station("s2000002","Москва"),YandexRaspApi.Station("other","Другой участок")))
   val manager=targetContext.getSystemService(NotificationManager::class.java)
   val notification=manager.activeNotifications.first{it.id==9092}.notification
   check(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()=="Москва → Мытищи")
   check(notification.actions.any{it.title=="Не отслеживать"})
   ui{check("✓ Отслеживается · отменить" in texts())}
   Thread.sleep(800)
   uiAutomation.takeScreenshot()?.let{b->java.io.File(targetContext.filesDir,"qa-rail-3.4.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
   notification.actions.first{it.title=="Не отслеживать"}.actionIntent.send()
   Thread.sleep(300);check(!RailTracking.selected(targetContext,future))
   check(manager.activeNotifications.none{it.id==9092})
   output.putString("stream","Upcoming/past toggle, tracking button, route notification and cancellation passed\n");code=Activity.RESULT_OK
  }catch(e:Throwable){output.putString("stream","FAILED: ${e.message}\n")}
  finally{if(!live){RailTracking.clear(targetContext);if(backup!=null){prefs.edit().putString("journey",backup).commit();RailTracking.sync(targetContext)}}}
  finish(code,output)
 }
}
