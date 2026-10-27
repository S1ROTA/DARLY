package com.darly.alarm

/** Yandex-derived station index is a temporary, local performance cache. */
object RailCachePolicy {
 const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
 fun shouldRefresh(lastModified:Long,now:Long):Boolean =
  lastModified<=0L || lastModified>now || now-lastModified>=MAX_AGE_MS
}
