package com.darly.alarm

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale

/** Compact private on-device catalog. The 40 MB network response is never stored as a huge JSON object. */
object RailStationCache {
 private const val FILE="suburban_stations_v1.bin"
 private const val MAX_STATIONS=120000
 private var cached:List<YandexRaspApi.Station>?=null
 private var indexed:List<Triple<YandexRaspApi.Station,String,String>>?=null
 fun needsRefresh(context:Context):Boolean = RailCachePolicy.shouldRefresh(File(context.filesDir,FILE).lastModified(),System.currentTimeMillis())
 @Synchronized fun all(context:Context):List<YandexRaspApi.Station>{
  cached?.let{return it}
  val file=File(context.filesDir,FILE)
  if(!file.isFile)return emptyList()
  return try{
   DataInputStream(file.inputStream().buffered()).use{input->
    val count=input.readInt()
    if(count !in 0..MAX_STATIONS)throw IllegalStateException("Wrong station catalog size")
    (0 until count).map{YandexRaspApi.Station(input.readUTF(),input.readUTF(),input.readUTF())}
   }.also{cached=it;indexed=null}
  }catch(_:Exception){emptyList()}
 }
 @Synchronized fun replace(context:Context,stations:List<YandexRaspApi.Station>){
  require(stations.size<=MAX_STATIONS)
  val dest=File(context.filesDir,FILE)
  val temp=File(context.filesDir,"$FILE.tmp")
  try{
   DataOutputStream(temp.outputStream().buffered()).use{output->
    output.writeInt(stations.size)
    stations.forEach{output.writeUTF(it.code);output.writeUTF(it.title);output.writeUTF(it.settlement)}
   }
   if(!temp.renameTo(dest)){
    temp.copyTo(dest,overwrite=true)
    temp.delete()
   }
   cached=stations;indexed=null
  }catch(e:Exception){temp.delete();throw e}
 }
 fun find(stations:List<YandexRaspApi.Station>,query:String,limit:Int=16):List<YandexRaspApi.Station>{
  val q=query.trim().lowercase(Locale.ROOT)
  if(q.length<2||limit<=0)return emptyList()
  // Cache searchable lowercase labels; avoid lowercasing tens of thousands of
  // entries on every keystroke. Prefer names beginning with the query.
  val index=synchronized(this){
   indexed?.takeIf{it.size==stations.size}?:stations.map{
    Triple(it,it.title.lowercase(Locale.ROOT),(it.title+" · "+it.settlement).lowercase(Locale.ROOT))
   }.also{indexed=it}
  }
  val result=ArrayList<YandexRaspApi.Station>(limit)
  for((station,title,_) in index){
   if(title.startsWith(q)||station.code.equals(q,true)){
    result.add(station);if(result.size==limit)return result
   }
  }
  for((station,_,label) in index){
   if(label.contains(q)&&result.none{it.code==station.code}){
    result.add(station);if(result.size==limit)break
   }
  }
  return result
 }
}
