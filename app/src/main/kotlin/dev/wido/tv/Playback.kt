package dev.wido.tv

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.VolumeProviderCompat
import dev.wido.core.NowPlaying
import dev.wido.core.RemoteKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Ce que montre une notification de lecture. */
data class PlaybackInfo(
    val title: String,
    val subtitle: String,
    val position: Double,
    val duration: Double,
    val playing: Boolean,
    val art: Bitmap? = null,
)

/**
 * Notification de lecture (comme Spotify ou YouTube) : image, titre, ⏪ ⏯ ⏩, sur l'écran verrouillé et
 * pilotable depuis des écouteurs Bluetooth. Partagée par les deux services ci-dessous.
 */
internal class PlaybackNotifier(private val service: Service, tag: String, private val stopAction: String) {
    private val manager = service.getSystemService(NotificationManager::class.java)
    val session = MediaSessionCompat(service, tag)

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, service.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        session.isActive = true
    }

    private fun action(icon: Int, title: String, action: String): NotificationCompat.Action {
        val intent = Intent(service, service.javaClass).setAction(action)
        val pending = PendingIntent.getService(service, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action(icon, title, pending)
    }

    fun build(info: PlaybackInfo, rewind: String, toggle: String, forward: String): Notification {
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, info.subtitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (info.duration * 1000).toLong())
                .apply { info.art?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it) } }
                .build(),
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(if (info.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, (info.position * 1000).toLong(), if (info.playing) 1f else 0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_SEEK_TO,
                )
                .build(),
        )
        val open = PendingIntent.getActivity(
            service, 0, Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(service, 1, Intent(service, service.javaClass).setAction(stopAction), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(service, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(info.title)
            .setContentText(info.subtitle)
            .setLargeIcon(info.art)
            .setContentIntent(open)
            .setDeleteIntent(stop)
            .setOnlyAlertOnce(true)
            .setOngoing(info.playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_rew, service.getString(R.string.notif_rewind), rewind))
            .addAction(
                if (info.playing) action(android.R.drawable.ic_media_pause, service.getString(R.string.tile_pause), toggle)
                else action(android.R.drawable.ic_media_play, service.getString(R.string.tile_play), toggle),
            )
            .addAction(action(android.R.drawable.ic_media_ff, service.getString(R.string.notif_forward), forward))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    fun show(id: Int, notification: Notification) = manager.notify(id, notification)

    fun release() {
        session.isActive = false
        session.release()
    }

    companion object {
        const val CHANNEL = "playback"
    }
}

/**
 * Téléphone : tant que la TV choisie lit une vidéo, une notification la pilote (⏪ ⏯ ⏩), aussi depuis
 * l'écran verrouillé ; les touches de volume du téléphone règlent la TV. Le service s'arrête de lui-même
 * quand la TV ne lit plus rien ou ne répond plus.
 */
class TvRemoteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notifier: PlaybackNotifier
    private val link get() = (application as WiDoApp).link
    private var started = false
    private var lastArtUrl: String? = null
    private var art: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = PlaybackNotifier(this, "WiDoTv", ACTION_STOP)
        notifier.session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = link.key(RemoteKey.PLAY)
            override fun onPause() = link.key(RemoteKey.PAUSE)
            override fun onRewind() = link.key(RemoteKey.REWIND)
            override fun onFastForward() = link.key(RemoteKey.FORWARD)
            override fun onSkipToPrevious() = link.key(RemoteKey.REWIND)
            override fun onSkipToNext() = link.key(RemoteKey.FORWARD)
        })
        // Le volume du téléphone devient celui de la TV (comme avec un Chromecast), écran verrouillé compris.
        notifier.session.setPlaybackToRemote(object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                when {
                    direction > 0 -> link.key(RemoteKey.VOLUME_UP)
                    direction < 0 -> link.key(RemoteKey.VOLUME_DOWN)
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Toujours au premier plan d'abord : Android ferme l'appli si un service lancé ainsi s'arrête avant.
        if (!started) {
            started = true
            val first = notifier.build(PlaybackInfo(getString(R.string.notif_tv_title), link.selectedPeer()?.name.orEmpty(), 0.0, 0.0, true), ACTION_REWIND, ACTION_TOGGLE, ACTION_FORWARD)
            ServiceCompat.startForeground(this, ID, first, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
            if (intent?.action != ACTION_STOP) scope.launch { follow() }
        }
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> link.key(RemoteKey.PLAY_PAUSE)
            ACTION_REWIND -> link.key(RemoteKey.REWIND)
            ACTION_FORWARD -> link.key(RemoteKey.FORWARD)
        }
        return START_NOT_STICKY
    }

    /** Suit la TV toutes les 3 s ; plus rien en lecture depuis 20 s (ou TV injoignable) : fin. */
    private suspend fun follow() {
        var idleSince = 0L
        while (scope.isActive) {
            val peer = link.selectedPeer() ?: break
            val state = link.refresh(peer)
            val now = state.status?.playing.takeIf { state.online }
            if (now == null) {
                if (idleSince == 0L) idleSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - idleSince > 20_000) break
            } else {
                idleSince = 0L
                show(now, peer.name)
            }
            delay(3_000)
        }
        stopSelf()
    }

    private suspend fun show(now: NowPlaying, tvName: String) {
        if (now.image != lastArtUrl) {
            lastArtUrl = now.image
            art = now.image?.let { (application as WiDoApp).thumbnails.load(it)?.asAndroidBitmap() }
        }
        val info = PlaybackInfo(now.title.ifEmpty { now.siteName }, getString(R.string.notif_on_tv, tvName), now.position, now.duration, now.playing, art)
        notifier.show(ID, notifier.build(info, ACTION_REWIND, ACTION_TOGGLE, ACTION_FORWARD))
    }

    override fun onDestroy() {
        requested = false
        scope.cancel()
        notifier.release()
        super.onDestroy()
    }

    companion object {
        private const val ID = 41
        private const val ACTION_STOP = "dev.wido.tv.tv.STOP"
        private const val ACTION_TOGGLE = "dev.wido.tv.tv.TOGGLE"
        private const val ACTION_REWIND = "dev.wido.tv.tv.REWIND"
        private const val ACTION_FORWARD = "dev.wido.tv.tv.FORWARD"

        /** Démarre la notification si la TV choisie lit quelque chose (appelé avec l'appli encore visible). */
        fun startIfPlaying(context: Context) {
            val app = context.applicationContext as WiDoApp
            if (app.isTv || !app.settings.tvNotification) return
            val peer = app.link.selectedPeer() ?: return
            val state = app.link.states.value[peer.id] ?: return
            if (!state.online || state.status?.playing == null) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TvRemoteService::class.java)) }.onSuccess { requested = true }
        }

        @Volatile private var requested = false

        /**
         * Arrêt demandé au service lui-même (il passe au premier plan puis s'arrête) : un simple
         * stopService avant son démarrage complet ferait fermer l'appli par Android.
         */
        fun stop(context: Context) {
            if (!requested) return
            requested = false
            runCatching { context.startService(Intent(context, TvRemoteService::class.java).setAction(ACTION_STOP)) }
        }
    }
}

