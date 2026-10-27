package com.darly.alarm

import android.util.JsonReader
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Official Yandex Timetables API v3.0. No token is embedded into the APK. */
object YandexRaspApi {
 const val BASE="https://api.rasp.yandex-net.ru/v3.0/"
 const val ATTRIBUTION="Данные предоставлены сервисом Яндекс.Расписания"
 const val ATTRIBUTION_URL="https://rasp.yandex.ru/"
 private const val REFERRER="http://com.darly.alarm.yraspapp"
 data class Station(val code:String,val title:String,val settlement:String=""){
  val label:String get()=if(settlement.isNotEmpty() && settlement!=title)"$title · $settlement" else title
 }
 data class Train(val number:String,val title:String,val departure:String,val arrival:String?,val departureAt:Long,val durationSeconds:Int){
  val labelTime:String get()=runCatching {OffsetDateTime.parse(departure).format(DateTimeFormatter.ofPattern("HH:mm"))}.getOrElse {departure.takeLast(5)}
  val arrivalTime:String get()=arrival?.let {runCatching {OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("HH:mm"))}.getOrNull()} ?: "—"
 }
 data class Search(val trains:List<Train>,val total:Int,val offset:Int,val intervalCount:Int)
 class RequestFailure(message:String):Exception(message)

 private fun enc(value:String)=URLEncoder.encode(value,"UTF-8")
 private fun connection(url:String,apiKey:String):HttpURLConnection {
  require(apiKey.isNotBlank()){"Нужен ключ Яндекс Расписаний"}
  val con=URL(url).openConnection() as HttpURLConnection
  con.requestMethod="GET"
  con.connectTimeout=12000
  con.readTimeout=25000
  con.setRequestProperty("Authorization",apiKey.trim())
  con.setRequestProperty("Referer",REFERRER)
  con.setRequestProperty("Accept","application/json")
  con.setRequestProperty("Accept-Language","ru-RU")
  con.setRequestProperty("Accept-Encoding","gzip")
  return con
 }
 private fun open(con:HttpURLConnection):java.io.InputStream {
  val status=con.responseCode
  if(status!=200){
   val short=when(status){401,403->"Ключ API не принят или доступ запрещён";429->"Превышен лимит запросов к Яндексу";else->"Ошибка Яндекс Расписаний: HTTP $status"}
   throw RequestFailure(short)
  }
  val stream=BufferedInputStream(con.inputStream)
  return if(con.contentEncoding?.equals("gzip",true)==true)java.util.zip.GZIPInputStream(stream) else stream
 }
 private fun getJson(path:String,apiKey:String,query:Map<String,String>):JSONObject {
  val qs=(query+mapOf("format" to "json","lang" to "ru_RU")).entries.joinToString("&"){"${enc(it.key)}=${enc(it.value)}"}
  val con=connection(BASE+path+"?"+qs,apiKey)
  try{
   return open(con).use{stream->
    val content=stream.bufferedReader(Charsets.UTF_8).use{reader->reader.readText()}
    JSONObject(content)
   }
  }catch(e:RequestFailure){throw e}
   catch(e:Exception){throw RequestFailure("Не удалось получить расписание: ${e.message ?: "ошибка сети"}")}
  finally{con.disconnect()}
 }
 fun search(apiKey:String,origin:Station,destination:Station,date:String,offset:Int=0):Search {
  val result=getJson("search/",apiKey,mapOf(
   "from" to origin.code,"to" to destination.code,"date" to date,
   "transport_types" to "suburban","transfers" to "false",
   "limit" to "50","offset" to offset.toString()))
  val pagination=result.optJSONObject("pagination")
  val segments=result.optJSONArray("segments")
  val trains=mutableListOf<Train>()
  if(segments!=null)for(i in 0 until segments.length()){
   val s=segments.optJSONObject(i)?:continue
   val departure=s.optString("departure")
   val instant=runCatching{OffsetDateTime.parse(departure).toInstant().toEpochMilli()}.getOrNull()?:continue
   val thread=s.optJSONObject("thread")
   trains.add(Train(thread?.optString("number").orEmpty(),thread?.optString("title").orEmpty(),departure,
    s.optString("arrival").takeIf{it.isNotBlank()&&it!="null"},instant,s.optInt("duration",0)))
  }
  val intervalCount=result.optJSONArray("interval_segments")?.length()?:0
  return Search(trains.sortedBy{it.departureAt},pagination?.optInt("total",trains.size)?:trains.size,offset,intervalCount)
 }
 /** Stations list can be ~40 MB. It is parsed streaming without a 40 MB string/JSON object. */
 fun loadStations(apiKey:String):List<Station> {
  val con=connection(BASE+"stations_list/?format=json&lang=ru_RU",apiKey)
  try{
   val found=LinkedHashMap<String,Station>()
   open(con).use{stream->InputStreamReader(stream,Charsets.UTF_8).use{reader->
    JsonReader(reader).use{json->parseRoot(json,found)}
   }}
   return found.values.toList()
  }finally{con.disconnect()}
 }
 private fun parseRoot(r:JsonReader,out:MutableMap<String,Station>){
  r.beginObject();while(r.hasNext())when(r.nextName()){
   "countries"->{r.beginArray();while(r.hasNext())parseCountry(r,out);r.endArray()}
   else->r.skipValue()
  };r.endObject()
 }
 private fun parseCountry(r:JsonReader,out:MutableMap<String,Station>){
  r.beginObject();while(r.hasNext())when(r.nextName()){
   "regions"->{r.beginArray();while(r.hasNext())parseRegion(r,out);r.endArray()}
   else->r.skipValue()
  };r.endObject()
 }
 private fun parseRegion(r:JsonReader,out:MutableMap<String,Station>){
  r.beginObject();while(r.hasNext())when(r.nextName()){
   "settlements"->{r.beginArray();while(r.hasNext())parseSettlement(r,out);r.endArray()}
   else->r.skipValue()
  };r.endObject()
 }
 private fun parseSettlement(r:JsonReader,out:MutableMap<String,Station>){
  var settlement="";val stationList=mutableListOf<Station>()
  r.beginObject();while(r.hasNext())when(r.nextName()){
   "title"->settlement=readString(r)
   "stations"->{r.beginArray();while(r.hasNext())parseStation(r)?.let{stationList.add(it)};r.endArray()}
   else->r.skipValue()
  };r.endObject()
  stationList.forEach{out[it.code]=it.copy(settlement=settlement)}
 }
 private fun parseStation(r:JsonReader):Station?{
  var code="";var title="";var transport=""
  r.beginObject();while(r.hasNext())when(r.nextName()){
   "title"->title=readString(r)
   "transport_type"->transport=readString(r)
   "codes"->{r.beginObject();while(r.hasNext())when(r.nextName()){
    "yandex_code"->code=readString(r)
    else->r.skipValue()
   };r.endObject()}
   else->r.skipValue()
  };r.endObject()
  // The documented transport_type for commuter rail is "suburban".
  return if(code.startsWith("s") && title.isNotBlank() && (transport.equals("suburban",true)||transport.contains("электрич",true)))Station(code,title) else null
 }
 private fun readString(r:JsonReader):String=when(r.peek()){
  android.util.JsonToken.STRING->r.nextString()
  android.util.JsonToken.NUMBER->r.nextString()
  else->{r.skipValue();""}
 }
}
