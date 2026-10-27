package com.darly.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Favorites are schedules-only; no AlarmStore dependency. */
object RailFavorites {
 data class Route(val from:YandexRaspApi.Station,val to:YandexRaspApi.Station)
 private fun prefs(c:Context)=c.getSharedPreferences("rail_schedules",Context.MODE_PRIVATE)
 fun all(c:Context):List<Route> = runCatching {
  val array=JSONArray(prefs(c).getString("favorite_routes","[]")?:"[]")
  (0 until array.length()).mapNotNull{idx->
   val r=array.optJSONObject(idx)?:return@mapNotNull null
   val f=r.optJSONObject("from")?:return@mapNotNull null
   val t=r.optJSONObject("to")?:return@mapNotNull null
   val from=YandexRaspApi.Station(f.getString("code"),f.optString("title"),f.optString("settlement"))
   val to=YandexRaspApi.Station(t.getString("code"),t.optString("title"),t.optString("settlement"))
   Route(from,to)
  }
 }.getOrDefault(emptyList())
 private fun write(c:Context,routes:List<Route>){
  fun station(s:YandexRaspApi.Station)=JSONObject().put("code",s.code).put("title",s.title).put("settlement",s.settlement)
  val array=JSONArray()
  routes.forEach{array.put(JSONObject().put("from",station(it.from)).put("to",station(it.to)))}
  prefs(c).edit().putString("favorite_routes",array.toString()).apply()
 }
 fun contains(c:Context,from:YandexRaspApi.Station,to:YandexRaspApi.Station):Boolean=
  all(c).any{it.from.code==from.code && it.to.code==to.code}
 fun toggle(c:Context,from:YandexRaspApi.Station,to:YandexRaspApi.Station){
  val saved=all(c).toMutableList()
  if(saved.any{it.from.code==from.code && it.to.code==to.code})saved.removeAll{it.from.code==from.code && it.to.code==to.code}
  else saved.add(0,Route(from,to))
  write(c,saved.take(30))
 }
}
