package com.darly.alarm

import java.time.LocalDate

data class StepDay(val date:String,val steps:Long=0,val goal:Int=8000)
data class StepLedger(val counter:Long=-1,val boot:Int=-1,val eventNanos:Long=-1,val days:List<StepDay> = emptyList())
data class StepChallenge(val kind:String,val start:String,val dailyGoal:Int)
data class StepChallengeProgress(val value:Long,val target:Long,val completed:Boolean,val expired:Boolean,val end:LocalDate)

object StepRules {
 fun record(old:StepLedger,counter:Long,boot:Int,nanos:Long,date:String,goal:Int):StepLedger {
  if(counter<0||nanos<0)return old
  if(old.counter>=0&&old.boot==boot&&nanos<=old.eventNanos)return old
  val delta=if(old.counter>=0&&old.boot==boot&&counter>=old.counter)counter-old.counter else 0
  val existing=old.days.firstOrNull{it.date==date}
  val day=StepDay(date,(existing?.steps?:0)+delta,existing?.goal?:goal)
  val days=if(delta==0L&&existing==null)old.days else old.days.filterNot{it.date==date}+day
  return StepLedger(counter,boot,nanos,days.sortedBy{it.date}.takeLast(366))
 }
 fun streak(days:List<StepDay>,today:LocalDate):Int {
  val byDate=days.associateBy{it.date};var day=today
  if(byDate[day.toString()]?.let{it.steps>=it.goal}!=true)day=day.minusDays(1)
  var count=0
  while(byDate[day.toString()]?.let{it.steps>=it.goal}==true){count++;day=day.minusDays(1)}
  return count
 }
 fun challenge(c:StepChallenge,days:List<StepDay>,today:LocalDate):StepChallengeProgress {
  val start=LocalDate.parse(c.start);val duration=if(c.kind=="walk")1L else 7L
  val end=start.plusDays(duration)
  val selected=days.filter{it.date>=start.toString()&&it.date<end.toString()&&it.date<=today.toString()}
  val target=when(c.kind){"walk"->3000L;"week"->35000L;else->3L}
  val value=if(c.kind=="streak")selected.count{it.steps>=c.dailyGoal}.toLong() else selected.sumOf{it.steps}
  return StepChallengeProgress(value,target,value>=target,!today.isBefore(end)&&value<target,end)
 }
}
