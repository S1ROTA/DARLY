package com.darly.alarm

import android.app.*
import android.content.*
import android.os.*
import android.view.*
import android.widget.*

/** Operates only on QA-owned data and views; never injects events into other apps. */
class WakeChecksInstrumentation:Instrumentation(){
 private var mode="ui"
 private lateinit var activity:Activity
 private val result=Bundle()
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);mode=arguments?.getString("mode")?:"ui";start()}
 private fun ui(action:()->Unit){var problem:Throwable?=null;runOnMainSync{try{action()}catch(e:Throwable){problem=e}};problem?.let{throw it}}
 private fun views(root:View):List<View> = listOf(root)+if(root is android.view.ViewGroup)(0 until root.childCount).flatMap{views(root.getChildAt(it))}else emptyList()
 private fun nodes()=views(activity.window.decorView)
 private fun texts()=nodes().filterIsInstance<TextView>().map{it.text.toString()}
 private fun click(label:String){ui{
  val all=nodes();var node=all.firstOrNull{it.contentDescription?.toString()==label&&it.isClickable}?:all.first{it is TextView&&it.text.toString()==label}
  while(!node.isClickable&&node.parent is View)node=node.parent as View
  check(node.performClick()){"Cannot click $label"}
 };waitForIdleSync()}
 private fun until(timeout:Long=10000,condition:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(condition())return;Thread.sleep(40)};error("Timed out: $mode")}
 private fun shot(name:String){Thread.sleep(700);uiAutomation.takeScreenshot()?.let{b->java.io.File(targetContext.filesDir,"qa-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun status(message:String){sendStatus(0,Bundle().apply{putString("stream","$message\n")})}
 override fun onStart(){
  check(targetContext.packageName.endsWith(".qa"))
  val prefs=targetContext.getSharedPreferences("darly",0)
  val backupAlarms=AlarmStore.all(targetContext)
  val oldMoving=prefs.getBoolean(RingPreferences.MOVING_SNOOZE,true)
  val oldPower=prefs.getBoolean(RingPreferences.POWER_SNOOZE,false)
  val oldAnimations=prefs.getBoolean("animations",true)
  val id=(backupAlarms.maxOfOrNull{it.id}?:0)+1000
  var code=Activity.RESULT_CANCELED
  try{
   check(!RingService.active(targetContext)){"An existing QA alarm is active"}
   prefs.edit().putBoolean(RingPreferences.MOVING_SNOOZE,true).putBoolean("animations",true).putBoolean(RingPreferences.POWER_SNOOZE,mode=="power").commit()
   activity=startActivitySync(Intent().setClassName(targetContext.packageName,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync()
   if(mode=="ui"){
    click("Электрички")
    var from="";ui{from=texts().firstOrNull{it=="Москва (Ярославский вокзал)"}?:"Выбрать станцию"}
    val opened=SystemClock.elapsedRealtime();click(from);result.putLong("picker_open_ms",SystemClock.elapsedRealtime()-opened)
    val beatHandler=Handler(Looper.getMainLooper());var lastBeat=SystemClock.uptimeMillis();var maxGap=0L
    val beat=object:Runnable{override fun run(){val now=SystemClock.uptimeMillis();maxGap=maxOf(maxGap,now-lastBeat);lastBeat=now;beatHandler.postDelayed(this,16)}}
    beatHandler.post(beat)
    val start=SystemClock.elapsedRealtime()
    ui{nodes().filterIsInstance<EditText>().first{it.hint?.toString()=="Поиск станции или платформы"}.setText("орел")}
    result.putLong("query_dispatch_ms",SystemClock.elapsedRealtime()-start)
    until{var ready=false;ui{ready=texts().any{it.startsWith("Орёл")||it.startsWith("Орел")}};ready}
    beatHandler.removeCallbacks(beat);result.putLong("first_search_ms",SystemClock.elapsedRealtime()-start);result.putLong("ui_heartbeat_max_gap_ms",maxGap)
    click("‹");click("Будильники");click("＋ Будильник")
    var dateCount=0;ui{dateCount=texts().count{Regex("(?:Пн|Вт|Ср|Чт|Пт|Сб|Вс)\\n[0-9]{1,2}\\.[0-9]{2}").matches(it)}}
    check(dateCount==7){"Weekday dates are missing"};shot("dates-3.3")
    click("‹");click("Настройки")
    ui{check(nodes().any{it.contentDescription=="Движущаяся кнопка «Отложить»"});check(nodes().any{it.contentDescription=="Отложить кнопкой питания"})}
   }
   AlarmStore.save(targetContext,backupAlarms+DAlarm(id,7,0,0,"",title="Проверка DARLY",vibrate=false,fade=0))
   val monitor=addMonitor(RingActivity::class.java.name,null,false)
   if(mode=="ui")ui{activity.startForegroundService(Intent(activity,RingService::class.java).putExtra("id",id))}
   else{
    val manager=targetContext.getSystemService(AlarmManager::class.java)
    check(Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms()){"Exact alarm access required"}
    val pending=PendingIntent.getBroadcast(targetContext,id,Intent(targetContext,AlarmReceiver::class.java).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    ui{activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
    status("LOCK_SCREEN_NOW_WAITING_FOR_PHYSICAL_POWER_BUTTON")
    until(120000){!targetContext.getSystemService(PowerManager::class.java).isInteractive}
    manager.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis()+5000,null),pending)
    status("ALARM_SCHEDULED_IN_5_SECONDS")
   }
   activity=waitForMonitorWithTimeout(monitor,20000)?:error("Ring screen did not open")
   removeMonitor(monitor);waitForIdleSync();Thread.sleep(1000)
   check(RingService.active(targetContext)){"Ring screen lost active state"}
   check(targetContext.getSystemService(PowerManager::class.java).isInteractive){"Alarm did not wake screen"}
   result.putBoolean("screen_awake",true)
   result.putBoolean("keyguard_locked",targetContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
   if(mode=="ui"){
    var x=0f;var y=0f;var moved=false
    ui{val b=nodes().filterIsInstance<TextView>().first{it.text.toString().startsWith("Отложить ·")};x=b.translationX;y=b.translationY}
    Thread.sleep(1700)
    ui{
     val b=nodes().filterIsInstance<TextView>().first{it.text.toString().startsWith("Отложить ·")};val host=b.parent as View
     moved=kotlin.math.abs(x-b.translationX)+kotlin.math.abs(y-b.translationY)>1
     check(b.translationX>=0&&b.translationY>=0&&b.translationX+b.width<=host.width+1&&b.translationY+b.height<=host.height+1){"Moving button left screen"}
    }
    check(moved){"Snooze did not move"};shot("ring-3.3")
    prefs.edit().putBoolean(RingPreferences.MOVING_SNOOZE,false).commit()
    val recreated=addMonitor(RingActivity::class.java.name,null,false);ui{activity.recreate()}
    activity=waitForMonitorWithTimeout(recreated,10000)?:error("Ring did not recreate");removeMonitor(recreated);waitForIdleSync();Thread.sleep(600)
    ui{val b=nodes().filterIsInstance<TextView>().first{it.text.toString().startsWith("Отложить ·")};x=b.translationX;y=b.translationY}
    Thread.sleep(1600)
    ui{val b=nodes().filterIsInstance<TextView>().first{it.text.toString().startsWith("Отложить ·")};check(x==b.translationX&&y==b.translationY){"Disabled motion still moved"}}
    click("Отложить · 5 мин");until{!RingService.active(targetContext)}
    check(AlarmStore.snoozeUntil(targetContext,id)>System.currentTimeMillis()){"Snooze not scheduled"}
   }else{
    shot("locked-ring-3.3");status("POWER_BUTTON_TEST_SLEEP_NOW")
    if(mode=="power"){
     until(15000){!RingService.active(targetContext)}
     check(AlarmStore.snoozeUntil(targetContext,id)>System.currentTimeMillis()){"Power snooze missing"}
    }else{
     until(90000){!targetContext.getSystemService(PowerManager::class.java).isInteractive}
     Thread.sleep(1200);check(RingService.active(targetContext)&&AlarmStore.snoozeUntil(targetContext,id)==0L){"Disabled power snooze interrupted alarm"}
     click("Остановить будильник");until{!RingService.active(targetContext)}
    }
   }
   result.putString("stream","Wake checks passed ($mode)\n");code=Activity.RESULT_OK
  }catch(e:Throwable){result.putString("stream","FAILED ($mode): ${e.message}\n")}
  finally{
   if(RingService.activeId(targetContext)==id)targetContext.stopService(Intent(targetContext,RingService::class.java))
   AlarmStore.cancelSnooze(targetContext,id);AlarmStore.cancel(targetContext,DAlarm(id,7,0,0,""));AlarmStore.save(targetContext,backupAlarms)
   prefs.edit().putBoolean(RingPreferences.MOVING_SNOOZE,oldMoving).putBoolean(RingPreferences.POWER_SNOOZE,oldPower).putBoolean("animations",oldAnimations).commit()
  }
  finish(code,result)
 }
}
