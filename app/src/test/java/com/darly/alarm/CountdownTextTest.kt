package com.darly.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownTextTest {
 @Test fun hoursAndMinutes(){assertEquals("Через 2 ч 10 мин",CountdownText.until(7_800_000,0))}
 @Test fun nextMinuteRoundsUp(){assertEquals("Через 2 мин",CountdownText.until(60001,0))}
 @Test fun secondsNearRing(){assertEquals("Через 9 сек",CountdownText.until(8901,0))}
 @Test fun tomorrow(){assertEquals("Через 1 д 3 ч",CountdownText.until(27L*3600_000,0))}
 @Test fun pastAlarm(){assertEquals("Сейчас",CountdownText.until(1000,2000))}
 @Test fun exactMinute(){assertEquals("Через 1 мин",CountdownText.until(60000,0))}
}
