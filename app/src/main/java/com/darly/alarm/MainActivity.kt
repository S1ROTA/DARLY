package com.darly.alarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AlarmManager
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:Activity(){
 private lateinit var body:LinearLayout
 private var screen="home"
 private var selectedSound=""
 private var clockView:TextView?=null
 private var showing=false
 private var lastPermission:Boolean?=null
 private val handler=Handler(Looper.getMainLooper())
 private val clockTick=object:Runnable{override fun run(){
  if(screen=="home" && showing){
   clockView?.text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
   handler.postDelayed(this,60000-(System.currentTimeMillis()%60000))
  }
 }}
 private val refresh=Runnable{if(screen=="home")home()}
 private val changes=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
  if(RingService.active(this@MainActivity)){openCurrentScreen();return}
  if(screen=="home"){handler.removeCallbacks(refresh);handler.postDelayed(refresh,100)}
 }}
 private var registered=false
 private fun prefs()=getSharedPreferences("darly",0)
 private fun snoozeDefault()=prefs().getInt("default_snooze",5)
 private fun fadeDefault()=prefs().getInt("default_fade",30)
 private fun vibrateDefault()=prefs().getBoolean("default_vibrate",true)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
   requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),3)
  openCurrentScreen()
 }
 override fun onResume(){super.onResume();showing=true
  if(!registered){val filter=IntentFilter(AlarmStore.ACTION_CHANGED)
   if(Build.VERSION.SDK_INT>=33)registerReceiver(changes,filter,Context.RECEIVER_NOT_EXPORTED)else registerReceiver(changes,filter)
   registered=true
  }
  handler.removeCallbacks(clockTick);handler.removeCallbacks(refresh)
  val allowed=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
  if(allowed && lastPermission==false)AlarmStore.reschedule(this)
  lastPermission=allowed
  if(RingService.active(this))openCurrentScreen()else if(screen=="home")home()
  // home() already arms a single minute-boundary clock callback.
 }
 override fun onPause(){showing=false;handler.removeCallbacks(clockTick);handler.removeCallbacks(refresh)
  if(registered){unregisterReceiver(changes);registered=false}
  super.onPause()
 }
 private fun openCurrentScreen(){if(RingService.active(this)){startActivity(Intent(this,RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));return};home()}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==17&&resultCode==RESULT_OK)data?.data?.let{uri->
   try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
   selectedSound=uri.toString();Toast.makeText(this,"Мелодия выбрана",Toast.LENGTH_SHORT).show()
  }
 }
 private fun chooseSound(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"),17)}
 private fun setupScreen(which:String,title:String,subtitle:String="",back:Boolean=false){
  screen=which;clockView=null;handler.removeCallbacks(clockTick);Ui.applyTheme(this)
  window.statusBarColor=Ui.bg;window.navigationBarColor=Ui.bg
  window.decorView.systemUiVisibility=0
  val root=FrameLayout(this)
  val image=ImageView(this).apply{setImageResource(Ui.background(this@MainActivity));scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription=null}
  root.addView(image,FrameLayout.LayoutParams(-1,-1))
  val shade=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x9507101c.toInt(),0xdd07121f.toInt(),0xf5070e1b.toInt()))}
  root.addView(shade,FrameLayout.LayoutParams(-1,-1))
  val frame=Ui.column(this)
  root.addView(frame,FrameLayout.LayoutParams(-1,-1))
  if(Build.VERSION.SDK_INT>=30){root.setOnApplyWindowInsetsListener{v,insets->val b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(0,b.top,0,b.bottom);insets}}
  else root.fitsSystemWindows=true
  val scroll=ScrollView(this).apply{isFillViewport=false;isVerticalScrollBarEnabled=false;clipToPadding=false}
  body=Ui.column(this);Ui.pad(this,body,19,16);body.setPadding(body.paddingLeft,body.paddingTop,body.paddingRight,Ui.dp(this,34))
  scroll.addView(body);frame.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val bar=Ui.row(this)
  if(back){val prev=Ui.text(this,"‹",35f,Ui.WHITE,true)
   prev.setPadding(0,0,Ui.dp(this,18),0);Ui.interactive(this,prev){home()};bar.addView(prev)}
  val heading=Ui.column(this);heading.addView(Ui.text(this,title,if(back)23f else 25f,Ui.WHITE,true))
  if(subtitle.isNotBlank()){heading.addView(Ui.space(this,4));heading.addView(Ui.text(this,subtitle,12f,Ui.muted))}
  bar.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
  if(!back){val gear=Ui.text(this,"⚙",27f,Ui.muted).apply{gravity=Gravity.CENTER;minWidth=Ui.dp(this@MainActivity,46)}
   gear.contentDescription="Настройки";Ui.interactive(this,gear){settings()};bar.addView(gear)}
  body.addView(bar);body.addView(Ui.space(this,23))
  if(which=="home"||which=="presets"||which=="settings")frame.addView(bottomNavigation(which))
  setContentView(root)
 }
 private fun bottomNavigation(active:String):LinearLayout{
  val nav=Ui.row(this).apply{setBackgroundColor(0xf209111e.toInt());setPadding(Ui.dp(this@MainActivity,10),Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,10),Ui.dp(this@MainActivity,8))}
  listOf(Triple("home","◷","Будильники"),Triple("presets","▦","Пресеты"),Triple("settings","⚙","Настройки")).forEach{(key,symbol,label)->
   val item=Ui.column(this).apply{gravity=Gravity.CENTER}
   item.addView(Ui.text(this,symbol,24f,if(key==active)Ui.blue else Ui.muted,true).apply{gravity=Gravity.CENTER})
   item.addView(Ui.space(this,4));item.addView(Ui.text(this,label,11f,if(key==active)Ui.blue else Ui.muted).apply{gravity=Gravity.CENTER})
   Ui.interactive(this,item){when(key){"home"->home();"presets"->presets();else->settings()}}
   nav.addView(item,LinearLayout.LayoutParams(0,Ui.dp(this,54),1f))
  }
  return nav
 }
 private fun section(title:String,tag:String?=null){val r=Ui.row(this);r.addView(Ui.text(this,title,19f,Ui.WHITE,true),LinearLayout.LayoutParams(0,-2,1f));if(tag!=null)r.addView(Ui.text(this,tag,12f,Ui.muted));body.addView(r);body.addView(Ui.space(this,12))}
 private fun small(s:String)=Ui.text(this,s,12f,Ui.muted)
 private fun daysLabel(mask:Int):String{if(mask==0)return "Однократно";if(mask==127)return "Ежедневно";if(mask==31)return "Пн–Пт";if(mask==96)return "Сб–Вс";val letters=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс");return letters.filterIndexed{i,_->(mask and (1 shl i))!=0}.joinToString(" · ")}
 private fun time(m:Int)="%02d:%02d".format(m/60,m%60)
 private fun addGap(h:Int=13){body.addView(Ui.space(this,h))}
 private fun home(){setupScreen("home","DARLY","Твой ритм. Твоё утро.")
  if(showing)handler.post(clockTick)
  val clock=Ui.text(this,SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),62f,Ui.WHITE,true).apply{gravity=Gravity.CENTER};clockView=clock
  body.addView(clock);body.addView(Ui.text(this,SimpleDateFormat("EEEE, d MMMM",Locale("ru")).format(Date()),14f,Ui.muted).apply{gravity=Gravity.CENTER});addGap(26)
  val all=AlarmStore.all(this);val next=all.mapNotNull{a->AlarmStore.nextTime(a)?.let{a to it}}.minByOrNull{it.second}
  val hero=Ui.card(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xe5355074.toInt(),0xf017253e.toInt())).apply{cornerRadius=Ui.dp(this@MainActivity,22).toFloat();setStroke(Ui.dp(this@MainActivity,1),0x776a91c4)}}
  hero.addView(Ui.text(this,"СЛЕДУЮЩИЙ СИГНАЛ",11f,Ui.gold,true));hero.addView(Ui.space(this,11))
  hero.addView(Ui.text(this,if(next==null) "— : —" else "%02d:%02d".format(next.first.hour,next.first.minute),39f,Ui.WHITE,true))
  hero.addView(Ui.text(this,next?.first?.title?:"Пока нет активных будильников",14f,Ui.muted))
  if(next!=null){hero.addView(Ui.space(this,10));hero.addView(small(SimpleDateFormat("EEE, d MMM",Locale("ru")).format(Date(next.second))))}
  body.addView(hero);addGap(20)
  val manager=getSystemService(AlarmManager::class.java)
  if(Build.VERSION.SDK_INT>=31&&!manager.canScheduleExactAlarms()){
   val hint=Ui.card(this);hint.addView(Ui.text(this,"Нужен доступ к точным будильникам",15f,Ui.WHITE,true));hint.addView(Ui.space(this,8));hint.addView(small("Без разрешения сигналы могут не сработать вовремя."));hint.addView(Ui.space(this,11));hint.addView(Ui.outlineButton(this,"Разрешить"){AlarmStore.exactPermission(this)});body.addView(hint);addGap()
  }
  val snoozed=AlarmStore.snoozed(this)
  if(snoozed.isNotEmpty()){section("Отложенные", "${snoozed.size}")
   snoozed.forEach{(a,until)->val c=Ui.card(this);c.addView(Ui.text(this,a.title,15f,Ui.WHITE,true));c.addView(Ui.space(this,7));c.addView(Ui.text(this,"Повтор в "+SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(until)),15f,Ui.gold));c.addView(Ui.space(this,10));c.addView(Ui.outlineButton(this,"Отменить повтор"){AlarmStore.cancelSnooze(this,a.id);home()});body.addView(c);addGap()}
  }
  val r=Ui.row(this);r.addView(Ui.button(this,"＋ Будильник"){singleEditor(null)},LinearLayout.LayoutParams(0,-2,1f));r.addView(Ui.space(this,10));r.addView(Ui.outlineButton(this,"＋ Серия"){seriesEditor(null)},LinearLayout.LayoutParams(0,-2,1f));body.addView(r);addGap(26)
  val groups=SeriesStore.all(this)
  if(groups.isNotEmpty()){section("Серии", "${groups.size}");groups.forEach{seriesCard(it);addGap()};addGap(12)}
  val singles=all.filter{it.groupId==0}.sortedWith(compareBy({it.hour},{it.minute}))
  section("Отдельные будильники","${singles.size}")
  if(singles.isEmpty()){val c=Ui.card(this);c.addView(Ui.text(this,"Здесь пока пусто",16f,Ui.WHITE,true));c.addView(Ui.space(this,7));c.addView(small("Создай один сигнал или настрой серию для работы, учёбы и других дел."));body.addView(c)}
  singles.forEach{a->singleCard(a);addGap()}
 }
 private fun seriesCard(s:AlarmSeries){
  val c=Ui.card(this);val top=Ui.row(this);val info=Ui.column(this)
  info.addView(Ui.text(this,s.title,19f,Ui.WHITE,true));info.addView(Ui.space(this,5))
  info.addView(Ui.text(this,"${s.labelAt(0)} — ${s.labelAt(s.count-1)}",23f,Ui.gold,true));info.addView(Ui.space(this,5))
  info.addView(small("${s.count} сигналов · каждые ${s.step} мин · ${daysLabel(s.days)}"))
  top.addView(info,LinearLayout.LayoutParams(0,-2,1f));val toggle=Ui.switch(this,s.enabled);toggle.setOnCheckedChangeListener{_,on->SeriesStore.toggle(this,s.id,on);home()};top.addView(toggle);c.addView(top);c.addView(Ui.space(this,14));c.addView(Ui.divider(this));c.addView(Ui.space(this,12))
  val actions=Ui.row(this);actions.addView(Ui.outlineButton(this,"Настроить"){seriesEditor(s)},LinearLayout.LayoutParams(0,-2,1f));actions.addView(Ui.space(this,10));actions.addView(Ui.outlineButton(this,"Удалить"){confirm("Удалить серию?","Все ${s.count} будильников серии будут удалены."){SeriesStore.delete(this,s.id);home()}},LinearLayout.LayoutParams(0,-2,1f));c.addView(actions)
  val disclosure=Ui.text(this,"Показать расписание  ↓",13f,Ui.blue,true)
  disclosure.setPadding(0,Ui.dp(this,12),0,0);val preview=Ui.column(this)
  Ui.interactive(this,disclosure){if(preview.childCount>0){preview.removeAllViews();disclosure.text="Показать расписание  ↓"}else{
   (0 until s.count).forEach{i->preview.addView(Ui.text(this,"${s.labelAt(i)}     ${s.title} · ${i+1}",13f,Ui.muted).apply{setPadding(0,Ui.dp(this@MainActivity,7),0,0)})};disclosure.text="Скрыть расписание  ↑"
  }}
  c.addView(disclosure);c.addView(preview);body.addView(c)
 }
 private fun singleCard(a:DAlarm){val c=Ui.card(this);val top=Ui.row(this);val info=Ui.column(this)
  info.addView(Ui.text(this,"%02d:%02d".format(a.hour,a.minute),30f,if(a.enabled)Ui.WHITE else Ui.muted,true));info.addView(Ui.space(this,3));info.addView(Ui.text(this,a.title,14f,Ui.gold,true));info.addView(Ui.space(this,5));info.addView(small(daysLabel(a.days)))
  top.addView(info,LinearLayout.LayoutParams(0,-2,1f));val toggle=Ui.switch(this,a.enabled);toggle.setOnCheckedChangeListener{_,enabled->val updated=a.copy(enabled=enabled);AlarmStore.save(this,AlarmStore.all(this).map{if(it.id==a.id)updated else it});AlarmStore.schedule(this,updated);home()};top.addView(toggle);c.addView(top)
  c.addView(Ui.space(this,13));val actions=Ui.row(this);actions.addView(Ui.outlineButton(this,"Изменить"){singleEditor(a)},LinearLayout.LayoutParams(0,-2,1f));actions.addView(Ui.space(this,10));actions.addView(Ui.outlineButton(this,"Удалить"){confirm("Удалить будильник?",a.title){AlarmStore.cancel(this,a);AlarmStore.save(this,AlarmStore.all(this).filter{it.id!=a.id});home()}},LinearLayout.LayoutParams(0,-2,1f));c.addView(actions);body.addView(c)
 }
 private fun confirm(title:String,bodyText:String,yes:()->Unit){AlertDialog.Builder(this).setTitle(title).setMessage(bodyText).setNegativeButton("Отмена",null).setPositiveButton("Удалить"){_,_->yes()}.show()}
 private fun daySelector(initial:Int,onChange:(Int)->Unit):LinearLayout{
  var mask=initial;val row=Ui.row(this);val labels=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
  labels.forEachIndexed{i,t->val view=Ui.text(this,t,12f,Ui.WHITE,true).apply{gravity=Gravity.CENTER;minHeight=Ui.dp(this@MainActivity,41)}
   fun paint(){view.background=Ui.shape(if(mask and (1 shl i)!=0)Ui.blue else 0xd9283547.toInt(),Ui.dp(this@MainActivity,10).toFloat());view.setTextColor(if(mask and (1 shl i)!=0)0xff0d192b.toInt()else Ui.muted)}
   paint();Ui.interactive(this,view){mask=mask xor (1 shl i);paint();onChange(mask)}
   val lp=LinearLayout.LayoutParams(0,Ui.dp(this,41),1f);if(i>0)lp.marginStart=Ui.dp(this,5);row.addView(view,lp)
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
  val p=TimePicker(this).apply{setIs24HourView(true);hour=a?.hour?:7;minute=a?.minute?:0};body.addView(p);addGap(12)
  val title=textInput(a?.title?:"Будильник","Название");option("НАЗВАНИЕ",title)
  body.addView(Ui.text(this,"ДНИ НЕДЕЛИ",13f,Ui.muted));addGap(9);var days=a?.days?:0;body.addView(daySelector(days){days=it});addGap(19)
  body.addView(Ui.outlineButton(this,"♪  Выбрать мелодию"){chooseSound()});addGap(16)
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
  val t=TimePicker(this).apply{setIs24HourView(true);hour=source.start/60;minute=source.start%60};body.addView(Ui.text(this,"ПЕРВЫЙ СИГНАЛ",13f,Ui.muted));body.addView(t);addGap(12)
  var days=source.days
  body.addView(Ui.text(this,"ДНИ НЕДЕЛИ",13f,Ui.muted));addGap(9);body.addView(daySelector(days){days=it});addGap(20)
  val intervals=listOf(5,10,15,20,30,45,60);val interval=picker(intervals,source.step){"Каждые $it мин"};option("ИНТЕРВАЛ",interval)
  val counts=(2..24).toList();val count=picker(counts,source.count){"$it сигналов"};option("КОЛИЧЕСТВО",count)
  val preview=Ui.card(this);preview.addView(Ui.text(this,"ПРЕДПРОСМОТР",11f,Ui.gold,true));val content=Ui.text(this,"",15f,Ui.WHITE)
  preview.addView(Ui.space(this,8));preview.addView(content);body.addView(preview);addGap(17)
  fun updatePreview(){val step=intervals[interval.selectedItemPosition.coerceAtLeast(0)];val n=counts[count.selectedItemPosition.coerceAtLeast(0)];val start=t.hour*60+t.minute;val times=(0 until n).map{time((start+it*step)%1440)};content.text=times.chunked(4).joinToString("\n"){it.joinToString("     ")}}
  t.setOnTimeChangedListener{_,_,_->updatePreview()}
  val update=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){updatePreview()}
  };interval.onItemSelectedListener=update;count.onItemSelectedListener=update;updatePreview()
  val vibrate=Ui.switch(this,source.vibrate);val v=Ui.row(this);v.addView(Ui.text(this,"Вибрация",15f),LinearLayout.LayoutParams(0,-2,1f));v.addView(vibrate);body.addView(v);addGap(13)
  body.addView(Ui.outlineButton(this,"♪  Мелодия для всей серии"){chooseSound()});addGap(17)
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
  body.addView(small("На POCO / HyperOS также разреши автозапуск и работу без ограничений батареи. Иначе система может задерживать запуск будильников."));addGap(23)
  body.addView(small("DARLY 2.5.0 · Cinematic Edition"))
 }
 private fun themes(){setupScreen("themes","Оформление","Реалистичные пейзажи и авторские игровые арты",true)
  val selected=Ui.selectedTheme(this)
  listOf("Neo Dark","Deadlock","Minecraft","Сезонная","Весна","Лето","Осень","Зима").forEach{name->
   val c=Ui.card(this)
   val image=ImageView(this).apply{setImageResource(when(name){"Deadlock"->R.drawable.darly_deadlock;"Minecraft"->R.drawable.darly_minecraft;"Весна"->R.drawable.darly_spring;"Лето"->R.drawable.darly_summer;"Осень"->R.drawable.darly_autumn;"Зима"->R.drawable.darly_winter;"Сезонная"->Ui.background(this@MainActivity);else->R.drawable.darly_neo});scaleType=ImageView.ScaleType.CENTER_CROP}
   c.addView(image,LinearLayout.LayoutParams(-1,Ui.dp(this,165)));c.addView(Ui.space(this,12))
   c.addView(Ui.text(this,(if(name==selected) "✓  " else "")+name,18f,Ui.WHITE,true))
   c.addView(Ui.space(this,5));c.addView(small(when(name){"Neo Dark"->"Ночной город · неон";"Deadlock"->"Атмосферный городской арт";"Minecraft"->"Игровой арт · блочный мир";"Сезонная"->"Пейзаж сменяется автоматически по времени года";else->"Кинематографичный сезонный пейзаж"}))
   Ui.interactive(this,c){prefs().edit().putString("theme",name).apply();Ui.applyTheme(this);themes()}
   body.addView(c);addGap()
  }
 }
 override fun onBackPressed(){home()}
}
