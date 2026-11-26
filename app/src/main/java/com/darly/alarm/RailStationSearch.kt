package com.darly.alarm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Trace
import java.util.concurrent.Executors

/** Disk reads, first index normalization and filtering never run on the UI thread. */
object RailStationSearch {
 private val worker=Executors.newSingleThreadExecutor{task->Thread(task,"DARLY-station-search")}
 private val main=Handler(Looper.getMainLooper())
 data class Result(val available:Boolean,val matches:List<YandexRaspApi.Station>)
 fun search(context:Context,query:String,callback:(Result)->Unit){
  val app=context.applicationContext
  worker.execute{
   Trace.beginSection("DARLY.stationSearch")
   val result=try{
    val all=RailStationCache.all(app)
    Result(all.isNotEmpty(),RailStationCache.find(all,query))
   }finally{Trace.endSection()}
   main.post{callback(result)}
  }
 }
}
