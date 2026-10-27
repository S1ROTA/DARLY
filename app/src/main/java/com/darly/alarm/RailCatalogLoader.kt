package com.darly.alarm

import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

/** One application-scoped download survives Activity recreation without retaining its views. */
object RailCatalogLoader {
 const val ACTION_CHANGED="com.darly.alarm.CATALOG_CHANGED"
 data class State(val loading:Boolean=false,val bytes:Long=0,val error:String?=null)
 @Volatile var state=State(); private set
 private val executor=Executors.newSingleThreadExecutor()
 private var generation=0L
 private var activeKey=""
 private var attemptedKey=""
 @Synchronized fun ensure(context:Context,key:String,force:Boolean=false){
  if(RailInputRules.apiKeyError(key)!=null)return
  if(state.loading && activeKey==key)return
  if(!force && (!RailStationCache.needsRefresh(context) || attemptedKey==key))return
  val app=context.applicationContext
  val token=++generation
  activeKey=key;attemptedKey=key
  publish(app,State(loading=true))
  executor.execute {
   var lastUpdate=0L
   val result=runCatching {
    val stations=YandexRaspApi.loadStations(key){bytes->
     val now=android.os.SystemClock.elapsedRealtime()
     synchronized(this){
      if(token!=generation)throw java.util.concurrent.CancellationException()
      if(now-lastUpdate>=750){lastUpdate=now;publish(app,State(true,bytes))}
     }
    }
    require(stations.isNotEmpty()){"В ответе нет железнодорожных станций"}
    synchronized(this){if(token==generation)RailStationCache.replace(app,stations)}
   }
   synchronized(this){if(token==generation){
    if(result.isSuccess)attemptedKey=""
    publish(app,State(error=result.exceptionOrNull()?.let {
    when(it){
     is YandexRaspApi.RequestFailure->it.message
     is java.net.SocketTimeoutException->"Сервер не ответил вовремя. Повтори загрузку."
     is java.io.IOException->"Не удалось скачать каталог. Проверь соединение и повтори загрузку."
     else->"Не удалось разобрать каталог станций. Повтори загрузку."
    }
   }))}}
  }
 }
 @Synchronized fun invalidate(context:Context){
  ++generation;activeKey="";attemptedKey="";publish(context.applicationContext,State())
 }
 private fun publish(c:Context,value:State){state=value;c.sendBroadcast(Intent(ACTION_CHANGED).setPackage(c.packageName))}
}
