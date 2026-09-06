package com.itexpert120.yomu.data.stats

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

class ReadingCalendar @Inject constructor(@ApplicationContext private val context: Context) {
    val dates = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(LocalDate.now())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val midnight = launch {
            while (true) {
                val now = ZonedDateTime.now()
                trySend(now.toLocalDate())
                val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, next).toMillis().coerceIn(1L, 60_000L))
            }
        }
        awaitClose {
            midnight.cancel()
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged()
}
