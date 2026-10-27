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
 @Synchronized fun all(context:Context):List<YandexRaspApi.Station>{
  cached?.let{return it}
  val file=File(context.filesDir,FILE)
  if(!file.isFile)return emptyList()
  return try{
   DataInputStream(file.inputStream().buffered()).use{input->
    val count=input.readInt()
    if(count !in 0..MAX_STATIONS)throw IllegalStateException("Wrong station catalog size")
    (0 until count).map{YandexRaspApi.Station(input.readUTF(),input.readUTF(),input.readUTF())}
   }.also{cached=it}
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
   cached=stations
  }catch(e:Exception){temp.delete();throw e}
 }
 fun find(stations:List<YandexRaspApi.Station>,query:String,limit:Int=35):List<YandexRaspApi.Station>{
  val s=query.trim().lowercase(Locale.ROOT)
  if(s.length<2)return emptyList()
  return stations.asSequence().filter{
   it.title.lowercase(Locale.ROOT).contains(s)||it.settlement.lowercase(Locale.ROOT).contains(s)||it.code.lowercase(Locale.ROOT)==s
  }.take(limit).toList()
 }
}
