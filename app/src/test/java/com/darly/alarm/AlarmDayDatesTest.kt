package com.darly.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AlarmDayDatesTest {
 @Test fun todayBeforeAlarm(){assertEquals("2026-10-27",AlarmDayDates.next(1,420,LocalDateTime.parse("2026-10-27T06:59:00")).toString())}
 @Test fun todayAtOrAfterAlarmMeansNextWeek(){assertEquals("2026-11-03",AlarmDayDates.next(1,420,LocalDateTime.parse("2026-10-27T07:00:00")).toString())}
 @Test fun yearBoundary(){assertEquals("2027-01-04",AlarmDayDates.next(0,420,LocalDateTime.parse("2026-12-31T12:00:00")).toString())}
 @Test fun mondayOrderIsIndependentOfLocale(){assertEquals("2026-11-01",AlarmDayDates.next(6,420,LocalDateTime.parse("2026-10-27T12:00:00")).toString())}
}
