package com.darly.alarm

import org.junit.Assert.*
import org.junit.Test

class RailInputRulesTest {
 @Test fun acceptsRailwayStationsRegardlessOfPrimaryRailService(){
  listOf("train","suburban","Поезд","Электричка"," TRAIN ").forEach{assertTrue(it,RailInputRules.isRailTransport(it))}
  listOf("bus","Автобус","plane","").forEach{assertFalse(it,RailInputRules.isRailTransport(it))}
 }
 @Test fun pastedInstructionsCannotBecomeAnHttpHeader(){
  assertNotNull(RailInputRules.apiKeyError("API-ключ: вставьте ключ"))
  assertNotNull(RailInputRules.apiKeyError("key\r\nAuthorization: other"))
  assertNotNull(RailInputRules.apiKeyError("key with spaces"))
  assertNotNull(RailInputRules.apiKeyError(""))
  assertNull(RailInputRules.apiKeyError(" 01234567-abcd-abcd-abcd-0123456789ab "))
 }
 @Test fun stationSearchNormalizesYoWhitespaceAndDashes(){
  assertEquals(RailInputRules.searchable("Орёл–Лужки"),RailInputRules.searchable("  ОРЕЛ-ЛУЖКИ "))
  assertEquals("московская область",RailInputRules.searchable("Московская   область"))
  assertEquals("москва ярославский вокзал",RailInputRules.searchable("Москва (Ярославский вокзал)"))
 }
}
