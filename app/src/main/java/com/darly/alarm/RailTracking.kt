package com.darly.alarm

import android.app.*
import android.content.*
import android.os.Build
import org.json.JSONObject
import java.time.OffsetDateTime

/** One selected journey, stored privately; timetable tracking, not GPS/live train location. */
object RailTracking {
 private const val ID=9092
 private fun prefs(c:Context)=c.getSharedPreferences("rail_tracking",0)
 private fun stored(c:Context)=runCatching{JSONObject(prefs(c).getString("journey",null)?:return@runCatching null)}.getOrNull()
 fun selected(c:Context,t:YandexRaspApi.Train,from:YandexRaspApi.Station?=null,to:YandexRaspApi.Station?=null)=stored(c)?.let{it.optString("uid")==t.uid&&it.optLong("departure")==t.departureAt&&(from==null||it.optString("fromCode")==from.code)&&(to==null||it.optString("toCode")==to.code)}?:false
 fun track(c:Context,t:YandexRaspApi.Train,from:YandexRaspApi.Station,to:YandexRaspApi.Station,date:String){
  val arrival=runCatching{OffsetDateTime.parse(t.arrival).toInstant().toEpochMilli()}.getOrElse{t.departureAt+t.durationSeconds.coerceAtLeast(60)*1000L}
  val j=JSONObject().put("uid",t.uid).put("departure",t.departureAt).put("arrivalAt",arrival).put("departureText",t.departure).put("arrival",t.arrival).put("duration",t.durationSeconds).put("number",t.number).put("title",t.title).put("fromCode",from.code).put("from",from.title).put("toCode",to.code).put("to",to.title).put("date",date)
  j.put("fares",org.json.JSONArray().apply{t.fares.forEach{put(JSONObject().put("name",it.name).put("currency",it.currency).put("minor",it.minorUnits))}})
  prefs(c).edit().putString("journey",j.toString()).apply();sync(c)
 }
 private fun pending(c:Context,action:String)=PendingIntent.getBroadcast(c,ID,Intent(c,RailTrackingReceiver::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun clear(c:Context){prefs(c).edit().clear().apply();c.getSystemService(NotificationManager::class.java).cancel(ID);c.getSystemService(AlarmManager::class.java).cancel(pending(c,"REFRESH"))}
 fun sync(c:Context){
  val j=stored(c)?:return
  val now=System.currentTimeMillis();val departure=j.optLong("departure");val arrival=j.optLong("arrivalAt");val expires=arrival+15*60000L
  if(now>=expires){clear(c);return}
  val manager=c.getSystemService(NotificationManager::class.java)
  if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
  manager.createNotificationChannel(NotificationChannel("rail_tracking_v1","Отслеживаемая электричка",NotificationManager.IMPORTANCE_LOW))
  val storedFares=j.optJSONArray("fares")
  val fares=if(storedFares==null)emptyList()else (0 until storedFares.length()).mapNotNull{index->storedFares.optJSONObject(index)?.let{RailFare(it.optString("name"),it.optString("currency"),it.optLong("minor"))}}
  val train=YandexRaspApi.Train(j.optString("number"),j.optString("title"),j.optString("departureText"),j.optString("arrival").takeUnless{it.isBlank()||it=="null"},departure,j.optInt("duration"),uid=j.optString("uid"),fares=fares)
  val open=Intent(c,TrainDetailActivity::class.java).putExtra("train",train).putExtra("from",YandexRaspApi.Station(j.getString("fromCode"),j.getString("from"))).putExtra("to",YandexRaspApi.Station(j.getString("toCode"),j.getString("to"))).putExtra("date",j.getString("date"))
  val click=PendingIntent.getActivity(c,ID,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val status=if(now<departure)"До отправления" else if(now<arrival)"До прибытия по расписанию" else "Рейс прибыл по расписанию"
  val details="№ ${train.number} · ${train.labelTime} → ${train.arrivalTime} · $status"
  val notification=Notification.Builder(c,"rail_tracking_v1").setSmallIcon(R.drawable.ic_rail_nav).setContentTitle("${j.getString("from")} → ${j.getString("to")}").setContentText(details).setStyle(Notification.BigTextStyle().bigText(details+"\nДанные расписания; положение поезда не отслеживается.")).setContentIntent(click).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_STATUS).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Не отслеживать",pending(c,"CLEAR"))
  if(now<arrival)notification.setWhen(if(now<departure)departure else arrival).setUsesChronometer(true).setChronometerCountDown(true)
  manager.notify(ID,notification.build())
  c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,if(now<departure)departure else if(now<arrival)arrival else expires,pending(c,"REFRESH"))
 }
}
class RailTrackingReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(i.action=="CLEAR")RailTracking.clear(c)else RailTracking.sync(c)}}
