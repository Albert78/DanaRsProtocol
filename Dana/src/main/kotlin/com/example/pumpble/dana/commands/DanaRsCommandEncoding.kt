package com.example.pumpble.dana.commands

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

internal fun encodeDanaDateTime(date: ZonedDateTime): ByteArray {
    return byteArrayOf(
        (date.year - 2000 and 0xff).toByte(),
        (date.monthValue and 0xff).toByte(),
        (date.dayOfMonth and 0xff).toByte(),
        (date.hour and 0xff).toByte(),
        (date.minute and 0xff).toByte(),
        (date.second and 0xff).toByte(),
    )
}

internal fun encodeDanaDateTime(timeMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): ByteArray {
    return encodeDanaDateTime(Instant.ofEpochMilli(timeMillis).atZone(zoneId))
}

internal fun encodeDanaUtcDateTime(timeMillis: Long): ByteArray {
    return encodeDanaDateTime(Instant.ofEpochMilli(timeMillis).atZone(ZoneOffset.UTC))
}

internal fun encodeDanaHistoryStart(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): ByteArray {
    return if (fromMillis == 0L) {
        byteArrayOf(0, 1, 1, 0, 0, 0)
    } else {
        encodeDanaDateTime(fromMillis, zoneId)
    }
}

internal fun le16(value: Int): ByteArray {
    return byteArrayOf((value and 0xff).toByte(), (value ushr 8 and 0xff).toByte())
}
