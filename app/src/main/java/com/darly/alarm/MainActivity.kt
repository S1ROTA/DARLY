package com.darly.alarm

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import java.util.Calendar

class MainActivity: Activity() {
 private val bg=Color.rgb(12,13,19); private val gold=Color.rgb(237,190,116)
 private lateinit var root:LinearLayout
 private var selectedSound=""
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data); if(requestCode==17 && resultCode==RESULT_OK) { data?.data?.let { uri -> try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}; selectedSound=uri.toString(); toast("Мелодия выбрана") } } }

 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=bg; window.navigationBarColor=bg; if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),3); AlarmStore.exactPermission(this); showHome() }
 override fun onResume() { super.onResume(); if(::root.isInitialized) showHome() }
 private fun label(t:String,size:Float=18f,color:Int=Color.WHITE)=TextView(this).apply { text=t; textSize=size; setTextColor(color); gravity=Gravity.CENTER_VERTICAL; setPadding(8,14,8,14) }
 private fun button(t:String, action:()->Unit)=Button(this).apply { text=t; setTextColor(bg); setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold)); setOnClickListener{action()} }
 private fun base(title:String) { root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(26,35,26,20); setBackgroundColor(bg) }; setContentView(ScrollView(this).apply { fillViewport=true; addView(root) }); root.addView(label(title,33f,gold)) }
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
 private fun showHome() { base("DARLY  ☾"); root.addView(label("Твоё время. Твой ритм.",15f,gold)); root.addView(label("БУДИЛЬНИКИ",13f,Color.LTGRAY)); val list=AlarmStore.all(this)
  if(list.isEmpty()) root.addView(label("Пока нет будильников. Создай первый ✨",17f))
  list.forEach { a -> val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,10,12,10)}; row.addView(label("%02d:%02d".format(a.hour,a.minute),38f)); row.addView(label(if(a.days==0) "Однократно" else "Повтор по дням",14f,gold)); val controls=LinearLayout(this); controls.addView(button(if(a.enabled) "Выключить" else "Включить") { val changed=list.map{if(it.id==a.id) it.copy(enabled=!it.enabled) else it}; AlarmStore.save(this,changed); AlarmStore.schedule(this,changed.first{it.id==a.id}); showHome() }); controls.addView(button("Удалить") { AlarmStore.schedule(this,a.copy(enabled=false)); AlarmStore.save(this,list.filter{it.id!=a.id}); showHome() }); row.addView(controls); root.addView(row) }
  root.addView(button("＋ Новый будильник") { showEditor() })
 }
 private fun showEditor() { selectedSound=""; base("Новый будильник"); val time=TimePicker(this).apply{setIs24HourView(true); val c=Calendar.getInstance(); hour=c.get(Calendar.HOUR_OF_DAY); minute=c.get(Calendar.MINUTE)}; root.addView(time)
  root.addView(label("Повторять по дням",17f,gold)); val names=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс"); val checks=names.map{ CheckBox(this).apply{text=it;setTextColor(Color.WHITE);buttonTintList=android.content.res.ColorStateList.valueOf(gold)} }; val daysRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; checks.forEach{ daysRow.addView(it,LinearLayout.LayoutParams(0,60,1f)) }; root.addView(daysRow)
  root.addView(button("♫ Выбрать свою мелодию") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"),17) }); root.addView(label("Если мелодия не выбрана, прозвучит стандартный сигнал.",13f,Color.LTGRAY))
  root.addView(button("Сохранить будильник") { val days=checks.foldIndexed(0){i,acc,box->if(box.isChecked)acc or (1 shl i) else acc}; val a=DAlarm((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),time.hour,time.minute,days,selectedSound); val updated=AlarmStore.all(this)+a; AlarmStore.save(this,updated); val scheduled=AlarmStore.schedule(this,a); toast(if(scheduled) "Будильник сохранён" else "Будильник сохранён, но требуется разрешение на точные сигналы"); showHome() }); root.addView(button("Назад") {showHome()})
 }
}
