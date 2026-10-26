package com.darly.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PresetStore {
 private fun prefs(c:Context)=c.getSharedPreferences("darly_presets",0)
 fun all(c:Context):List<DAlarm> = try {
  val array=JSONArray(prefs(c).getString("items","[]"))
  (0 until array.length()).map { i-> val j=array.getJSONObject(i)
   DAlarm(j.getInt("id"),j.getInt("hour"),j.getInt("minute"),j.getInt("days"),j.optString("sound"),true,j.optString("title","Будильник"),j.optBoolean("vibrate",true),j.optInt("snooze",5),j.optInt("fade",30)) }
 }catch(_:Exception){emptyList()}
 private fun save(c:Context,items:List<DAlarm>){val array=JSONArray();items.forEach{a->array.put(JSONObject().put("id",a.id).put("hour",a.hour).put("minute",a.minute).put("days",a.days).put("sound",a.sound).put("title",a.title).put("vibrate",a.vibrate).put("snooze",a.snooze).put("fade",a.fade))};prefs(c).edit().putString("items",array.toString()).apply()}
 fun add(c:Context,a:DAlarm){val previous=all(c);val id=(previous.maxOfOrNull{it.id}?:0)+1;save(c,previous+a.copy(id=id))}
 fun delete(c:Context,id:Int)=save(c,all(c).filter{it.id!=id})
}
