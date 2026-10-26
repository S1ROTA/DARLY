package com.darly.alarm

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:Activity(){
 private lateinit var body:LinearLayout
 private var sound=""
 private var editing:DAlarm?=null
 private val uiHandler=Handler(Looper.getMainLooper())
 private var screen="home"
 private var liveClock:TextView?=null
 // The clock updates only at minute boundaries while the activity is visible.
 private val clockTick=object:Runnable{override fun run(){
  if(screen=="home")liveClock?.text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
  val delay=60000L-(System.currentTimeMillis()%60000L)
  uiHandler.postDelayed(this,delay)
 }}
 private val alarmEvents=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
  if(RingService.active(this@MainActivity)){
   openCurrentScreen()
  }else if(screen=="home"){
   home()
  }
 }}
 private var listening=false
 private fun prefs()=getSharedPreferences("darly",0)
 private fun defaultSnooze()=prefs().getInt("default_snooze",5)
 private fun defaultFade()=prefs().getInt("default_fade",30)
 private fun defaultVibrate()=prefs().getBoolean("default_vibrate",true)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);Ui.applyTheme(this);window.statusBarColor=Ui.bg;window.navigationBarColor=Ui.bg;if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),3);AlarmStore.exactPermission(this);openCurrentScreen()}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==17&&resultCode==RESULT_OK)data?.data?.let{uri->try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};sound=uri.toString();Toast.makeText(this,"Мелодия выбрана",Toast.LENGTH_SHORT).show()}}
 override fun onResume(){
  super.onResume()
  if(!listening){
   val filter=IntentFilter(AlarmStore.ACTION_CHANGED)
   if(Build.VERSION.SDK_INT>=33)registerReceiver(alarmEvents,filter,Context.RECEIVER_NOT_EXPORTED)
   else registerReceiver(alarmEvents,filter)
   listening=true
  }
  uiHandler.removeCallbacks(clockTick)
  if(RingService.active(this))openCurrentScreen() else if(screen=="home")home()
  uiHandler.post(clockTick)
 }
 override fun onPause(){
  uiHandler.removeCallbacks(clockTick)
  if(listening){unregisterReceiver(alarmEvents);listening=false}
  super.onPause()
 }
 private fun openCurrentScreen(){if(RingService.active(this)){startActivity(Intent(this,RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));return};home()}
 private fun base(title:String){screen="other";liveClock=null;val scroll=ScrollView(this).apply{isFillViewport=true;background=Ui.backdrop(this@MainActivity)};body=Ui.column(this);Ui.pad(this,body,20,39);scroll.addView(body);setContentView(scroll);body.addView(Ui.text(this,title,27f,Color.WHITE,true));body.addView(Ui.space(this,22))}
 private fun panel():LinearLayout=Ui.column(this).apply{background=Ui.shape(Ui.card,Ui.dp(this@MainActivity,23).toFloat());Ui.pad(this@MainActivity,this,17,16)}
 private fun home(){screen="home";Ui.applyTheme(this);window.statusBarColor=Ui.bg;window.navigationBarColor=Ui.bg;base("DARLY");screen="home";val header=body.getChildAt(0) as TextView;header.text="DARLY";val headerRow=Ui.row(this);body.removeView(header);headerRow.addView(header,LinearLayout.LayoutParams(0,-2,1f));val gear=Ui.text(this,"⚙",29f,Color.WHITE,true).apply{gravity=Gravity.CENTER;setPadding(Ui.dp(this@MainActivity,14),0,0,0);setOnClickListener{settings()}};headerRow.addView(gear);body.addView(headerRow,0);body.addView(Ui.text(this,"Будильники",12f,Ui.muted));body.addView(Ui.space(this,20));val clock=Ui.text(this,SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),60f,Color.WHITE,true).apply{gravity=Gravity.CENTER};body.addView(clock);liveClock=clock;body.addView(Ui.text(this,SimpleDateFormat("EEEE, d MMMM",Locale("ru")).format(Date()),15f,Ui.muted).apply{gravity=Gravity.CENTER});body.addView(Ui.space(this,25))
  val alarms=AlarmStore.all(this).sortedWith(compareBy({it.hour},{it.minute}));val active=alarms.firstOrNull{it.enabled};val hero=panel();hero.background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,intArrayOf(0xff263c69.toInt(),0xff111d31.toInt(),0xff332b37.toInt())).apply{cornerRadius=Ui.dp(this@MainActivity,24).toFloat()};hero.addView(Ui.text(this,"☀  СЛЕДУЮЩИЙ БУДИЛЬНИК",13f,Ui.gold,true));hero.addView(Ui.space(this,10));hero.addView(Ui.text(this,if(active==null)"Нет активных" else "%02d:%02d".format(active.hour,active.minute),35f,Color.WHITE,true));hero.addView(Ui.text(this,active?.title?:"Добавь первый будильник",14f,Ui.muted));body.addView(hero);body.addView(Ui.space(this,24));val delayed=AlarmStore.snoozed(this)
  if(delayed.isNotEmpty()){
   body.addView(Ui.text(this,"Отложенные будильники",22f,Color.WHITE,true));body.addView(Ui.space(this,12))
   delayed.forEach{(a,until)->val card=panel();card.addView(Ui.text(this,a.title,17f,Ui.gold,true));card.addView(Ui.text(this,"Повтор в "+SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(until)),15f,Color.WHITE));card.addView(Ui.space(this,9));val cancel=Ui.text(this,"Отменить повтор",14f,Ui.blue,true);cancel.setOnClickListener{AlarmStore.cancelSnooze(this,a.id);home()};card.addView(cancel);body.addView(card);body.addView(Ui.space(this,12))}
  }
  body.addView(Ui.text(this,"Мои будильники",22f,Color.WHITE,true));body.addView(Ui.space(this,13))
  alarms.forEach{a->val card=panel();val line=Ui.row(this);val info=Ui.column(this);info.addView(Ui.text(this,"%02d:%02d".format(a.hour,a.minute),33f,if(a.enabled)Color.WHITE else Ui.muted,true));info.addView(Ui.text(this,a.title,15f,Ui.gold));info.addView(Ui.text(this,if(a.days==0)"Однократно" else "Повтор по дням",12f,Ui.muted));line.addView(info,LinearLayout.LayoutParams(0,-2,1f));val toggle=Ui.switch(this,a.enabled);toggle.setOnCheckedChangeListener{_,checked->val new=a.copy(enabled=checked);AlarmStore.save(this,AlarmStore.all(this).map{if(it.id==a.id)new else it});AlarmStore.schedule(this,new);home()};line.addView(toggle);card.addView(line);card.addView(Ui.space(this,12));val actions=Ui.row(this);val edit=Ui.text(this,"Изменить  ›",14f,Ui.blue,true);edit.setOnClickListener{editor(a)};actions.addView(edit,LinearLayout.LayoutParams(0,-2,1f));val del=Ui.text(this,"Удалить",14f,Ui.muted);del.setOnClickListener{AlarmStore.cancelSnooze(this,a.id);AlarmStore.schedule(this,a.copy(enabled=false));AlarmStore.save(this,AlarmStore.all(this).filter{it.id!=a.id});home()};actions.addView(del);card.addView(actions);body.addView(card);body.addView(Ui.space(this,12))}
  body.addView(Ui.space(this,8));body.addView(Ui.button(this,"＋  Новый будильник"){editor(null)});body.addView(Ui.space(this,18));body.addView(Ui.button(this,"▤  Пресеты"){presets()});body.addView(Ui.space(this,18))
 }
 private fun settings(){base("Настройки");body.addView(Ui.text(this,"Оформление",19f,Color.WHITE,true));body.addView(Ui.space(this,10));body.addView(Ui.button(this,"Темы оформления  ›"){themes()});body.addView(Ui.space(this,24));body.addView(Ui.text(this,"Новые будильники",19f,Color.WHITE,true));body.addView(Ui.space(this,10));
  val vibration=Ui.row(this);vibration.addView(Ui.text(this,"Вибрация по умолчанию",16f),LinearLayout.LayoutParams(0,-2,1f));val v=Ui.switch(this,defaultVibrate());v.setOnCheckedChangeListener{_,checked->prefs().edit().putBoolean("default_vibrate",checked).apply()};vibration.addView(v);body.addView(vibration);body.addView(Ui.space(this,15));
  body.addView(Ui.text(this,"Интервал повтора по умолчанию",16f));val snooze=Spinner(this);val mins=listOf(1,5,10,15,20,30);snooze.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,mins.map{"$it мин"});snooze.setSelection(mins.indexOf(defaultSnooze()).coerceAtLeast(0));snooze.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(parent:android.widget.AdapterView<*>?){};override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){prefs().edit().putInt("default_snooze",mins[position]).apply()}};body.addView(snooze);body.addView(Ui.space(this,15));
  body.addView(Ui.text(this,"Нарастание громкости",16f));val fade=Spinner(this);val fades=listOf(0,15,30,60,120);fade.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,fades.map{if(it==0)"Без нарастания" else "$it сек"});fade.setSelection(fades.indexOf(defaultFade()).coerceAtLeast(0));fade.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(parent:android.widget.AdapterView<*>?){};override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){prefs().edit().putInt("default_fade",fades[position]).apply()}};body.addView(fade);body.addView(Ui.space(this,24));
  body.addView(Ui.text(this,"Система",19f,Color.WHITE,true));body.addView(Ui.space(this,12));body.addView(Ui.button(this,"Разрешение на точные будильники"){AlarmStore.exactPermission(this)});body.addView(Ui.space(this,12));body.addView(Ui.button(this,"Настройки уведомлений"){try{startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))}catch(_:Exception){}});body.addView(Ui.space(this,20));body.addView(Ui.text(this,"Изменения по умолчанию действуют на новые будильники.",13f,Ui.muted))
 }
 private fun presets(){base("Пресеты");body.addView(Ui.text(this,"Сохраняй настройки и создавай будильники быстрее.",14f,Ui.muted));body.addView(Ui.space(this,18));val items=PresetStore.all(this);if(items.isEmpty())body.addView(Ui.text(this,"Пресетов пока нет. Открой будильник и сохрани его как пресет.",16f,Ui.muted));items.forEach{preset->val card=panel();card.addView(Ui.text(this,preset.title,19f,Color.WHITE,true));card.addView(Ui.text(this,"%02d:%02d".format(preset.hour,preset.minute),28f,Ui.gold,true));card.addView(Ui.space(this,8));card.addView(Ui.button(this,"Создать по пресету"){editor(preset.copy(id=-1,enabled=true))});card.addView(Ui.space(this,8));val remove=Ui.text(this,"Удалить пресет",14f,Ui.muted);remove.setOnClickListener{PresetStore.delete(this,preset.id);presets()};card.addView(remove);body.addView(card);body.addView(Ui.space(this,14))};body.addView(Ui.button(this,"← К будильникам"){home()})}
 private fun themes(){base("Темы оформления");val current=Ui.selectedTheme(this);val names=listOf("Neo Dark","Deadlock","Minecraft","Сезонная");names.forEach { name ->
  val card=panel();val preview=android.widget.ImageView(this).apply{val image=when(name){"Deadlock"->R.drawable.darly_deadlock;"Minecraft"->R.drawable.darly_minecraft;"Сезонная"->Ui.background(this@MainActivity);else->R.drawable.darly_neo};setImageResource(image);scaleType=android.widget.ImageView.ScaleType.CENTER_CROP};card.addView(preview,LinearLayout.LayoutParams(-1,Ui.dp(this,135)));card.addView(Ui.space(this,10));card.addView(Ui.text(this,(if(name==current)"✓  " else "")+name,21f,Color.WHITE,true));if(name=="Сезонная")card.addView(Ui.text(this,"Весна · Лето · Осень · Зима — автоматически",13f,Ui.muted));card.setOnClickListener{getSharedPreferences("darly",0).edit().putString("theme",name).apply();Ui.applyTheme(this);home()};body.addView(card);body.addView(Ui.space(this,14))}
 }
 private fun editor(a:DAlarm?){editing=a;sound=a?.sound?:"";base(if(a==null)"Новый будильник" else "Изменить будильник");val time=TimePicker(this).apply{setIs24HourView(true);hour=a?.hour?:7;minute=a?.minute?:0};body.addView(time);body.addView(Ui.space(this,12));val name=EditText(this).apply{setText(a?.title?:"Будильник");setTextColor(Color.WHITE);setHintTextColor(Ui.muted);hint="Название";background=Ui.shape(Ui.card,Ui.dp(this@MainActivity,15).toFloat());Ui.pad(this@MainActivity,this,15,12)};body.addView(name);body.addView(Ui.space(this,20));body.addView(Ui.text(this,"Повторять",18f,Color.WHITE,true));val labels=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс");val days=Ui.row(this);val checks=labels.mapIndexed{i,label->CheckBox(this).apply{text=label;textSize=11f;setTextColor(Color.WHITE);isChecked=a?.days?.and(1 shl i)!=0}};checks.forEach{days.addView(it,LinearLayout.LayoutParams(0,Ui.dp(this,60),1f))};body.addView(days)
  body.addView(Ui.button(this,"♫  Выбрать мелодию"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"),17)});body.addView(Ui.space(this,18));val vibrate=Ui.switch(this,a?.vibrate?:defaultVibrate());val vr=Ui.row(this);vr.addView(Ui.text(this,"Вибрация",17f),LinearLayout.LayoutParams(0,-2,1f));vr.addView(vibrate);body.addView(vr);body.addView(Ui.space(this,12));body.addView(Ui.text(this,"Отложить на",16f));val snooze=Spinner(this);val mins=listOf(1,5,10,15,20,30);snooze.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,mins.map{"$it мин"});snooze.setSelection(mins.indexOf(a?.snooze?:defaultSnooze()).coerceAtLeast(0));body.addView(snooze);body.addView(Ui.space(this,12));body.addView(Ui.text(this,"Плавное увеличение громкости",16f));val fade=Spinner(this);val fades=listOf(0,15,30,60,120);fade.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,fades.map{if(it==0)"Выключено" else "$it сек"});fade.setSelection(fades.indexOf(a?.fade?:defaultFade()).coerceAtLeast(0));body.addView(fade);body.addView(Ui.space(this,22));body.addView(Ui.button(this,"Сохранить будильник"){val mask=checks.foldIndexed(0){i,acc,b->if(b.isChecked)acc or (1 shl i) else acc};val alarm=DAlarm(a?.id?.takeIf{it>0}?:(System.currentTimeMillis()%Int.MAX_VALUE).toInt(),time.hour,time.minute,mask,sound,true,name.text.toString().ifBlank{"Будильник"},vibrate.isChecked,mins[snooze.selectedItemPosition],fades[fade.selectedItemPosition]);val updated=AlarmStore.all(this).filter{it.id!=alarm.id}+alarm;AlarmStore.save(this,updated);val ok=AlarmStore.schedule(this,alarm);Toast.makeText(this,if(ok)"Будильник сохранён" else "Разреши точные будильники в настройках",Toast.LENGTH_LONG).show();home()});body.addView(Ui.space(this,14));body.addView(Ui.button(this,"Сохранить как пресет"){val mask=checks.foldIndexed(0){i,acc,b->if(b.isChecked)acc or (1 shl i) else acc};val template=DAlarm((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),time.hour,time.minute,mask,sound,true,name.text.toString().ifBlank{"Будильник"},vibrate.isChecked,mins[snooze.selectedItemPosition],fades[fade.selectedItemPosition]);PresetStore.add(this,template);Toast.makeText(this,"Пресет сохранён",Toast.LENGTH_SHORT).show()});body.addView(Ui.space(this,12));val back=Ui.text(this,"← Назад",16f,Ui.muted);back.setOnClickListener{home()};body.addView(back)
 }
 override fun onBackPressed(){home()}
}
