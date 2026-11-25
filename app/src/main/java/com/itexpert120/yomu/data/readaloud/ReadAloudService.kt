package com.itexpert120.yomu.data.readaloud

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.itexpert120.yomu.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts read-aloud's media session, so reading continues outside the app with notification,
 * lock-screen, and headset controls. media3 runs it in the foreground while speaking; it stops
 * itself as soon as the player is idle or failed.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class ReadAloudService : MediaSessionService() {
    @Inject lateinit var readAloud: ReadAloudPlayer

    private val scope = MainScope()
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.read_aloud_notification_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_read_aloud_notification) },
        )
        val builder = MediaSession.Builder(this, ReadAloudSessionPlayer(readAloud)).setId(SESSION_ID)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        val created = builder.build()
        session = created
        // Started with startService rather than by a controller, so add the session for media3's
        // notification and foreground handling.
        addSession(created)
        scope.launch {
            readAloud.state.collect { state ->
                if (state is ReadAloudState.Idle || state is ReadAloudState.Error) stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        // Swiped away from recents while paused (media3 then stops the service): nothing may linger.
        if (readAloud.state.value is ReadAloudState.Paused) readAloud.stop()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "read_aloud"
        const val SESSION_ID = "read_aloud"
    }
}

/** Starts [ReadAloudService] when reading starts; media3 promotes it to the foreground. */
class ServiceReadAloudHost @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReadAloudHost {
    override fun ensureRunning() {
        // Reading starts from the reader, in the foreground; a background start is refused, harmlessly.
        runCatching { context.startService(Intent(context, ReadAloudService::class.java)) }
    }
}
