package com.itexpert120.yomu.data.readaloud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Read-aloud is spoken-word media, like an audiobook. */
internal val readAloudAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** Audio focus plus the "becoming noisy" broadcast, which is only listened to while focus is held. */
@Singleton
class AndroidAudioOutput @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReadAloudAudioOutput {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val events = MutableSharedFlow<AudioInterruption>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val interruptions: Flow<AudioInterruption> = events.asSharedFlow()

    private var holding = false

    // Speech is unintelligible ducked, so a duck request pauses (the system sends a transient loss).
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(readAloudAudioAttributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> events.tryEmit(AudioInterruption.Gain)
                AudioManager.AUDIOFOCUS_LOSS -> events.tryEmit(AudioInterruption.Loss)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> events.tryEmit(AudioInterruption.TransientLoss)
            }
        }
        .build()

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) events.tryEmit(AudioInterruption.BecomingNoisy)
        }
    }

    override fun requestFocus(): Boolean {
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted && !holding) {
            holding = true
            ContextCompat.registerReceiver(
                context,
                noisy,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        return granted
    }

    override fun abandonFocus() {
        if (!holding) return
        holding = false
        audioManager.abandonAudioFocusRequest(focusRequest)
        runCatching { context.unregisterReceiver(noisy) }
    }
}
