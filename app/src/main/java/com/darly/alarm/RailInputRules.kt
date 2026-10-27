package com.darly.alarm

import java.util.Locale

object RailInputRules {
 fun apiKeyError(value:String):String? {
  val key=value.trim()
  return when {
   key.isEmpty()->"Введи API-ключ Яндекс Расписаний"
   key.length>512 || key.any{it.code !in 33..126}->"Вставь только API-ключ, без пробелов, переносов строк и поясняющего текста"
   else->null
  }
 }
 fun searchable(value:String)=value.lowercase(Locale.ROOT).replace('ё','е').replace(Regex("[\\s\\p{P}]+")," ").trim()
 fun isRailTransport(value:String):Boolean {
  val type=value.trim().lowercase(Locale.ROOT)
  return type=="train" || type=="suburban" || type.startsWith("поезд") || type.contains("электрич")
 }
}
