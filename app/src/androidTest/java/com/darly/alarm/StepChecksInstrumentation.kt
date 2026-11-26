package com.darly.alarm

import android.app.*
import android.content.*
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import java.time.LocalDate

/** Synthetic data is limited to QA and restored before finishing. Sensor mode never fabricates steps. */
class StepChecksInstrumentation:Instrumentation(){
 private var mode="ui"
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);mode=arguments?.getString("mode")?:"ui";start()}
 override fun onStart(){
  val result=Bundle();var code=Activity.RESULT_CANCELED
  if(!targetContext.packageName.endsWith(".qa")){finish(code,result);return}
  val prefs=targetContext.getSharedPreferences("activity_steps",0);val backup=prefs.all
  var modified=false
  var activity:Activity?=null
  fun ui(action:()->Unit){var problem:Throwable?=null;runOnMainSync{try{action()}catch(e:Throwable){problem=e}};problem?.let{throw it}}
  fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
  fun texts()=views(activity!!.window.decorView).filterIsInstance<TextView>().map{it.text.toString()}
  fun click(label:String){ui{
   val nodes=views(activity!!.window.decorView);var v=nodes.firstOrNull{it.contentDescription?.toString()==label&&it.isClickable}?:nodes.first{it is TextView&&it.text.toString()==label}
   while(!v.isClickable&&v.parent is View)v=v.parent as View
   check(v.performClick())
  };waitForIdleSync()}
  try{
   if(mode=="ui"){check(!StepsService.active&&!StepStore.enabled(targetContext)&&StepStore.ledger(targetContext).days.isEmpty()){"Synthetic checks require QA without activity history"}}
   activity=startActivitySync(Intent().setClassName(targetContext.packageName,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();click("Активность")
   if(mode=="ui"){prefs.edit().clear().commit();modified=true}
   ui{check("Челленджи" in texts()&&"За последние 7 дней" in texts()&&"История" in texts()){"Activity sections missing"}}
   if(mode=="permission"||mode=="sensor"){
    if(!StepsService.active){if(mode=="sensor")ui{StepsService.start(activity!!)}else click(if(StepStore.enabled(targetContext))"Продолжить учёт" else "Начать отслеживание")}
    val deadline=android.os.SystemClock.elapsedRealtime()+120000
    while(!StepsService.active&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(200)
    check(StepsService.active){"Physical activity permission or service start missing"}
    val sampleDeadline=android.os.SystemClock.elapsedRealtime()+30000
    while(StepStore.ledger(targetContext).counter<0&&android.os.SystemClock.elapsedRealtime()<sampleDeadline)Thread.sleep(200)
    val ledger=StepStore.ledger(targetContext)
    check(ledger.counter>=0){"No real step counter sample received"}
    if(mode=="sensor"){
     val walkDeadline=android.os.SystemClock.elapsedRealtime()+60000
     while((StepStore.ledger(targetContext).days.firstOrNull{it.date==LocalDate.now().toString()}?.steps?:0)==0L&&android.os.SystemClock.elapsedRealtime()<walkDeadline)Thread.sleep(250)
     val actual=StepStore.ledger(targetContext).days.firstOrNull{it.date==LocalDate.now().toString()}?.steps?:0
     check(actual>0){"No physical steps detected yet"};result.putLong("actual_steps_today",actual)
    }
    val notification=targetContext.getSystemService(NotificationManager::class.java).activeNotifications.first{it.id==StepsService.ID}.notification
    check(notification.visibility==Notification.VISIBILITY_PUBLIC)
    result.putString("stream","Real hardware counter active, baseline received, foreground step notification present\n")
   }else{
    val today=LocalDate.now().toString();StepStore.setGoal(targetContext,5000);StepStore.enable(targetContext)
    StepStore.record(targetContext,1000,999,1,today);StepStore.record(targetContext,4500,999,2,today)
    waitForIdleSync();Thread.sleep(200)
    check(StepStore.ledger(targetContext).days.single{it.date==today}.steps==3500L)
    click("Принять челлендж");waitForIdleSync()
    ui{check(texts().any{it.contains("Выполнен ✓")}){"Challenge did not complete"}}
    val notification=StepNotification.build(targetContext)
    check(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("500"))
    check(notification.extras.getInt(Notification.EXTRA_PROGRESS)==3500&&notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX)==5000)
    check(notification.visibility==Notification.VISIBILITY_PUBLIC&&notification.actions.any{it.title=="Пауза"})
    check(notification.contentIntent.creatorPackage==targetContext.packageName)
    ui{
     val root=activity!!.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
     views(root).filterIsInstance<ScrollView>().forEach{it.alpha=1f}
     val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
     java.io.File(targetContext.filesDir,"qa-activity-3.5.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    result.putString("stream","Activity navigation, daily ledger, goal, challenge, history and step notification checks passed\n")
   }
   code=Activity.RESULT_OK
  }catch(e:Throwable){result.putString("stream","FAILED ($mode): ${e.message}\n")}
  finally{if(modified){
   targetContext.stopService(Intent(targetContext,StepsService::class.java))
   val editor=prefs.edit().clear()
   backup.forEach{(k,v)->when(v){is String->editor.putString(k,v);is Boolean->editor.putBoolean(k,v);is Int->editor.putInt(k,v);is Long->editor.putLong(k,v);is Float->editor.putFloat(k,v)}}
   editor.commit()
  }}
  finish(code,result)
 }
}
