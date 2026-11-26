package com.darly.alarm

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object StepStore {
 const val ACTION_CHANGED="com.darly.alarm.STEPS_CHANGED"
 private fun prefs(c:Context)=c.getSharedPreferences("activity_steps",0)
 fun enabled(c:Context)=prefs(c).getBoolean("enabled",false)
 fun goal(c:Context)=prefs(c).getInt("goal",8000).coerceIn(100,100000)
 fun walkingProfile(c:Context):WalkingProfile?=runCatching{
  val p=prefs(c)
  WalkingProfile(p.getInt("weight_kg",0),p.getInt("height_cm",0),p.getInt("age",0),p.getInt("stride_cm",0))
 }.getOrNull()
 fun setWalkingProfile(c:Context,profile:WalkingProfile){
  prefs(c).edit().putInt("stride_cm",profile.strideCm).putInt("weight_kg",profile.weightKg)
   .putInt("height_cm",profile.heightCm).putInt("age",profile.age).apply();changed(c)
 }
 fun error(c:Context)=prefs(c).getString("error",null)
 fun setError(c:Context,message:String?){prefs(c).edit().putString("error",message).apply();changed(c)}
 fun changed(c:Context){c.sendBroadcast(Intent(ACTION_CHANGED).setPackage(c.packageName))}
 @Synchronized fun ledger(c:Context):StepLedger=runCatching{
  val j=JSONObject(prefs(c).getString("ledger","{}")!!);val list=j.optJSONArray("days")?:JSONArray()
  StepLedger(j.optLong("counter",-1),j.optInt("boot",-1),j.optLong("nanos",-1),(0 until list.length()).mapNotNull{i->list.optJSONObject(i)?.let{StepDay(it.getString("date"),it.optLong("steps").coerceAtLeast(0),it.optInt("goal",8000).coerceIn(100,100000))}})
 }.getOrElse{StepLedger()}
 private fun write(c:Context,l:StepLedger){
  val days=JSONArray();l.days.forEach{days.put(JSONObject().put("date",it.date).put("steps",it.steps).put("goal",it.goal))}
  prefs(c).edit().putString("ledger",JSONObject().put("counter",l.counter).put("boot",l.boot).put("nanos",l.eventNanos).put("days",days).toString()).apply()
 }
 @Synchronized fun enable(c:Context){
  if(!enabled(c))write(c,ledger(c).copy(counter=-1,eventNanos=-1))
  prefs(c).edit().putBoolean("enabled",true).remove("error").apply();ensureToday(c);changed(c)
 }
 @Synchronized fun pause(c:Context){prefs(c).edit().putBoolean("enabled",false).apply();write(c,ledger(c).copy(counter=-1,eventNanos=-1));changed(c)}
 @Synchronized fun record(c:Context,value:Long,boot:Int,nanos:Long,date:String,notify:Boolean=true){
  if(!enabled(c))return
  val old=ledger(c);val updated=StepRules.record(old,value,boot,nanos,date,goal(c))
  if(updated!=old){write(c,updated);if(notify)changed(c)}
 }
 @Synchronized fun ensureToday(c:Context){
  if(!enabled(c))return
  val date=LocalDate.now().toString();val old=ledger(c)
  if(old.days.none{it.date==date})write(c,old.copy(days=(old.days+StepDay(date,0,goal(c))).sortedBy{it.date}.takeLast(366)))
 }
 @Synchronized fun setGoal(c:Context,value:Int){
  require(value in 100..100000)
  prefs(c).edit().putInt("goal",value).apply()
  val l=ledger(c);write(c,l.copy(days=l.days.map{if(it.date==LocalDate.now().toString())it.copy(goal=value)else it}));changed(c)
 }
 fun challenges(c:Context):List<StepChallenge> = runCatching{
  val list=JSONArray(prefs(c).getString("challenges","[]"));(0 until list.length()).map{i->list.getJSONObject(i).let{StepChallenge(it.getString("kind"),it.getString("start"),it.getInt("goal"))}}
 }.getOrElse{emptyList()}
 fun join(c:Context,kind:String){require(kind in setOf("walk","week","streak"));saveChallenges(c,challenges(c).filterNot{it.kind==kind}+StepChallenge(kind,LocalDate.now().toString(),goal(c)))}
 fun cancel(c:Context,kind:String)=saveChallenges(c,challenges(c).filterNot{it.kind==kind})
 private fun saveChallenges(c:Context,items:List<StepChallenge>){val list=JSONArray();items.forEach{list.put(JSONObject().put("kind",it.kind).put("start",it.start).put("goal",it.dailyGoal))};prefs(c).edit().putString("challenges",list.toString()).apply();changed(c)}
}
