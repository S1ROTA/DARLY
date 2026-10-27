package com.darly.alarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.HapticFeedbackConstants
import android.content.res.ColorStateList
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:Activity(){
 private lateinit var body:LinearLayout
 private var screen="home"
 private var railFrom:YandexRaspApi.Station?=null
 private var railTo:YandexRaspApi.Station?=null
 private var railDate=java.time.LocalDate.now().toString()
 private var railTrains:List<YandexRaspApi.Train> = emptyList()
 private var railSearchTotal=0
 private var railNextOffset=0
 private var railIntervalCount=0
 private var railError:String?=null
 private var railCatalogError:String?=null
 private var railLoading=false
 private var railCatalogLoading=false
 private var railHasSearched=false
 private var railChoosingOrigin=true
 private var railSearchEpoch=0
 private var selectedSound=""
 private var clockView:TextView?=null
 private var showing=false
 private var choosing=false
 private var selectionButton:TextView?=null
 private val countdownLabels=mutableListOf<Pair<TextView,Long>>()
 private val selectedAlarms=linkedSetOf<Int>()
 private val selectedSeries=linkedSetOf<Int>()
 private val timerLabels=mutableMapOf<Int,TextView>()
 private var currentTimers:List<DTimer> = emptyList()
 private var activeStopwatch:StopwatchState?=null
 private var stopwatchClock:TextView?=null
 private var tabSwipeX=0f
 private var backgroundPickerPending=false
 private var tabSwipeY=0f
 private val toolsTick=object:Runnable{override fun run(){
  if(!showing)return
  when(screen){
   "timers"->{currentTimers.forEach{t->timerLabels[t.id]?.text=duration(t.remaining())};handler.postDelayed(this,450)}
   "stopwatch"->{stopwatchClock?.text=duration((activeStopwatch?.elapsed()?:0L),true);handler.postDelayed(this,90)}
  }
 }}
 private var lastPermission:Boolean?=null
 private val handler=Handler(Looper.getMainLooper())
 private val clockTick=object:Runnable{override fun run(){
  if(screen=="home" && showing){
   val now=System.currentTimeMillis()
   val displayed=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(now))
   if(clockView?.text?.toString()!=displayed)clockView?.text=displayed
   countdownLabels.forEach{(view,at)->
    val remaining=CountdownText.until(at,now)
    if(view.text.toString()!=remaining)view.text=remaining
   }
   // Minute ticks normally; precise seconds only in the last minute before a signal.
   // Only existing TextViews change — no card or wallpaper gets rebuilt.
   val finalMinute=countdownLabels.any{(_,at)->at>now && at-now<60000L}
   val interval=if(finalMinute)(1000-now%1000).coerceAtLeast(100L)
    else (60000-now%60000).coerceAtLeast(1000L)
   handler.postDelayed(this,interval)
  }
 }}
 private val refresh=Runnable{when(screen){"home"->home();"timers"->timersPage()}}
 private val changes=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
  if(RingService.active(this@MainActivity)){openCurrentScreen();return}
  if(screen=="home"||screen=="timers"){handler.removeCallbacks(refresh);handler.postDelayed(refresh,100)}
 }}
 private var registered=false
 /** On Android 13+ app-only events must be registered as non-exported. */
 private fun registerChangesReceiver(){
  val filter=IntentFilter(AlarmStore.ACTION_CHANGED)
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
   registerReceiver(changes,filter,Context.RECEIVER_NOT_EXPORTED)
  }else{
   registerLegacyChangesReceiver(filter)
  }
  registered=true
 }
 /** Android 8.0–12L do not support RECEIVER_NOT_EXPORTED. The fallback is
  * only invoked below API 33; keep the Lint exception local to this call. */
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 private fun registerLegacyChangesReceiver(filter:IntentFilter){
  registerReceiver(changes,filter)
 }
 private fun prefs()=getSharedPreferences("darly",0)
 private fun snoozeDefault()=prefs().getInt("default_snooze",5)
 private fun fadeDefault()=prefs().getInt("default_fade",30)
 private fun vibrateDefault()=prefs().getBoolean("default_vibrate",true)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
   requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),3)
  NextAlarmNotifier.sync(this)
  if(intent.getStringExtra("open_tools")=="timers")timersPage() else openCurrentScreen()
 }
 override fun onResume(){super.onResume();showing=true
  if(!registered)registerChangesReceiver()
  handler.removeCallbacks(clockTick);handler.removeCallbacks(refresh);handler.removeCallbacks(toolsTick)
  val allowed=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
  if(allowed && lastPermission==false)AlarmStore.reschedule(this)
  lastPermission=allowed
  if(RingService.active(this))openCurrentScreen()else when(screen){
   "home"->home();"timers"->timersPage();"stopwatch"->stopwatchPage();"rail"->railPage();"rail_stations"->railStationPicker(railChoosingOrigin);"rail_key"->railKeyPage()
  }
  NextAlarmNotifier.sync(this)
 }
 override fun onPause(){stopPreview();showing=false;handler.removeCallbacks(clockTick);handler.removeCallbacks(refresh);handler.removeCallbacks(toolsTick)
  if(registered){unregisterReceiver(changes);registered=false}
  super.onPause()
 }
 private fun openCurrentScreen(){if(RingService.active(this)){startActivity(Intent(this,RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));return};home()}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==28&&resultCode==RESULT_OK)data?.data?.let{uri->
   try{
    contentResolver.openInputStream(uri)?.use{input->openFileOutput("darly_custom_wallpaper.jpg",MODE_PRIVATE).use{output->input.copyTo(output)}}
    prefs().edit().putString("theme","Своя фотография").apply()
    Ui.clearWallpaperCache();Ui.applyTheme(this);themes()
   }catch(e:Exception){Toast.makeText(this,"Не удалось открыть изображение",Toast.LENGTH_LONG).show()}
  }
  if(requestCode==17&&resultCode==RESULT_OK)data?.data?.let{uri->
   try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
   selectedSound=uri.toString();soundName?.text=soundLabel();Toast.makeText(this,"Мелодия выбрана",Toast.LENGTH_SHORT).show()
  }
 }
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent)
  setIntent(intent)
  if(intent.getStringExtra("open_tools")=="timers")timersPage()
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==3)NextAlarmNotifier.sync(this)
 }
 private fun chooseWallpaper(){
  startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),28)
 }
 private var soundName:TextView?=null
 private fun soundLabel():String=when(selectedSound){
  BundledSounds.TRUMPET->"Труба · Бодрое утро (DARLY)"
  ""->"Системная мелодия"
  else->"Своя мелодия"
 }
 private fun soundControls(){
  body.addView(Ui.text(this,"МЕЛОДИЯ СИГНАЛА",13f,Ui.muted));addGap(10)
  soundName=Ui.text(this,soundLabel(),14f,Ui.gold,true)
  body.addView(soundName!!);addGap(11)
  val row=Ui.row(this)
  row.addView(Ui.button(this,"Труба"){selectedSound=BundledSounds.TRUMPET;soundName?.text=soundLabel()},LinearLayout.LayoutParams(0,-2,1f))
  row.addView(Ui.hspace(this,12))
  row.addView(Ui.outlineButton(this,"Другой звук"){chooseSound()},LinearLayout.LayoutParams(0,-2,1f))
  body.addView(row);addGap(10)
  val other=Ui.row(this)
  other.addView(Ui.outlineButton(this,"▶ Прослушать") {previewSelectedSound()},LinearLayout.LayoutParams(0,-2,1f))
  other.addView(Ui.hspace(this,12))
  other.addView(Ui.outlineButton(this,"Системная") {selectedSound="";soundName?.text=soundLabel()},LinearLayout.LayoutParams(0,-2,1f))
  body.addView(other)
 }
 private var previewPlayer:android.media.MediaPlayer?=null
 private fun stopPreview(){try{previewPlayer?.stop()}catch(_:Exception){};previewPlayer?.release();previewPlayer=null}
 private fun previewSelectedSound(){
  stopPreview()
  try{
   val uri=BundledSounds.uri(this,selectedSound)
   previewPlayer=android.media.MediaPlayer().apply{
    setDataSource(this@MainActivity,uri)
    setAudioAttributes(android.media.AudioAttributes.Builder()
     .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
     .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
    setOnCompletionListener{stopPreview()}
    prepare();start()
   }
  }catch(_:Exception){stopPreview();Toast.makeText(this,"Не удалось воспроизвести мелодию",Toast.LENGTH_SHORT).show()}
 }
 private fun chooseSound(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"),17)}
 private fun setupScreen(which:String,title:String,subtitle:String="",back:Boolean=false){
  val from=screen
  if(from=="home" && which!="home")resetSelection()
  selectionButton=null
  soundName=null
  stopPreview()
  countdownLabels.clear()
  screen=which;clockView=null;handler.removeCallbacks(clockTick);handler.removeCallbacks(toolsTick);timerLabels.clear();stopwatchClock=null;Ui.applyTheme(this)
  // Edge-to-edge: wallpaper is drawn beneath the transparent status bar.
  window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
  window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
  window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=Ui.bg
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
  if(Build.VERSION.SDK_INT>=29)window.isStatusBarContrastEnforced=false
  val root=FrameLayout(this)
  val image=ImageView(this).apply{setImageDrawable(Ui.wallpaper(this@MainActivity));scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription=null}
  Ui.animateWallpaper(this,image)
  root.addView(image,FrameLayout.LayoutParams(-1,-1))
  // Softer vignette for user images preserves contrast and visible photo detail.
  val stops=if(Ui.theme(this)=="Своя фотография")
   intArrayOf(0x7307101c,0xa507121f.toInt(),0xd5070e1b.toInt())
   else intArrayOf(0x9507101c.toInt(),0xdd07121f.toInt(),0xf5070e1b.toInt())
  val shade=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,stops)}
  root.addView(shade,FrameLayout.LayoutParams(-1,-1))
  val frame=Ui.column(this)
  root.addView(frame,FrameLayout.LayoutParams(-1,-1))
  if(Build.VERSION.SDK_INT>=30){root.setOnApplyWindowInsetsListener{_,insets->val b=insets.getInsets(WindowInsets.Type.systemBars());frame.setPadding(0,b.top,0,b.bottom);insets}}
  else frame.fitsSystemWindows=true
  val scroll=ScrollView(this).apply{isFillViewport=false;isVerticalScrollBarEnabled=false;clipToPadding=false}
  body=Ui.column(this);Ui.pad(this,body,19,16);body.setPadding(body.paddingLeft,body.paddingTop,body.paddingRight,Ui.dp(this,34))
  scroll.addView(body);frame.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val bar=Ui.row(this)
  if(back){val prev=Ui.text(this,"‹",35f,Ui.WHITE,true)
   prev.setPadding(0,0,Ui.dp(this,18),0);Ui.interactive(this,prev){when(which){"stopwatch","timer_editor"->timersPage();"themes"->settings();"rail_stations","rail_key"->railPage();else->home()}};bar.addView(prev)}
  val heading=Ui.column(this);heading.addView(Ui.text(this,title,if(back)23f else 25f,Ui.WHITE,true))
  if(subtitle.isNotBlank()){heading.addView(Ui.space(this,4));heading.addView(Ui.text(this,subtitle,12f,Ui.muted))}
  bar.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
  if(!back){
   // Distinct clock face, crown and side controls; generous 52dp tap target.
   val timer=ImageView(this).apply{
    setImageResource(R.drawable.ic_stopwatch)
    imageTintList=ColorStateList.valueOf(Ui.muted)
    scaleType=ImageView.ScaleType.CENTER_INSIDE
    setPadding(Ui.dp(this@MainActivity,7),Ui.dp(this@MainActivity,7),Ui.dp(this@MainActivity,7),Ui.dp(this@MainActivity,7))
    contentDescription="Таймер и секундомер"
   }
   Ui.interactive(this,timer){timersPage()};bar.addView(timer,LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,52)))

  }
  body.addView(bar);body.addView(Ui.space(this,23))
  if(which=="home"||which=="presets"||which=="rail"||which=="settings")frame.addView(bottomNavigation(which))
  // No overlapping full-screen roots: they caused moving nav bars and jumpy status insets.
  // Animate only opacity of the incoming content; the wallpaper never translates.
  setContentView(root)
  val tabs=listOf("home","presets","rail","settings")
  if(from!=which && from in tabs && which in tabs && prefs().getBoolean("animations",true)){
   scroll.alpha=0f
   scroll.animate().alpha(1f).setDuration(260L)
    .setInterpolator(PathInterpolator(.2f,0f,.2f,1f)).start()
  }
 }
 // Horizontal swipes switch between primary sections, without disrupting vertical scrolls.
 override fun dispatchTouchEvent(ev:MotionEvent):Boolean{
  if(ev.actionMasked==MotionEvent.ACTION_DOWN){tabSwipeX=ev.x;tabSwipeY=ev.y}
  if(ev.actionMasked==MotionEvent.ACTION_UP&&screen in listOf("home","presets","rail","settings")){
   val dx=ev.x-tabSwipeX;val dy=ev.y-tabSwipeY
   if(kotlin.math.abs(dx)>Ui.dp(this,115)&&kotlin.math.abs(dy)<Ui.dp(this,85)){
    val cancel=MotionEvent.obtain(ev);cancel.action=MotionEvent.ACTION_CANCEL
    super.dispatchTouchEvent(cancel);cancel.recycle()
    val list=listOf("home","presets","rail","settings");val index=list.indexOf(screen)
    val target=(index+if(dx<0)1 else -1).coerceIn(0,list.lastIndex)
    if(target!=index)when(target){0->home();1->presets();2->railPage();3->settings()}
    return true
   }
  }
  return super.dispatchTouchEvent(ev)
 }
 private fun bottomNavigation(active:String):LinearLayout{
  val nav=Ui.row(this).apply{setBackgroundColor(0xf209111e.toInt());setPadding(Ui.dp(this@MainActivity,10),Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,10),Ui.dp(this@MainActivity,8))}
  listOf(Triple("home",R.drawable.ic_alarm_nav,"Будильники"),Triple("presets",R.drawable.ic_presets_nav,"Пресеты"),Triple("rail",R.drawable.ic_rail_nav,"Электрички"),Triple("settings",R.drawable.ic_settings_nav,"Настройки")).forEach{(key,iconId,label)->
   val item=Ui.column(this).apply{gravity=Gravity.CENTER;contentDescription=label}
   val icon=ImageView(this).apply{
    setImageResource(iconId)
    imageTintList=ColorStateList.valueOf(if(key==active)Ui.blue else Ui.muted)
    scaleType=ImageView.ScaleType.FIT_CENTER
    contentDescription=null
   }
   item.addView(icon,LinearLayout.LayoutParams(Ui.dp(this,28),Ui.dp(this,28)))
   item.addView(Ui.space(this,5));item.addView(Ui.text(this,label,12f,if(key==active)Ui.blue else Ui.muted).apply{gravity=Gravity.CENTER})
   Ui.interactive(this,item){when(key){"home"->home();"presets"->presets();"rail"->railPage();else->settings()}}
   nav.addView(item,LinearLayout.LayoutParams(0,Ui.dp(this,64),1f))
  }
  return nav
 }
 private fun section(title:String,tag:String?=null){val r=Ui.row(this);r.addView(Ui.text(this,title,19f,Ui.WHITE,true),LinearLayout.LayoutParams(0,-2,1f));if(tag!=null)r.addView(Ui.text(this,tag,12f,Ui.muted));body.addView(r);body.addView(Ui.space(this,12))}
 private fun small(s:String)=Ui.text(this,s,12f,Ui.muted)
 private fun daysLabel(mask:Int):String{if(mask==0)return "Однократно";if(mask==127)return "Ежедневно";if(mask==31)return "Пн–Пт";if(mask==96)return "Сб–Вс";val letters=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс");return letters.filterIndexed{i,_->(mask and (1 shl i))!=0}.joinToString(" · ")}
 private fun time(m:Int)="%02d:%02d".format(m/60,m%60)
 private fun duration(ms:Long,centis:Boolean=false):String=TimerMath.format(ms,centis)
 private fun countdown(at:Long):TextView=Ui.text(this,CountdownText.until(at),13f,Ui.blue,true).also{
  countdownLabels.add(it to at)
 }
 private fun enterSelection(id:Int,series:Boolean):Boolean{
  if(!choosing){choosing=true;selectedAlarms.clear();selectedSeries.clear()}
  if(series)selectedSeries.add(id)else selectedAlarms.add(id)
  if(prefs().getBoolean("haptic_buttons",true))body.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
  home()
  return true
 }
 private fun updateSelectionButton(){
  val count=selectedAlarms.size+selectedSeries.size
  selectionButton?.text="Удалить выбранные ($count)"
  selectionButton?.isEnabled=count>0
 }
 private fun resetSelection(){choosing=false;selectedAlarms.clear();selectedSeries.clear();selectionButton=null}
 private fun deleteSelection(all:Boolean){
  val series=if(all)SeriesStore.all(this).map{it.id}.toSet()else selectedSeries.toSet()
  val alarms=if(all)AlarmStore.all(this).map{it.id}.toSet()else selectedAlarms.toSet()
  series.forEach{SeriesStore.delete(this,it)}
  val singles=AlarmStore.all(this)
  singles.filter{it.id in alarms}.forEach{AlarmStore.cancel(this,it)}
  if(alarms.isNotEmpty())AlarmStore.save(this,singles.filterNot{it.id in alarms})
  resetSelection();home()
 }
 private fun selectButton(label:String,onClick:()->Unit)=Ui.outlineButton(this,label,onClick)
 private fun addGap(h:Int=13){body.addView(Ui.space(this,h))}
 private fun home(){setupScreen("home","DARLY","Твой ритм. Твоё утро.")
  if(showing)handler.post(clockTick)
  val clock=Ui.text(this,SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),62f,Ui.WHITE,true).apply{gravity=Gravity.CENTER};clockView=clock
  body.addView(clock);body.addView(Ui.text(this,SimpleDateFormat("EEEE, d MMMM",Locale("ru")).format(Date()),14f,Ui.muted).apply{gravity=Gravity.CENTER});addGap(26)
  val all=AlarmStore.all(this)
  val next=AlarmStore.nextUpcoming(this)
  val hero=Ui.card(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xe5355074.toInt(),0xf017253e.toInt())).apply{cornerRadius=Ui.dp(this@MainActivity,22).toFloat();setStroke(Ui.dp(this@MainActivity,1),0x776a91c4)}}
  hero.addView(Ui.text(this,"СЛЕДУЮЩИЙ СИГНАЛ",11f,Ui.gold,true));hero.addView(Ui.space(this,11))
  hero.addView(Ui.text(this,if(next==null) "— : —" else SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(next.whenAt)),39f,Ui.WHITE,true))
  hero.addView(Ui.text(this,next?.alarm?.title?:"Пока нет активных будильников",14f,Ui.muted))
  if(next!=null){
   hero.addView(Ui.space(this,9))
   hero.addView(countdown(next.whenAt))
   hero.addView(Ui.space(this,8))
   hero.addView(small(SimpleDateFormat("EEE, d MMM",Locale("ru")).format(Date(next.whenAt))+(if(next.snoozed) " · отложен" else "")))
  }
  body.addView(hero);addGap(20)
  val manager=getSystemService(AlarmManager::class.java)
  if(Build.VERSION.SDK_INT>=31&&!manager.canScheduleExactAlarms()){
   val hint=Ui.card(this);hint.addView(Ui.text(this,"Нужен доступ к точным будильникам",15f,Ui.WHITE,true));hint.addView(Ui.space(this,8));hint.addView(small("Без разрешения сигналы могут не сработать вовремя."));hint.addView(Ui.space(this,11));hint.addView(Ui.outlineButton(this,"Разрешить"){AlarmStore.exactPermission(this)});body.addView(hint);addGap()
  }
  val snoozed=AlarmStore.snoozed(this)
  if(snoozed.isNotEmpty()){section("Отложенные", "${snoozed.size}")
   snoozed.forEach{(a,until)->val c=Ui.card(this);c.addView(Ui.text(this,a.title,15f,Ui.WHITE,true));c.addView(Ui.space(this,7));c.addView(Ui.text(this,"Повтор в "+SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(until)),15f,Ui.gold));c.addView(Ui.space(this,6));c.addView(countdown(until));c.addView(Ui.space(this,10));c.addView(Ui.outlineButton(this,"Отменить повтор"){AlarmStore.cancelSnooze(this,a.id);home()});body.addView(c);addGap()}
  }
  val r=Ui.row(this);r.addView(Ui.button(this,"＋ Будильник"){singleEditor(null)},LinearLayout.LayoutParams(0,-2,1f));r.addView(Ui.hspace(this,12));r.addView(Ui.outlineButton(this,"＋ Серия"){seriesEditor(null)},LinearLayout.LayoutParams(0,-2,1f));body.addView(r);addGap(26)
  val groups=SeriesStore.all(this)
  // Management controls stay hidden until the user long-presses an alarm card.
  if(choosing && (groups.isNotEmpty()||all.isNotEmpty())){
   val manage=Ui.row(this)
   manage.addView(selectButton("Готово"){resetSelection();home()},LinearLayout.LayoutParams(0,-2,1f))
   manage.addView(Ui.hspace(this,12))
   manage.addView(selectButton("Выбрать все"){
    selectedSeries.addAll(groups.map{it.id})
    selectedAlarms.addAll(all.filter{it.groupId==0}.map{it.id})
    home()
   },LinearLayout.LayoutParams(0,-2,1f))
   body.addView(manage);addGap(8)
   val deleteAll=Ui.outlineButton(this,"Удалить все будильники"){deleteSelection(true)}
   body.addView(deleteAll);addGap(8)
   val selected=selectedSeries.size+selectedAlarms.size
   selectionButton=Ui.button(this,"Удалить выбранные ($selected)"){
    if(selectedAlarms.isNotEmpty()||selectedSeries.isNotEmpty())deleteSelection(false)
   }
   selectionButton?.isEnabled=selected>0
   body.addView(selectionButton!!);addGap(17)
  }
  if(groups.isNotEmpty()){section("Серии", "${groups.size}");groups.forEach{seriesCard(it,all);addGap()};addGap(12)}
  val singles=all.filter{it.groupId==0}.sortedWith(compareBy({it.hour},{it.minute}))
  section("Отдельные будильники","${singles.size}")
  if(singles.isEmpty()){val c=Ui.card(this);c.addView(Ui.text(this,"Здесь пока пусто",16f,Ui.WHITE,true));c.addView(Ui.space(this,7));c.addView(small("Создай один сигнал или настрой серию для работы, учёбы и других дел."));body.addView(c)}
  singles.forEach{a->singleCard(a);addGap()}
 }
 private fun seriesCard(s:AlarmSeries,all:List<DAlarm>){
  val c=Ui.card(this);val top=Ui.row(this);val info=Ui.column(this)
  info.addView(Ui.text(this,s.title,19f,Ui.WHITE,true));info.addView(Ui.space(this,5))
  info.addView(Ui.text(this,"${s.labelAt(0)} — ${s.labelAt(s.count-1)}",23f,Ui.gold,true));info.addView(Ui.space(this,5))
  info.addView(small("${s.count} сигналов · каждые ${s.step} мин · ${daysLabel(s.days)}"))
  if(s.enabled){val nearest=all.asSequence().filter{it.groupId==s.id}.mapNotNull{AlarmStore.nextTime(it)}.minOrNull()
   if(nearest!=null){info.addView(Ui.space(this,6));info.addView(countdown(nearest))}
  }
  top.addView(info,LinearLayout.LayoutParams(0,-2,1f))
  c.isClickable=true;c.isLongClickable=true
  c.setOnLongClickListener{enterSelection(s.id,true)}
  if(choosing){val box=CheckBox(this).apply{isChecked=s.id in selectedSeries;contentDescription="Выбрать серию ${s.title}"}
   box.setOnCheckedChangeListener{_,on->if(on)selectedSeries.add(s.id)else selectedSeries.remove(s.id);updateSelectionButton()};top.addView(box)
   Ui.interactive(this,c){box.isChecked=!box.isChecked}
  }else { val toggle=Ui.switch(this,s.enabled);toggle.setOnCheckedChangeListener{_,on->SeriesStore.toggle(this,s.id,on);home()};top.addView(toggle) };c.addView(top);c.addView(Ui.space(this,14));c.addView(Ui.divider(this));c.addView(Ui.space(this,12))
  if(!choosing){val actions=Ui.row(this);actions.addView(Ui.outlineButton(this,"Настроить"){seriesEditor(s)},LinearLayout.LayoutParams(0,-2,1f));actions.addView(Ui.hspace(this,12));actions.addView(Ui.outlineButton(this,"Удалить"){SeriesStore.delete(this,s.id);home()},LinearLayout.LayoutParams(0,-2,1f));c.addView(actions)}
  val disclosure=Ui.text(this,"Показать расписание  ↓",13f,Ui.blue,true)
  disclosure.setPadding(0,Ui.dp(this,12),0,0);val preview=Ui.column(this)
  Ui.interactive(this,disclosure){if(preview.childCount>0){preview.removeAllViews();disclosure.text="Показать расписание  ↓"}else{
   (0 until s.count).forEach{i->preview.addView(Ui.text(this,"${s.labelAt(i)}     ${s.title} · ${i+1}",13f,Ui.muted).apply{setPadding(0,Ui.dp(this@MainActivity,7),0,0)})};disclosure.text="Скрыть расписание  ↑"
  }}
  c.addView(disclosure);c.addView(preview);body.addView(c)
 }
 private fun singleCard(a:DAlarm){val c=Ui.card(this);val top=Ui.row(this);val info=Ui.column(this)
  info.addView(Ui.text(this,"%02d:%02d".format(a.hour,a.minute),30f,if(a.enabled)Ui.WHITE else Ui.muted,true));info.addView(Ui.space(this,3));info.addView(Ui.text(this,a.title,14f,Ui.gold,true));info.addView(Ui.space(this,5));info.addView(small(daysLabel(a.days)))
  if(a.enabled){AlarmStore.nextTime(a)?.let{at->info.addView(Ui.space(this,5));info.addView(countdown(at))}}
  top.addView(info,LinearLayout.LayoutParams(0,-2,1f))
  c.isClickable=true;c.isLongClickable=true
  c.setOnLongClickListener{enterSelection(a.id,false)}
  if(choosing){val box=CheckBox(this).apply{isChecked=a.id in selectedAlarms;contentDescription="Выбрать ${a.title}"}
   box.setOnCheckedChangeListener{_,on->if(on)selectedAlarms.add(a.id)else selectedAlarms.remove(a.id);updateSelectionButton()};top.addView(box)
   Ui.interactive(this,c){box.isChecked=!box.isChecked}
  }else { val toggle=Ui.switch(this,a.enabled);toggle.setOnCheckedChangeListener{_,enabled->val updated=a.copy(enabled=enabled);AlarmStore.save(this,AlarmStore.all(this).map{if(it.id==a.id)updated else it});AlarmStore.schedule(this,updated);home()};top.addView(toggle) };c.addView(top)
  if(!choosing){c.addView(Ui.space(this,13));val actions=Ui.row(this);actions.addView(Ui.outlineButton(this,"Изменить"){singleEditor(a)},LinearLayout.LayoutParams(0,-2,1f));actions.addView(Ui.hspace(this,12));actions.addView(Ui.outlineButton(this,"Удалить"){AlarmStore.cancel(this,a);AlarmStore.save(this,AlarmStore.all(this).filter{it.id!=a.id});home()},LinearLayout.LayoutParams(0,-2,1f));c.addView(actions)}
  body.addView(c)
 }
 private fun daySelector(initial:Int,onChange:(Int)->Unit):LinearLayout{
  var mask=initial;val row=Ui.row(this);val labels=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
  labels.forEachIndexed{i,t->val view=Ui.text(this,t,12f,Ui.WHITE,true).apply{gravity=Gravity.CENTER;minHeight=Ui.dp(this@MainActivity,41)}
   fun paint(){view.background=Ui.shape(if(mask and (1 shl i)!=0)Ui.blue else 0xd9283547.toInt(),Ui.dp(this@MainActivity,10).toFloat());view.setTextColor(if(mask and (1 shl i)!=0)0xff0d192b.toInt()else Ui.muted)}
   paint();Ui.interactive(this,view){mask=mask xor (1 shl i);paint();onChange(mask)}
   val lp=LinearLayout.LayoutParams(0,Ui.dp(this,41),1f);if(i>0)lp.marginStart=Ui.dp(this,6);row.addView(view,lp)
  }
  return row
 }
 private fun textInput(value:String,hint:String):EditText=EditText(this).apply{
  setSingleLine(true);setText(value);setSelection(text.length);setTextColor(Ui.WHITE);setHintTextColor(Ui.muted);textSize=16f;this.hint=hint
  background=Ui.shape(0xee1c2a3b.toInt(),Ui.dp(this@MainActivity,15).toFloat(),0x44788ba5)
  Ui.pad(this@MainActivity,this,14,13)
 }
 private fun picker(values:List<Int>,value:Int,format:(Int)->String):Spinner{
  val sp=Spinner(this)
  sp.adapter=object:ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values.map(format)){
   override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View{
    val v=super.getView(position,convertView,parent) as TextView;v.setTextColor(Ui.WHITE);v.textSize=16f;v.setPadding(Ui.dp(this@MainActivity,14),Ui.dp(this@MainActivity,12),Ui.dp(this@MainActivity,14),Ui.dp(this@MainActivity,12));return v
   }
  }
  sp.background=Ui.shape(0xee1c2a3b.toInt(),Ui.dp(this,15).toFloat(),0x44788ba5)
  sp.setSelection(values.indexOf(value).coerceAtLeast(0));return sp
 }
 private fun option(label:String,view:View){body.addView(Ui.text(this,label,14f,Ui.muted));addGap(7);body.addView(view);addGap(15)}
 private fun singleEditor(a:DAlarm?){selectedSound=a?.sound?:"";setupScreen("single","Будильник",if((a?.id?:0)<=0) "Новый сигнал" else "Изменение сигнала",true)
  val p=CompactTimePicker(this).apply{hour=a?.hour?:7;minute=a?.minute?:0}
  body.addView(p);addGap(12)
  val title=textInput(a?.title?:"Будильник","Название");option("НАЗВАНИЕ",title)
  body.addView(Ui.text(this,"ДНИ НЕДЕЛИ",13f,Ui.muted));addGap(9);var days=a?.days?:0;body.addView(daySelector(days){days=it});addGap(19)
  soundControls();addGap(16)
  val vibrate=Ui.switch(this,a?.vibrate?:vibrateDefault());val vr=Ui.row(this);vr.addView(Ui.text(this,"Вибрация",15f),LinearLayout.LayoutParams(0,-2,1f));vr.addView(vibrate);body.addView(vr);addGap(18)
  val snoozes=listOf(1,5,10,15,20,30);val snooze=picker(snoozes,a?.snooze?:snoozeDefault()){ "$it мин" };option("ОТЛОЖИТЬ НА",snooze)
  val fades=listOf(0,15,30,60,120);val fade=picker(fades,a?.fade?:fadeDefault()){if(it==0) "Без нарастания" else "$it сек"};option("НАРАСТАНИЕ ГРОМКОСТИ",fade)
  body.addView(Ui.button(this,"Сохранить будильник"){
   val current=AlarmStore.all(this);val nextId=(current.maxOfOrNull{it.id}?:0)+1
   val alarm=DAlarm(a?.id?.takeIf{it>0}?:nextId,p.hour,p.minute,days,selectedSound,true,title.text.toString().trim().ifBlank{"Будильник"},vibrate.isChecked,snoozes[snooze.selectedItemPosition],fades[fade.selectedItemPosition])
   AlarmStore.save(this,current.filter{it.id!=alarm.id}+alarm)
   val ok=AlarmStore.schedule(this,alarm);if(!ok)Toast.makeText(this,"Разреши точные будильники",Toast.LENGTH_LONG).show();home()
  });addGap(10)
  body.addView(Ui.outlineButton(this,"Сохранить как пресет"){
   val alarm=DAlarm(0,p.hour,p.minute,days,selectedSound,true,title.text.toString().ifBlank{"Будильник"},vibrate.isChecked,snoozes[snooze.selectedItemPosition],fades[fade.selectedItemPosition]);PresetStore.add(this,alarm);Toast.makeText(this,"Шаблон сохранён",Toast.LENGTH_SHORT).show()
  })
 }
 private fun seriesEditor(existing:AlarmSeries?){val source=existing?:AlarmSeries(vibrate=vibrateDefault(),snooze=snoozeDefault(),fade=fadeDefault())
  selectedSound=source.sound;setupScreen("series","Серия будильников",if((existing?.id ?: 0)>0) "Изменение расписания" else "Несколько сигналов с интервалом",true)
  val name=textInput(source.title,"Например, Работа");option("НАЗВАНИЕ СЕРИИ",name)
  val t=CompactTimePicker(this).apply{hour=source.start/60;minute=source.start%60}
  body.addView(Ui.text(this,"ПЕРВЫЙ СИГНАЛ",13f,Ui.muted));addGap(10);body.addView(t);addGap(12)
  var days=source.days
  body.addView(Ui.text(this,"ДНИ НЕДЕЛИ",13f,Ui.muted));addGap(9);body.addView(daySelector(days){days=it});addGap(20)
  val intervals=listOf(5,10,15,20,30,45,60);val interval=picker(intervals,source.step){"Каждые $it мин"};option("ИНТЕРВАЛ",interval)
  val counts=(2..24).toList();val count=picker(counts,source.count){"$it сигналов"};option("КОЛИЧЕСТВО",count)
  val preview=Ui.card(this);preview.addView(Ui.text(this,"ПРЕДПРОСМОТР",11f,Ui.gold,true));val content=Ui.text(this,"",15f,Ui.WHITE)
  preview.addView(Ui.space(this,8));preview.addView(content);body.addView(preview);addGap(17)
  fun updatePreview(){val step=intervals[interval.selectedItemPosition.coerceAtLeast(0)];val n=counts[count.selectedItemPosition.coerceAtLeast(0)];val start=t.hour*60+t.minute;val times=(0 until n).map{time((start+it*step)%1440)};content.text=times.chunked(4).joinToString("\n"){it.joinToString("     ")}}
  t.setOnTimeChangedListener{updatePreview()}
  val update=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){updatePreview()}
  };interval.onItemSelectedListener=update;count.onItemSelectedListener=update;updatePreview()
  val vibrate=Ui.switch(this,source.vibrate);val v=Ui.row(this);v.addView(Ui.text(this,"Вибрация",15f),LinearLayout.LayoutParams(0,-2,1f));v.addView(vibrate);body.addView(v);addGap(13)
  soundControls();addGap(17)
  val snoozes=listOf(1,5,10,15,20,30);val snooze=picker(snoozes,source.snooze){"$it мин"};option("ОТЛОЖИТЬ НА",snooze)
  val fades=listOf(0,15,30,60,120);val fade=picker(fades,source.fade){if(it==0) "Без нарастания" else "$it сек"};option("НАРАСТАНИЕ ГРОМКОСТИ",fade)
  fun draft()=AlarmSeries(source.id,name.text.toString().trim().ifBlank{"Серия"},t.hour*60+t.minute,intervals[interval.selectedItemPosition],counts[count.selectedItemPosition],days,if(source.id>0)source.enabled else true,selectedSound,vibrate.isChecked,snoozes[snooze.selectedItemPosition],fades[fade.selectedItemPosition])
  body.addView(Ui.button(this,if(source.id>0) "Сохранить изменения" else "Создать серию"){
   val edited=draft();val s=SeriesStore.upsert(this,edited);Toast.makeText(this,"${s.count} сигналов в серии",Toast.LENGTH_SHORT).show()
   if(Build.VERSION.SDK_INT>=31&&!getSystemService(AlarmManager::class.java).canScheduleExactAlarms())Toast.makeText(this,"Разреши точные будильники в настройках",Toast.LENGTH_LONG).show()
   home()
  });addGap(10)
  body.addView(Ui.outlineButton(this,"Сохранить серию как пресет"){
   SeriesStore.addTemplate(this,draft());Toast.makeText(this,"Пресет серии сохранён",Toast.LENGTH_SHORT).show()
  })
  addGap(13);body.addView(small("Каждый сигнал создаётся в Android отдельно. Отключить или удалить все сигналы можно одной кнопкой на главном экране."))
 }
 private fun presets(){setupScreen("presets","Пресеты","Готовые сценарии, которые можно переиспользовать")
  section("Быстрый старт")
  val ready=Ui.card(this);ready.addView(Ui.text(this,"Рабочее утро",20f,Ui.WHITE,true));ready.addView(Ui.space(this,7));ready.addView(Ui.text(this,"04:30  ·  каждые 10 мин  ·  8 сигналов",15f,Ui.gold));ready.addView(Ui.space(this,7));ready.addView(small("Понедельник — пятница. От 04:30 до 05:40. Можно изменить время, интервал и количество."));ready.addView(Ui.space(this,13));ready.addView(Ui.button(this,"Настроить и добавить"){seriesEditor(AlarmSeries())});body.addView(ready);addGap(23)
  val series=SeriesStore.templates(this);section("Мои пресеты серий","${series.size}")
  if(series.isEmpty()){body.addView(small("Когда сохранишь серию как пресет, она появится здесь."));addGap()}
  series.forEach{s->val c=Ui.card(this);c.addView(Ui.text(this,s.title,17f,Ui.WHITE,true));c.addView(Ui.space(this,7));c.addView(Ui.text(this,"${s.labelAt(0)} · ${s.count} сигналов · ${s.step} мин",15f,Ui.gold));c.addView(Ui.space(this,12));c.addView(Ui.button(this,"Использовать"){seriesEditor(s.copy(id=0))});c.addView(Ui.space(this,8));c.addView(Ui.outlineButton(this,"Удалить шаблон"){SeriesStore.deleteTemplate(this,s.id);presets()});body.addView(c);addGap()}
  val singles=PresetStore.all(this);addGap(15);section("Пресеты одиночных","${singles.size}")
  singles.forEach{s->val c=Ui.card(this);c.addView(Ui.text(this,s.title,17f,Ui.WHITE,true));c.addView(Ui.space(this,6));c.addView(Ui.text(this,"%02d:%02d".format(s.hour,s.minute),25f,Ui.gold,true));c.addView(Ui.space(this,12));c.addView(Ui.button(this,"Использовать"){singleEditor(s.copy(id=0))});c.addView(Ui.space(this,7));c.addView(Ui.outlineButton(this,"Удалить шаблон"){PresetStore.delete(this,s.id);presets()});body.addView(c);addGap()}
 }
 private fun settings(){setupScreen("settings","Настройки","Вид, звук и поведение приложения")
  section("Внешний вид");body.addView(Ui.button(this,"Выбрать тему оформления  ›"){themes()});addGap(12)
  val animation=Ui.switch(this,prefs().getBoolean("animations",true));val a=Ui.row(this);a.addView(Ui.text(this,"Анимация кнопок",15f),LinearLayout.LayoutParams(0,-2,1f));animation.setOnCheckedChangeListener{_,v->prefs().edit().putBoolean("animations",v).apply()};a.addView(animation);body.addView(a);addGap(22)
  val sounds=Ui.switch(this,prefs().getBoolean("sound_buttons",true));val sr=Ui.row(this);sr.addView(Ui.text(this,"Звук нажатий",15f),LinearLayout.LayoutParams(0,-2,1f));sounds.setOnCheckedChangeListener{_,on->prefs().edit().putBoolean("sound_buttons",on).apply()};sr.addView(sounds);body.addView(sr);addGap(13)
  val haptics=Ui.switch(this,prefs().getBoolean("haptic_buttons",true));val hr=Ui.row(this);hr.addView(Ui.text(this,"Лёгкая вибрация нажатий",15f),LinearLayout.LayoutParams(0,-2,1f));haptics.setOnCheckedChangeListener{_,on->prefs().edit().putBoolean("haptic_buttons",on).apply()};hr.addView(haptics);body.addView(hr);addGap(22)
  section("Новые будильники")
  val vibration=Ui.switch(this,vibrateDefault());val vr=Ui.row(this);vr.addView(Ui.text(this,"Вибрация",15f),LinearLayout.LayoutParams(0,-2,1f));vibration.setOnCheckedChangeListener{_,v->prefs().edit().putBoolean("default_vibrate",v).apply()};vr.addView(vibration);body.addView(vr);addGap(13)
  val snoozes=listOf(1,5,10,15,20,30);val snooze=picker(snoozes,snoozeDefault()){ "$it мин" };option("ИНТЕРВАЛ ПОВТОРА",snooze)
  snooze.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(parent:android.widget.AdapterView<*>?){};override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){prefs().edit().putInt("default_snooze",snoozes[position]).apply()}}
  val fades=listOf(0,15,30,60,120);val fade=picker(fades,fadeDefault()){if(it==0) "Без нарастания" else "$it сек"};option("НАРАСТАНИЕ ГРОМКОСТИ",fade)
  fade.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(parent:android.widget.AdapterView<*>?){};override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){prefs().edit().putInt("default_fade",fades[position]).apply()}}
  addGap(11);section("Разрешения")
  body.addView(Ui.outlineButton(this,"Точные будильники"){AlarmStore.exactPermission(this)});addGap(10)
  body.addView(Ui.outlineButton(this,"Уведомления"){
   try{startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}catch(_:Exception){}
  });addGap(10)
  if(Build.VERSION.SDK_INT>=34){
   body.addView(Ui.outlineButton(this,"Полноэкранный сигнал на заблокированном экране"){
    try{startActivity(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",android.net.Uri.parse("package:$packageName")))}catch(_:Exception){Toast.makeText(this,"Открой разрешения DARLY в настройках системы",Toast.LENGTH_LONG).show()}
   });addGap(10)
  }
  body.addView(small("DARLY 3.0.0"))
 }
 private fun themes(){setupScreen("themes","Оформление","Сезоны и собственные фотографии",true)
  body.addView(Ui.button(this,"＋  Выбрать фотографию из галереи"){chooseWallpaper()});addGap(13)
  body.addView(small("Цвет элементов автоматически подбирается к фотографии. Изображение хранится только на устройстве."));addGap(18)
  val selected=Ui.selectedTheme(this)
  listOf("Сезонная","Весна","Лето","Осень","Зима","Своя фотография").forEach{name->
   if(name=="Своя фотография"&&!Ui.hasCustomWallpaper(this))return@forEach
   val c=Ui.card(this)
   val preview=ImageView(this).apply{
    setImageDrawable(if(name=="Своя фотография")Ui.customWallpaper(this@MainActivity) else resources.getDrawable(when(name){
     "Весна"->R.drawable.darly_spring;"Лето"->R.drawable.darly_summer;"Осень"->R.drawable.darly_autumn;"Зима"->R.drawable.darly_winter;else->Ui.background(this@MainActivity)
    },theme));scaleType=ImageView.ScaleType.CENTER_CROP
   }
   c.addView(preview,LinearLayout.LayoutParams(-1,Ui.dp(this,155)));c.addView(Ui.space(this,12))
   c.addView(Ui.text(this,(if(name==selected)"✓  " else "")+name,18f,Ui.WHITE,true))
   Ui.interactive(this,c){prefs().edit().putString("theme",name).apply();Ui.applyTheme(this);themes()}
   body.addView(c);addGap()
  }
 }
 /** Separate commuter timetable. It intentionally does not read or write DARLY alarms. */
 private fun railResetResults(){
  ++railSearchEpoch;railLoading=false;railTrains=emptyList();railSearchTotal=0;railNextOffset=0;railIntervalCount=0;railHasSearched=false;railError=null
 }
 private fun railPage(){
  setupScreen("rail","Электрички","Пригородное расписание · Яндекс Расписания")
  section("Маршрут")
  body.addView(Ui.outlineButton(this,"Откуда: ${railFrom?.label ?: "Выбрать станцию"}"){railStationPicker(true)});addGap(11)
  body.addView(Ui.outlineButton(this,"Куда: ${railTo?.label ?: "Выбрать станцию"}"){railStationPicker(false)});addGap(13)
  val actions=Ui.row(this)
  actions.addView(Ui.outlineButton(this,"⇄  Поменять местами"){
   val s=railFrom;railFrom=railTo;railTo=s;railResetResults();railPage()
  },LinearLayout.LayoutParams(0,-2,1f));actions.addView(Ui.hspace(this,12))
  actions.addView(Ui.outlineButton(this,"${if(railFrom!=null&&railTo!=null&&RailFavorites.contains(this,railFrom!!,railTo!!))"★" else "☆"}  Избранное"){
   val f=railFrom;val t=railTo
   if(f==null||t==null){Toast.makeText(this,"Выбери обе станции",Toast.LENGTH_SHORT).show()}
   else {RailFavorites.toggle(this,f,t);railPage()}
  },LinearLayout.LayoutParams(0,-2,1f))
  body.addView(actions);addGap(16)
  val dateRow=Ui.row(this)
  dateRow.addView(Ui.outlineButton(this,"Дата: $railDate  ▾"){
   val date=java.time.LocalDate.parse(railDate)
   android.app.DatePickerDialog(this,{_,year,month,day->
    railDate=java.time.LocalDate.of(year,month+1,day).toString()
    railResetResults();railPage()
   },date.year,date.monthValue-1,date.dayOfMonth).show()
  },LinearLayout.LayoutParams(0,-2,1f))
  dateRow.addView(Ui.hspace(this,12))
  dateRow.addView(Ui.outlineButton(this,"Сегодня"){
   railDate=java.time.LocalDate.now().toString();railResetResults();railPage()
  },LinearLayout.LayoutParams(0,-2,1f))
  body.addView(dateRow);addGap(15)
  val configured=railKey().isNotBlank()
  if(!configured){
   val info=Ui.card(this)
   info.addView(Ui.text(this,"Нужен API-ключ Яндекс Расписаний",16f,Ui.WHITE,true))
   info.addView(Ui.space(this,9));info.addView(small("Полученный в кабинете разработчика ключ хранится только на этом устройстве."))
   info.addView(Ui.space(this,11));info.addView(Ui.button(this,"Настроить ключ"){railKeyPage()})
   body.addView(info);addGap(15)
  }
  val search=Ui.button(this,if(railLoading)"Загрузка расписания…" else "Найти электрички"){
   if(!railLoading)railSearch(false)
  }
  search.isEnabled=!railLoading
  body.addView(search);addGap(23)
  val favorites=RailFavorites.all(this)
  if(favorites.isNotEmpty()){
   section("Избранные маршруты","${favorites.size}")
   favorites.forEach{favorite->
    body.addView(Ui.outlineButton(this,"${favorite.from.title}  →  ${favorite.to.title}"){
     railFrom=favorite.from;railTo=favorite.to;railResetResults();railPage()
    });addGap(9)
   }
   addGap(12)
  }
  if(railError!=null){val error=Ui.card(this)
   error.addView(Ui.text(this,"Не удалось загрузить",15f,Ui.gold,true));error.addView(Ui.space(this,8));error.addView(small(railError!!))
   body.addView(error);addGap(17)
  }
  if(railLoading){body.addView(small("Запрашиваем расписание электричек…"));addGap(18)}
  if(railTrains.isNotEmpty()){
   section("Расписание","${railTrains.size} из $railSearchTotal")
   railTrains.forEach{train->
    val card=Ui.card(this)
    val time=Ui.row(this)
    time.addView(Ui.text(this,train.labelTime,26f,Ui.WHITE,true))
    time.addView(Ui.text(this,"    →    ",18f,Ui.muted))
    time.addView(Ui.text(this,train.arrivalTime,24f,Ui.WHITE,true))
    card.addView(time);card.addView(Ui.space(this,7))
    val description=listOf(train.number.takeIf{it.isNotBlank()}?.let{"№ $it"},train.title.takeIf{it.isNotBlank()}).filterNotNull().joinToString(" · ")
    card.addView(small(description.ifBlank{"Электричка"}))
    if(train.durationSeconds>0){card.addView(Ui.space(this,7));card.addView(small("В пути: ${train.durationSeconds/3600} ч ${(train.durationSeconds%3600)/60} мин"))}
    body.addView(card);addGap(11)
   }
   if(railNextOffset<railSearchTotal){body.addView(Ui.outlineButton(this,"Показать следующие"){
    if(!railLoading)railSearch(true)
   });addGap(18)}
  }else if(!railLoading&&railError==null&&railHasSearched){
   body.addView(small("На выбранную дату электрички не найдены. Проверь станции и дату."));addGap(12)
  }
  if(railIntervalCount>0){body.addView(small("Также найдено интервальных рейсов: $railIntervalCount. Их время отправления может задаваться диапазоном, а не отдельным временем."));addGap(12)}
  body.addView(Ui.outlineButton(this,"Управление ключом API"){railKeyPage()});addGap(17)
  val attribution=Ui.text(this,YandexRaspApi.ATTRIBUTION,13f,Ui.muted,true)
  Ui.interactive(this,attribution){startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(YandexRaspApi.ATTRIBUTION_URL)))}
  body.addView(attribution);addGap(5)
  body.addView(small("Расписание справочное и может изменяться. Данные не влияют на будильники DARLY."))
 }
 private fun railKey()=getSharedPreferences("rail_schedules",MODE_PRIVATE).getString("api_key","").orEmpty()
 private fun railKeyPage(){
  setupScreen("rail_key","Доступ к расписанию","Ключ API Яндекс Расписаний",true)
  body.addView(small("Ключ не встраивается в APK и не отправляется на GitHub. Он хранится в приватных настройках DARLY на этом телефоне."));addGap(14)
  val key=textInput(railKey(),"Вставить API-ключ")
  key.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
  option("API-КЛЮЧ",key)
  body.addView(Ui.button(this,"Сохранить ключ"){
   val value=key.text.toString().trim()
   if(value.isBlank()){Toast.makeText(this,"Введите ключ",Toast.LENGTH_SHORT).show();return@button}
   getSharedPreferences("rail_schedules",MODE_PRIVATE).edit().putString("api_key",value).apply()
   railError=null;railPage()
  });addGap(12)
  if(railKey().isNotBlank()){body.addView(Ui.outlineButton(this,"Удалить сохранённый ключ"){
   getSharedPreferences("rail_schedules",MODE_PRIVATE).edit().remove("api_key").apply();railPage()
  });addGap(13)}
  body.addView(small("Работа API зависит от условий Яндекса и доступности сети. Для использования нужен выданный Яндексом ключ."))
 }
 private fun railStationPicker(isOrigin:Boolean){
  railChoosingOrigin=isOrigin
  setupScreen("rail_stations","Выбор станции",if(isOrigin)"Станция отправления" else "Станция прибытия",true)
  val stations=RailStationCache.all(this)
  val info=Ui.text(this,if(stations.isEmpty())"Список станций пока не загружен" else "Станций электричек в каталоге: ${stations.size}",13f,Ui.muted)
  body.addView(info);addGap(12)
  val query=textInput("","Название станции или населённого пункта")
  option("ПОИСК ПО КАТАЛОГУ",query)
  val results=Ui.column(this)
  body.addView(results);addGap(12)
  fun fill(text:String){
   results.removeAllViews()
   if(stations.isEmpty()){results.addView(small("Для поиска по названию скачай справочник станций ниже."));return}
   val matches=RailStationCache.find(stations,text)
   if(text.trim().length<2){results.addView(small("Введи минимум две буквы."));return}
   if(matches.isEmpty()){results.addView(small("Станции не найдены."));return}
   matches.forEach{station->
    results.addView(Ui.outlineButton(this,station.label){railStationSelected(station)})
    results.addView(Ui.space(this,7))
   }
  }
  query.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){fill(s?.toString().orEmpty())}
   override fun afterTextChanged(s:android.text.Editable?){}
  })
  fill("")
  if(stations.isEmpty()){
   addGap(12)
   val download=Ui.button(this,if(railCatalogLoading)"Загрузка…" else "Скачать справочник станций (~40 МБ)"){
    if(!railCatalogLoading)railDownloadStations()
   };download.isEnabled=!railCatalogLoading;body.addView(download);addGap(10)
   if(railCatalogLoading){body.addView(small("Скачиваем и обрабатываем станции. Это может занять некоторое время."));addGap(12)}
   if(railCatalogError!=null){body.addView(small(railCatalogError!!));addGap(12)}
  }
  addGap(12)
  body.addView(small("Либо введи код станции из адреса Яндекс Расписаний, например s9600213."));addGap(9)
  val code=textInput("","Код вида s1234567")
  option("КОД СТАНЦИИ",code)
  body.addView(Ui.outlineButton(this,"Выбрать по коду"){
   val normalized=code.text.toString().trim().lowercase(Locale.ROOT)
   if(RailScheduleRules.stationCode(normalized)==null){
    Toast.makeText(this,"Используй код станции вида s1234567",Toast.LENGTH_LONG).show()
    return@outlineButton
   }
   railStationSelected(YandexRaspApi.Station(normalized,normalized))
  })
 }
 private fun railStationSelected(station:YandexRaspApi.Station){
  if(railChoosingOrigin)railFrom=station else railTo=station
  railResetResults()
  railPage()
 }
 private fun railDownloadStations(){
  if(railKey().isBlank()){Toast.makeText(this,"Сначала укажи ключ API",Toast.LENGTH_LONG).show();railKeyPage();return}
  railCatalogLoading=true;railCatalogError=null;railStationPicker(railChoosingOrigin)
  val key=railKey()
  Thread({
   val result=runCatching{YandexRaspApi.loadStations(key)}
   if(result.isSuccess&&result.getOrThrow().isEmpty()){
    runOnUiThread{railCatalogError="Каталог не содержит пригородных станций";railCatalogLoading=false;if(screen=="rail_stations")railStationPicker(railChoosingOrigin)}
    return@Thread
   }
   val outcome=result.mapCatching{RailStationCache.replace(applicationContext,it);it.size}
   runOnUiThread{
    railCatalogLoading=false
    railCatalogError=outcome.exceptionOrNull()?.let{"Не удалось загрузить каталог: ${it.localizedMessage}"}
    if(screen=="rail_stations")railStationPicker(railChoosingOrigin)
   }
  },"DARLY-rail-catalog").start()
 }
 private fun railSearch(append:Boolean){
  val from=railFrom;val to=railTo;val key=railKey()
  if(key.isBlank()){railKeyPage();return}
  if(from==null||to==null){Toast.makeText(this,"Выбери станции отправления и прибытия",Toast.LENGTH_LONG).show();return}
  if(from.code==to.code){Toast.makeText(this,"Выбери разные станции",Toast.LENGTH_SHORT).show();return}
  railLoading=true;railError=null
  val offset=if(append)railNextOffset else 0
  if(!append){railTrains=emptyList();railSearchTotal=0;railNextOffset=0;railIntervalCount=0}
  val epoch=++railSearchEpoch
  val date=railDate
  railPage()
  Thread({
   val outcome=runCatching{YandexRaspApi.search(key,from,to,date,offset)}
   runOnUiThread{
    if(epoch!=railSearchEpoch)return@runOnUiThread
    railLoading=false;railHasSearched=true
    outcome.onSuccess{response->
     railTrains=if(append)(railTrains+response.trains).distinctBy{it.number to it.departureAt} else response.trains
     railSearchTotal=response.total;railNextOffset=offset+50;railIntervalCount=response.intervalCount
    }.onFailure{error->railError=error.localizedMessage?:"Ошибка сети"}
    if(screen=="rail")railPage()
   }
  },"DARLY-rail-search").start()
 }

 private fun toolsHeader(active:String){
  val row=Ui.row(this)
  val timers=if(active=="timers")Ui.button(this,"Таймеры"){if(active!="timers")timersPage()}
             else Ui.outlineButton(this,"Таймеры"){timersPage()}
  row.addView(timers,LinearLayout.LayoutParams(0,-2,1f))
  row.addView(Ui.hspace(this,12))
  val watch=if(active=="stopwatch")Ui.button(this,"Секундомер"){if(active!="stopwatch")stopwatchPage()}
            else Ui.outlineButton(this,"Секундомер"){stopwatchPage()}
  row.addView(watch,LinearLayout.LayoutParams(0,-2,1f))
  body.addView(row);addGap(22)
 }
 private fun timersPage(){setupScreen("timers","Время","Несколько независимых таймеров",true)
  toolsHeader("timers")
  body.addView(Ui.button(this,"＋ Новый таймер"){timerEditor()});addGap(21)
  val list=TimerStore.all(this).sortedBy{it.id};currentTimers=list
  section("Мои таймеры","${list.size}")
  if(list.isEmpty()){val c=Ui.card(this);c.addView(Ui.text(this,"Добавь первый таймер",17f,Ui.WHITE,true));c.addView(Ui.space(this,7));c.addView(small("Каждый таймер работает независимо, даже когда приложение закрыто."));body.addView(c)}
  list.forEach{t->
   val card=Ui.card(this)
   card.addView(Ui.text(this,t.title,17f,Ui.WHITE,true));card.addView(Ui.space(this,11))
   val label=Ui.text(this,duration(t.remaining()),35f,if(t.running)Ui.blue else Ui.WHITE,true)
   timerLabels[t.id]=label;card.addView(label);card.addView(Ui.space(this,14))
   val row=Ui.row(this)
   row.addView(Ui.button(this,if(t.running)"Пауза" else "Старт"){
    if(t.running)TimerStore.pause(this,t.id)else if(!TimerStore.start(this,t.id)){
     AlarmStore.exactPermission(this);Toast.makeText(this,"Нужен доступ к точным будильникам",Toast.LENGTH_LONG).show()
    };timersPage()
   },LinearLayout.LayoutParams(0,-2,1f))
   row.addView(Ui.hspace(this,12))
   row.addView(Ui.outlineButton(this,"Сброс"){
    TimerStore.reset(this,t.id);timersPage()
   },LinearLayout.LayoutParams(0,-2,1f))
   card.addView(row);card.addView(Ui.space(this,10))
   val extra=Ui.row(this)
   extra.addView(Ui.outlineButton(this,"＋1 мин"){
    TimerStore.addTime(this,t.id,60000);timersPage()
   },LinearLayout.LayoutParams(0,-2,1f));extra.addView(Ui.hspace(this,12))
   extra.addView(Ui.outlineButton(this,"＋5 мин"){
    TimerStore.addTime(this,t.id,300000);timersPage()
   },LinearLayout.LayoutParams(0,-2,1f));extra.addView(Ui.hspace(this,12))
   extra.addView(Ui.outlineButton(this,"Удалить"){
    TimerStore.delete(this,t.id);timersPage()
   },LinearLayout.LayoutParams(0,-2,1f))
   card.addView(extra);body.addView(card);addGap(13)
  }
  if(showing&&list.any{it.running})handler.post(toolsTick)
 }
 private fun timerEditor(){setupScreen("timer_editor","Новый таймер","Быстрая настройка времени",true)
  val name=textInput("","Например, Чай или тренировка");option("НАЗВАНИЕ",name)
  var total=5L*60000L
  val display=Ui.text(this,duration(total),43f,Ui.WHITE,true).apply{gravity=Gravity.CENTER}
  body.addView(display);addGap(22)
  val quick=listOf(0L to "＋10 сек",1L to "＋1 мин",5L to "＋5 мин",10L to "＋10 мин",30L to "＋30 мин",60L to "＋1 час")
  quick.chunked(2).forEach{group->val row=Ui.row(this)
   group.forEachIndexed{index,(minutes,label)->
    val b=Ui.outlineButton(this,label){val extra=if(minutes==0L)10000L else minutes*60000L;total=(total+extra).coerceAtMost(7*24*3600000L);display.text=duration(total)}
    if(index>0)row.addView(Ui.hspace(this,12))
    row.addView(b,LinearLayout.LayoutParams(0,-2,1f))
   }
   body.addView(row);addGap(9)
  }
  val row=Ui.row(this)
  row.addView(Ui.outlineButton(this,"−1 мин"){total=(total-60000).coerceAtLeast(0L);display.text=duration(total)},LinearLayout.LayoutParams(0,-2,1f))
  row.addView(Ui.hspace(this,12));row.addView(Ui.outlineButton(this,"Обнулить"){total=0;display.text=duration(total)},LinearLayout.LayoutParams(0,-2,1f))
  body.addView(row);addGap(21)
  body.addView(Ui.button(this,"Создать и запустить"){
   if(total==0L){Toast.makeText(this,"Добавь время",Toast.LENGTH_SHORT).show();return@button}
   val ok=TimerStore.create(this,name.text.toString(),total)
   if(!ok){AlarmStore.exactPermission(this);Toast.makeText(this,"Разреши точные будильники, затем запусти таймер",Toast.LENGTH_LONG).show()}
   timersPage()
  })
 }
 private fun stopwatchPage(){setupScreen("stopwatch","Секундомер","Стоп записывает результат и продолжает отсчёт",true)
  toolsHeader("stopwatch")
  val s=StopwatchStore.get(this);activeStopwatch=s
  val hero=Ui.card(this)
  val clock=Ui.text(this,duration(s.elapsed(),true),38f,Ui.WHITE,true).apply{gravity=Gravity.CENTER}
  stopwatchClock=clock;hero.addView(clock);hero.addView(Ui.space(this,14))
  hero.addView(Ui.text(this,if(s.running)"Идёт отсчёт" else if(s.accrued>0)"Пауза" else "Готов к старту",13f,Ui.muted).apply{gravity=Gravity.CENTER})
  body.addView(hero);addGap(17)
  val controls=Ui.row(this)
  controls.addView(Ui.button(this,if(s.running)"Стоп · записать" else "Старт"){
   if(s.running)StopwatchStore.stop(this)else StopwatchStore.start(this)
   stopwatchPage()
  },LinearLayout.LayoutParams(0,-2,1.25f));controls.addView(Ui.hspace(this,12))
  if(s.running||s.accrued>0L)controls.addView(Ui.outlineButton(this,if(s.running)"Пауза" else "Продолжить"){
   if(s.running)StopwatchStore.pause(this)else StopwatchStore.start(this)
   stopwatchPage()
  },LinearLayout.LayoutParams(0,-2,1f))
  body.addView(controls);addGap(10)
  body.addView(Ui.outlineButton(this,"Сбросить секундомер и результаты"){
   StopwatchStore.reset(this);stopwatchPage()
  });addGap(25)
  section("Результаты","${s.marks.size}")
  if(s.marks.isEmpty())body.addView(small("Нажимай «Стоп», чтобы сохранить результат. Отсчёт при этом продолжается."))
  s.marks.withIndex().reversed().forEach{(index,elapsed)->
   val previous=if(index==0)0L else s.marks[index-1]
   val card=Ui.card(this);val row=Ui.row(this)
   row.addView(Ui.text(this,"№ ${index+1}",17f,Ui.blue,true),LinearLayout.LayoutParams(0,-2,1f))
   row.addView(Ui.text(this,duration(elapsed,true),18f,Ui.WHITE,true));card.addView(row)
   card.addView(Ui.space(this,7));card.addView(small("От предыдущего: ${duration(TimerMath.split(elapsed,previous),true)}"))
   body.addView(card);addGap(9)
  }
  if(showing&&s.running)handler.post(toolsTick)
 }
 override fun onBackPressed(){when{screen=="stopwatch"||screen=="timer_editor"->timersPage();screen=="themes"->settings();screen=="rail_stations"||screen=="rail_key"->railPage();choosing->{resetSelection();home()};else->home()}}
}
