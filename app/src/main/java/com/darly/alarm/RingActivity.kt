package com.darly.alarm

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class RingActivity:Activity(){
 private val handler=Handler(Looper.getMainLooper())
 private var pulse:AlarmPulseView?=null
 private var moving:MovingSnoozeLayout?=null
 private var clock:TextView?=null
 private var registered=false
 private var displayedId=-1
 private val changed=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(!RingService.active(this@RingActivity))finish()}}
 private val tick=object:Runnable{override fun run(){clock?.text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date());handler.postDelayed(this,1000)}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);if(displayedId!=RingService.activeId(this))recreate()}
 override fun onResume(){
  super.onResume();if(!RingService.active(this)){finish();return}
  pulse?.start();moving?.setRunning(true);handler.post(tick)
 }
 override fun onPause(){handler.removeCallbacks(tick);pulse?.stop();moving?.setRunning(false);super.onPause()}
 override fun onDestroy(){handler.removeCallbacksAndMessages(null);if(registered)unregisterReceiver(changed);super.onDestroy()}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  if(!RingService.active(this)){finish();return}
  if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true)}else{
   @Suppress("DEPRECATION")
   window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
  }
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
  window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
  Ui.applyTheme(this);window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=0xff0b1423.toInt()
  @Suppress("DEPRECATION")
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
  if(Build.VERSION.SDK_INT>=29)window.isStatusBarContrastEnforced=false
  val root=FrameLayout(this)
  root.addView(ImageView(this).apply{setImageDrawable(Ui.wallpaper(this@RingActivity));scaleType=ImageView.ScaleType.CENTER_CROP;Ui.animateWallpaper(this@RingActivity,this)},FrameLayout.LayoutParams(-1,-1))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xb30b1423.toInt(),0xe50b1423.toInt(),0xff0b1423.toInt()))},FrameLayout.LayoutParams(-1,-1))
  val content=Ui.column(this).apply{Ui.pad(this@RingActivity,this,24,18)}
  root.addView(content,FrameLayout.LayoutParams(-1,-1))
  if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener{_,w->val s=w.getInsets(WindowInsets.Type.systemBars());content.setPadding(Ui.dp(this,24),s.top+Ui.dp(this,18),Ui.dp(this,24),s.bottom+Ui.dp(this,18));w}else content.fitsSystemWindows=true
  displayedId=RingService.activeId(this)
  val alarm=AlarmStore.all(this).firstOrNull{it.id==displayedId}
  val hero=Ui.column(this).apply{gravity=Gravity.CENTER}
  val scroll=ScrollView(this).apply{isFillViewport=true;isVerticalScrollBarEnabled=false;addView(hero)}
  content.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  hero.addView(Ui.text(this,"DARLY  /  ПОРА ПРОСЫПАТЬСЯ",12f,Ui.gold,true).apply{gravity=Gravity.CENTER})
  pulse=AlarmPulseView(this).apply{contentDescription="Звонит будильник"}
  hero.addView(pulse,LinearLayout.LayoutParams(-1,Ui.dp(this,144)))
  clock=Ui.text(this,"",64f,Ui.WHITE,true).apply{gravity=Gravity.CENTER}
  hero.addView(clock);hero.addView(Ui.space(this,12))
  hero.addView(Ui.text(this,alarm?.title?:"Будильник",23f,Ui.WHITE,true).apply{gravity=Gravity.CENTER})
  hero.addView(Ui.space(this,8));hero.addView(Ui.text(this,SimpleDateFormat("EEEE, d MMMM",Locale("ru")).format(Date()),14f,Ui.muted).apply{gravity=Gravity.CENTER})
  val snooze=Ui.button(this,"Отложить · ${alarm?.snooze?:5} мин"){startService(Intent(this,RingService::class.java).setAction("SNOOZE"))}
  moving=MovingSnoozeLayout(this,snooze,RingPreferences.moving(this))
  content.addView(moving,LinearLayout.LayoutParams(-1,Ui.dp(this,if(resources.configuration.screenHeightDp<480)84 else 164)))
  content.addView(Ui.outlineButton(this,"Остановить будильник"){startService(Intent(this,RingService::class.java).setAction("STOP"))})
  content.addView(Ui.space(this,10));content.addView(Ui.text(this,"Остановить — выключить сигнал полностью",11f,Ui.muted).apply{gravity=Gravity.CENTER})
  setContentView(root)
  if(Build.VERSION.SDK_INT>=33)registerReceiver(changed,IntentFilter(AlarmStore.ACTION_CHANGED),Context.RECEIVER_NOT_EXPORTED)else registerLegacy()
  registered=true
 }
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 private fun registerLegacy(){registerReceiver(changed,IntentFilter(AlarmStore.ACTION_CHANGED))}
}
