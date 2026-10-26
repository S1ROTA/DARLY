package com.darly.alarm

/** Pure scheduling rules shared by production and unit tests. No Android dependencies. */
object SeriesSchedule {
 data class Slot(val minuteOfDay:Int,val dayOffset:Int){
  val label:String get()="%02d:%02d".format(minuteOfDay/60,minuteOfDay%60)
 }
 fun slots(start:Int,step:Int,count:Int):List<Slot>{
  require(start in 0..1439){"Start must be a local clock minute"}
  require(step in 1..120){"Interval must be 1..120 minutes"}
  require(count in 2..24){"A series must contain 2..24 alarms"}
  return (0 until count).map{val total=start+step*it;Slot(total%1440,total/1440)}
 }
 fun shiftDays(mask:Int,offset:Int):Int{
  require(mask in 0..127)
  if(mask==0)return 0
  val shift=((offset%7)+7)%7
  if(shift==0)return mask
  return ((mask shl shift) or (mask ushr (7-shift))) and 127
 }
}
