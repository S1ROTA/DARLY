package com.darly.alarm

import java.math.BigDecimal
import java.util.Locale

data class RailFare(val name:String,val currency:String,val minorUnits:Long):java.io.Serializable {
 val label:String get(){
  val amount=BigDecimal.valueOf(minorUnits,2).stripTrailingZeros().toPlainString().replace('.',',')
  return "$amount ${when(currency){"RUB","RUR"->"₽";"EUR"->"€";"USD"->"$";else->currency}}"
 }
}
object RailJourneyRules {
 fun fare(name:String,currency:String,whole:Long,cents:Int):RailFare? {
  val code=currency.uppercase(Locale.ROOT)
  if(whole<0 || cents !in 0..99 || whole>(Long.MAX_VALUE-cents)/100 || !Regex("[A-Z]{3}").matches(code))return null
  return RailFare(name,code,whole*100+cents)
 }
 fun segment(codes:List<String>,from:String,to:String):IntRange? {
  val first=codes.indexOf(from)
  if(first<0)return null
  val last=(first+1 until codes.size).firstOrNull{codes[it]==to}?:return null
  return first..last
 }
 fun time(value:String?):String= value?.let{Regex("[T ]((?:[01][0-9]|2[0-3]):[0-5][0-9])").find(it)?.groupValues?.get(1)}?:"—"
}
