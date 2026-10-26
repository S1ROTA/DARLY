package com.darly.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A series is an installed collection of independent AlarmManager alarms.
 * A preset is reusable configuration; applying it never changes an existing series. */
data class AlarmSeries(
 val id:Int=0, val title:String="Работа", val start:Int=270, val step:Int=10,
 val count:Int=8, val days:Int=31, val enabled:Boolean=true,
 val sound:String="",val vibrate:Boolean=true,val snooze:Int=5,val fade:Int=30
){
 init { require(start in 0..1439 && step in 1..120 && count in 2..24) }
 fun timeAt(index:Int):Int=(start+step*index)%1440
 fun dayOffsetAt(index:Int):Int=(start+step*index)/1440
 fun labelAt(index:Int):String="%02d:%02d".format(timeAt(index)/60,timeAt(index)%60)
}

object SeriesStore {
 private const val KEY="items"
 private fun prefs(c:Context)=c.getSharedPreferences("darly_series",0)
 private fun toJson(s:AlarmSeries)=JSONObject().put("id",s.id).put("title",s.title)
  .put("start",s.start).put("step",s.step).put("count",s.count)
  .put("days",s.days).put("enabled",s.enabled).put("sound",s.sound)
  .put("vibrate",s.vibrate).put("snooze",s.snooze).put("fade",s.fade)
 private fun fromJson(j:JSONObject)=AlarmSeries(
  j.optInt("id"),j.optString("title","Работа"),j.optInt("start",270),
  j.optInt("step",10).coerceIn(1,120),j.optInt("count",8).coerceIn(2,24),
  j.optInt("days",31),j.optBoolean("enabled",true),j.optString("sound"),
  j.optBoolean("vibrate",true),j.optInt("snooze",5),j.optInt("fade",30))
 private fun decode(raw:String?):List<AlarmSeries> =try{
  val a=JSONArray(raw?:"[]");(0 until a.length()).map{fromJson(a.getJSONObject(it))}
 }catch(_:Exception){emptyList()}
 fun all(c:Context):List<AlarmSeries> = decode(prefs(c).getString(KEY,"[]"))
 private fun save(c:Context,items:List<AlarmSeries>){val a=JSONArray();items.forEach{a.put(toJson(it))};prefs(c).edit().putString(KEY,a.toString()).apply();AlarmStore.notifyChanged(c)}
 private fun nextId(c:Context):Int=(all(c).maxOfOrNull{it.id}?:0)+1
 fun upsert(c:Context,input:AlarmSeries):AlarmSeries {
  val id=if(input.id>0)input.id else nextId(c)
  val s=input.copy(id=id,title=input.title.trim().ifBlank{"Серия будильников"})
  val old=AlarmStore.all(c).filter{it.groupId==id}.sortedBy{it.seriesIndex}
  val unchanged=AlarmStore.all(c).filter{it.groupId!=id}
  val used=AlarmStore.all(c).map{it.id}.toMutableSet()
  var next=(used.maxOrNull()?:0).coerceAtLeast(0)+1
  fun newAlarmId():Int { while(next in used || next<=0)next++;val result=next++;used.add(result);return result }
  val slots=SeriesSchedule.slots(s.start,s.step,s.count)
  val generated=slots.mapIndexed{i,slot->
   val oldId=old.getOrNull(i)?.id?:newAlarmId()
   val minute=slot.minuteOfDay
   // Shift weekday recurrence when a cascade crosses midnight.
   val days=SeriesSchedule.shiftDays(s.days,slot.dayOffset)
   DAlarm(oldId,minute/60,minute%60,days,s.sound,s.enabled,
    "${s.title} · ${i+1}",s.vibrate,s.snooze,s.fade,s.id,i)
  }
  old.drop(s.count).forEach{AlarmStore.cancel(c,it)}
  save(c,all(c).filter{it.id!=id}+s)
  AlarmStore.save(c,unchanged+generated)
  generated.forEach{AlarmStore.schedule(c,it)}
  return s
 }
 fun toggle(c:Context,id:Int,enabled:Boolean){
  val s=all(c).firstOrNull{it.id==id}?:return
  save(c,all(c).map{if(it.id==id)it.copy(enabled=enabled)else it})
  val updated=AlarmStore.all(c).map{if(it.groupId==id)it.copy(enabled=enabled)else it}
  AlarmStore.save(c,updated)
  updated.filter{it.groupId==id}.forEach{AlarmStore.schedule(c,it)}
 }
 fun delete(c:Context,id:Int){
  val old=AlarmStore.all(c);old.filter{it.groupId==id}.forEach{AlarmStore.cancel(c,it)}
  AlarmStore.save(c,old.filter{it.groupId!=id});save(c,all(c).filter{it.id!=id})
 }
 /** Separate reusable series templates. */
 fun templates(c:Context):List<AlarmSeries> =decode(c.getSharedPreferences("darly_series_templates",0).getString(KEY,"[]"))
 fun addTemplate(c:Context,s:AlarmSeries){
  val values=templates(c)
  val next=(values.maxOfOrNull{it.id}?:0)+1
  val a=JSONArray();(values+s.copy(id=next,enabled=true)).forEach{a.put(toJson(it))}
  c.getSharedPreferences("darly_series_templates",0).edit().putString(KEY,a.toString()).apply()
 }
 fun deleteTemplate(c:Context,id:Int){val a=JSONArray();templates(c).filter{it.id!=id}.forEach{a.put(toJson(it))};c.getSharedPreferences("darly_series_templates",0).edit().putString(KEY,a.toString()).apply()}
}
