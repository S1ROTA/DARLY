package com.darly.alarm

data class WalkingEstimate(val meters:Double,val minutes:Double,val activeKcal:Double)
data class WalkingProfile(val weightKg:Int,val heightCm:Int,val age:Int,val strideCm:Int){
 init{require(weightKg in 20..300 && heightCm in 80..250 && age in 6..120 && strideCm in 20..150)}
 companion object {
  /** Initial approximation only; users can replace it with their measured step length. */
  fun initialStride(heightCm:Int):Int{require(heightCm in 80..250);return kotlin.math.round(heightCm*0.4).toInt()}
 }
}

/** Estimates for level walking at 4 km/h (3 MET), not GPS, duration or energy measurements. */
object WalkingEstimates {
 fun calculate(steps:Long,strideCm:Int,weightKg:Int):WalkingEstimate {
  require(strideCm in 20..150 && weightKg in 20..300)
  val meters=steps.coerceAtLeast(0).toDouble()*strideCm/100.0
  val hours=meters/4000.0
  // Net energy above resting expenditure: (MET - 1) × kg × hours.
  return WalkingEstimate(meters,hours*60,2.0*weightKg*hours)
 }
}
