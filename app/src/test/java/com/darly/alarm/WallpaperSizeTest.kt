package com.darly.alarm

import org.junit.Assert.*
import org.junit.Test

class WallpaperSizeTest {
 @Test fun preservesNativeResolution(){assertEquals(1080 to 2400,WallpaperSize.target(1080,2400,1080,2400))}
 @Test fun portraitDoesNotDropBelowScreen(){
  val (w,h)=WallpaperSize.target(3024,4032,1080,2400)
  assertTrue(w>=1080);assertTrue(h>=2400)
  assertTrue(w.toLong()*h<=16_000_000L)
 }
 @Test fun capsLargeCameraImage(){
  val (w,h)=WallpaperSize.target(10000,8000,1080,2400)
  assertTrue(w.toLong()*h<=16_000_000L)
  assertTrue(h>=2400)
 }
 @Test fun neverUpscalesSmallOriginal(){
  assertEquals(400 to 600,WallpaperSize.target(400,600,1080,2400))
 }
}
