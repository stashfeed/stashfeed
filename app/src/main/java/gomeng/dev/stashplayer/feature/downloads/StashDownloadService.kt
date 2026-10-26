package gomeng.dev.stashplayer.feature.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.ShareActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val CHANNEL_PROGRESS = "downloads_progress"
private const val CHANNEL_DONE = "downloads_done"
private const val PROGRESS_NOTIFICATION_ID = 7301

/**
 * Keeps Stashfeed running while downloads are in progress, so their files are imported into the library even with
 * the app in the background. Shows the progress while it runs and a notification for each download that ends.
 */
class StashDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    /** Phases already reported per download, so each one that ends notifies once. */
    private val announced = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        ServiceCompat.startForeground(
            this,
            PROGRESS_NOTIFICATION_ID,
            progressNotification(StashDownloads.items.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        // Downloads that already ended before the service started don't notify again.
        StashDownloads.items.value.filterNot { it.active }.forEach { announced += it.id }
        watcher = scope.launch {
            StashDownloads.items.collect { downloads ->
                downloads.filter { !it.active && announced.add(it.id) }.forEach(::notifyEnded)
                if (downloads.none { it.active }) {
                    ServiceCompat.stopForeground(this@StashDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notify(PROGRESS_NOTIFICATION_ID, progressNotification(downloads))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(downloads: List<StashDownload>): Notification {
        val active = downloads.filter { it.active }
        val current = active.lastOrNull { it.phase == DownloadPhase.Downloading || it.phase == DownloadPhase.Importing } ?: active.lastOrNull()
        val percent = current?.progress?.percentage?.toInt()
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(resources.getQuantityString(R.plurals.downloads_notification_running, active.size.coerceAtLeast(1), active.size.coerceAtLeast(1)))
            .setContentText(current?.title ?: current?.url)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openDownloads(this))
            .build()
    }

    private fun notifyEnded(download: StashDownload) {
        val title = when (download.phase) {
            DownloadPhase.Done -> R.string.downloads_notification_done
            DownloadPhase.AlreadyInLibrary -> R.string.downloads_notification_duplicate
            DownloadPhase.Failed -> R.string.downloads_notification_failed
            else -> return
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(if (download.phase == DownloadPhase.Failed) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(title))
            .setContentText(download.title ?: download.url)
            .setAutoCancel(true)
            .setContentIntent(openDownloads(this))
            .build()
        notify(download.id.hashCode(), notification)
    }

    private fun notify(id: Int, notification: Notification) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
        }
    }

    companion object {
        /** Starts the service if it isn't running; call from the app while it is in front, when a download starts. */
        fun ensureRunning(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, StashDownloadService::class.java)) }
        }

        private fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.downloads_channel_progress), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.downloads_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        private fun openDownloads(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, ShareActivity::class.java).setAction(ShareActivity.ACTION_OPEN_DOWNLOADS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