/**
 * Le lien entre la page en lecture sur le téléphone et la notification de lecture en arrière-plan :
 * la page s'enregistre, la notification lui transmet ⏪ ⏯ ⏩.
 */
object BackgroundBridge {
    /** Reçoit `toggle`, `play`, `pause`, `rewind` ou `forward`. */
    @Volatile var controller: ((String) -> Unit)? = null
    @Volatile var info: PlaybackInfo? = null
    @Volatile var listener: (() -> Unit)? = null

    fun update(next: PlaybackInfo) {
        info = next
        listener?.invoke()
    }
}

/**
 * Téléphone : la vidéo continue (le son surtout) quand on quitte l'appli ou qu'on éteint l'écran.
 * Notification de lecture, écran verrouillé et écouteurs Bluetooth la pilotent. Le processus et le
 * Wi-Fi restent éveillés tant que la lecture dure.
 */
class BackgroundPlayService : Service() {
    private lateinit var notifier: PlaybackNotifier
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = PlaybackNotifier(this, "WiDoBackground", ACTION_PAUSE_STOP)
        notifier.session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = send("play")
            override fun onPause() = send("pause")
            override fun onRewind() = send("rewind")
            override fun onFastForward() = send("forward")
            override fun onSkipToPrevious() = send("rewind")
            override fun onSkipToNext() = send("forward")
        })
        BackgroundBridge.listener = { refresh() }
    }

    private fun send(command: String) {
        android.os.Handler(mainLooper).post { BackgroundBridge.controller?.invoke(command) }
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Premier plan d'abord, quelle que soit la demande (voir TvRemoteService).
        if (!started) {
            started = true
            val info = BackgroundBridge.info ?: PlaybackInfo(getString(R.string.app_name), "", 0.0, 0.0, true)
            ServiceCompat.startForeground(this, ID, notifier.build(info, ACTION_REWIND, ACTION_TOGGLE, ACTION_FORWARD), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiDo:background").apply { acquire() }
            @Suppress("DEPRECATION")
            wifi = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WiDo:background").apply { acquire() }
        }
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_STOP -> {
                send("pause")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> send("toggle")
            ACTION_REWIND -> send("rewind")
            ACTION_FORWARD -> send("forward")
        }
        return START_NOT_STICKY
    }

    private fun refresh() {
        val info = BackgroundBridge.info ?: return
        android.os.Handler(mainLooper).post { notifier.show(ID, notifier.build(info, ACTION_REWIND, ACTION_TOGGLE, ACTION_FORWARD)) }
    }

    override fun onDestroy() {
        requested = false
        BackgroundBridge.listener = null
        runCatching { wake?.release() }
        runCatching { wifi?.release() }
        notifier.release()
        super.onDestroy()
    }

    companion object {
        private const val ID = 42
        private const val ACTION_STOP = "dev.wido.tv.bg.STOP"
        /** Notification balayée : la vidéo se met en pause et la notification disparaît. */
        private const val ACTION_PAUSE_STOP = "dev.wido.tv.bg.PAUSE_STOP"
        private const val ACTION_TOGGLE = "dev.wido.tv.bg.TOGGLE"
        private const val ACTION_REWIND = "dev.wido.tv.bg.REWIND"
        private const val ACTION_FORWARD = "dev.wido.tv.bg.FORWARD"

        @Volatile private var requested = false

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, BackgroundPlayService::class.java)) }.onSuccess { requested = true }
        }

        /** Voir TvRemoteService.stop : l'arrêt passe par le service lui-même. */
        fun stop(context: Context) {
            if (!requested) return
            requested = false
            runCatching { context.startService(Intent(context, BackgroundPlayService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
