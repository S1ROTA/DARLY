package com.darly.alarm

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Decode near the device's fullscreen display resolution, with a 12% overscan
 * for the slow wallpaper zoom. Keeps pixel memory bounded for huge photos. */
object WallpaperSize {
 fun target(width:Int,height:Int,screenWidth:Int,screenHeight:Int):Pair<Int,Int> {
  require(width>0 && height>0 && screenWidth>0 && screenHeight>0)
  val cover=max(screenWidth*1.12 / width,screenHeight*1.12 / height).coerceAtMost(1.0)
  val memoryScale=sqrt(16_000_000.0/(width.toDouble()*height)).coerceAtMost(1.0)
  val scale=min(cover,memoryScale)
  return max(1,(width*scale).roundToInt()) to max(1,(height*scale).roundToInt())
 }
}
