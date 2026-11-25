package com.itexpert120.yomu.data.readaloud

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Presents [ReadAloudPlayer] to the media session as a one-item player: the notification, lock
 * screen, and headset buttons drive it, and "next/previous" mean the next/previous sentence.
 */
@OptIn(UnstableApi::class)
internal class ReadAloudSessionPlayer(
    private val player: ReadAloudPlayer,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private var readAloud: ReadAloudState = player.state.value
    private var settings = ReadAloudSettings()

    // Completes once the player reports the state a play/pause asked for, so the controls show the
    // requested state until then instead of flicking back.
    private var pendingPlayPause: SettableFuture<Any?>? = null

    init {
        scope.launch {
            player.state.collect {
                readAloud = it
                pendingPlayPause?.set(null)
                pendingPlayPause = null
                invalidateState()
            }
        }
        scope.launch {
            player.settings.collect {
                settings = it
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAudioAttributes(sessionAudioAttributes)
            .setPlaybackParameters(PlaybackParameters(settings.rate, settings.pitch))
        val (book, chapterTitle) = when (val s = readAloud) {
            is ReadAloudState.Preparing -> s.book to null
            is ReadAloudState.Playing -> s.book to s.chapterTitle
            is ReadAloudState.Paused -> s.book to s.chapterTitle
            is ReadAloudState.Error, ReadAloudState.Idle -> null to null
        }
        if (book == null) {
            val error = (readAloud as? ReadAloudState.Error)?.let {
                PlaybackException(it.error.toString(), null, PlaybackException.ERROR_CODE_UNSPECIFIED)
            }
            return builder
                .setAvailableCommands(idleCommands)
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlayerError(error)
                .build()
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(book.title)
            .setDisplayTitle(book.title)
            .setArtist(chapterTitle ?: book.author)
            .setSubtitle(chapterTitle)
            .setAlbumTitle(book.title)
            .setAlbumArtist(book.author)
            .setArtworkUri(book.coverPath?.let { Uri.fromFile(File(it)) })
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        val item = MediaItemData.Builder(book.id.value)
            .setMediaItem(MediaItem.Builder().setMediaId(book.id.value).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setIsSeekable(false)
            .setDurationUs(C.TIME_UNSET)
            .build()
        return builder
            .setAvailableCommands(activeCommands)
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(0)
            .setPlaybackState(if (readAloud is ReadAloudState.Preparing) Player.STATE_BUFFERING else Player.STATE_READY)
            .setPlayWhenReady(readAloud !is ReadAloudState.Paused, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) player.resume() else player.pause()
        val future = SettableFuture.create<Any?>()
        pendingPlayPause?.set(null)
        pendingPlayPause = future
        // A refused resume (audio focus denied) never changes the state.
        handler.postDelayed({ future.set(null) }, PLAY_PAUSE_TIMEOUT_MS)
        return future
    }

    override fun handleStop(): ListenableFuture<*> {
        player.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> player.next()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> player.previous()
            else -> Unit
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        player.setRate(playbackParameters.speed)
        player.setPitch(playbackParameters.pitch)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacksAndMessages(null)
        pendingPlayPause?.set(null)
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val PLAY_PAUSE_TIMEOUT_MS = 1_000L

        val sessionAudioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val activeCommands: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                Player.COMMAND_RELEASE,
            )
            .build()

        val idleCommands: Player.Commands = Player.Commands.Builder()
            .addAll(Player.COMMAND_STOP, Player.COMMAND_RELEASE)
            .build()
    }
}
