/*
  This file is part of Subsonic.
	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
	Copyright 2014 (C) Scott Jackson
*/

package github.vrih.xsub.util

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews

import androidx.core.app.NotificationCompat
import github.vrih.xsub.R
import github.vrih.xsub.activity.SubsonicActivity
import github.vrih.xsub.activity.SubsonicFragmentActivity
import github.vrih.xsub.domain.MusicDirectory
import github.vrih.xsub.domain.PlayerState
import github.vrih.xsub.provider.DSubWidgetProvider
import github.vrih.xsub.service.DownloadFile
import github.vrih.xsub.service.DownloadService
import github.vrih.xsub.view.UpdateView

object Notifications {
    private val TAG = Notifications::class.java.simpleName

    // Notification IDs.
    private val NOTIFICATION_ID_PLAYING = 100
    private val NOTIFICATION_ID_DOWNLOADING = 102
    private val NOTIFICATION_ID_SHUT_GOOGLE_UP = 103
    private val NOTIFICATION_SYNC_GROUP = "github.vrih.xsub.sync"

    private var playShowing = false
    private var downloadShowing = false
    private var downloadForeground = false
    private var persistentPlayingShowing = false

    private var playingChannel: NotificationChannel? = null
    private var downloadingChannel: NotificationChannel? = null
    private var syncChannel: NotificationChannel? = null

    fun showPlayingNotification(context: Context, downloadService: DownloadService, handler: Handler, song: MusicDirectory.Entry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getPlayingNotificationChannel(context)
        }

        // Set the icon, scrolling text and timestamp
        val notification = NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.stat_notify_playing)
                .setTicker(song.title)
                .setWhen(System.currentTimeMillis())
                .setChannelId("now-playing-channel")
                .build()

        val playing = downloadService.getPlayerState() === PlayerState.STARTED
        if (playing) {
            notification.flags = notification.flags or (Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT)
        }
        val remote = downloadService.isRemoteEnabled
        val isSingle = downloadService.isCurrentPlayingSingle
        val shouldFastForward = downloadService.shouldFastForward()
        val expandedContentView = RemoteViews(context.packageName, R.layout.notification_expanded)
        setupViews(expandedContentView, context, song, true, playing, remote, isSingle, shouldFastForward)
        notification.bigContentView = expandedContentView
        notification.priority = Notification.PRIORITY_HIGH

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification.visibility = Notification.VISIBILITY_PUBLIC

            if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_HEADS_UP_NOTIFICATION, false)
                    && !UpdateView.hasActiveActivity()) {
                notification.vibrate = LongArray(0)
            }
        }

        val smallContentView = RemoteViews(context.packageName, R.layout.notification)
        setupViews(smallContentView, context, song, false, playing, remote, isSingle, shouldFastForward)
        notification.contentView = smallContentView

        val notificationIntent = Intent(context, SubsonicFragmentActivity::class.java)
        notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, true)
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        notification.contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0)

        playShowing = true
        if (downloadForeground && downloadShowing) {
            downloadForeground = false
            handler.post {
                stopForeground(downloadService, true)
                showDownloadingNotification(context, downloadService, handler, downloadService.currentDownloading, downloadService.backgroundDownloads.size)

                try {
                    startForeground(downloadService, NOTIFICATION_ID_PLAYING, notification)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start notifications after stopping foreground download")
                }
            }
        } else {
            handler.post {
                if (playing) {
                    try {
                        startForeground(downloadService, NOTIFICATION_ID_PLAYING, notification)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start notifications while playing")
                    }

                } else {
                    playShowing = false
                    persistentPlayingShowing = true
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    stopForeground(downloadService, false)

                    try {
                        notificationManager.notify(NOTIFICATION_ID_PLAYING, notification)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start notifications while paused")
                    }

                }
            }
        }

        // Update widget
        DSubWidgetProvider.notifyInstances(context, downloadService, playing)
    }

    private fun setupViews(rv: RemoteViews,
                           context: Context,
                           song: MusicDirectory.Entry,
                           expanded: Boolean,
                           playing: Boolean,
                           remote: Boolean,
                           isSingleFile: Boolean,
                           shouldFastForward: Boolean) {
        // Set the album art.
        try {
            val bitmap = ImageLoader(context).getCachedImage(context, song, false)

            if (bitmap == null) {
                // set default album art
                rv.setImageViewResource(R.id.notification_image, R.drawable.unknown_album)
            } else {
                imageLoader.setNowPlayingSmall(bitmap)
                rv.setImageViewBitmap(R.id.notification_image, bitmap)
            }
        } catch (x: Exception) {
            Log.w(TAG, "Failed to get notification cover art", x)
            rv.setImageViewResource(R.id.notification_image, R.drawable.unknown_album)
        }

        // set the text for the notifications
        rv.setTextViewText(R.id.notification_title, song.title)
        rv.setTextViewText(R.id.notification_artist, song.artist)
        rv.setTextViewText(R.id.notification_album, song.album)

        val persistent = Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_PERSISTENT_NOTIFICATION, false)

        val playingIcon = if (playing) R.drawable.media_pause else R.drawable.media_start
        val forwardIcon = if(shouldFastForward) R.drawable.media_fastforward else R.drawable.media_forward
        val backwardIcon = if(shouldFastForward) R.drawable.media_rewind else R.drawable.media_backward

        if (persistent) {
            if (expanded) {
                rv.setImageViewResource(R.id.control_pause, playingIcon)
                rv.setImageViewResource(R.id.control_next, forwardIcon)
                rv.setImageViewResource(R.id.control_previous, backwardIcon)
            } else {
                rv.setImageViewResource(R.id.control_previous, playingIcon)
                rv.setImageViewResource(R.id.control_pause, forwardIcon)
                rv.setImageViewResource(R.id.control_next, R.drawable.notification_close)
            }
        } else {
            rv.setImageViewResource(R.id.control_pause, playingIcon)
            rv.setImageViewResource(R.id.control_previous, backwardIcon)
            rv.setImageViewResource(R.id.control_next, forwardIcon)
        }

        // Create actions for media buttons
        var previous = 0
        val pause: Int
        var next = 0
        var close = 0
        var rewind = 0
        var fastForward = 0
        if (expanded) {
            pause = R.id.control_pause

            if (shouldFastForward) {
                rewind = R.id.control_previous
                fastForward = R.id.control_next
            } else {
                previous = R.id.control_previous
                next = R.id.control_next
            }

            if (remote || persistent) {
                close = R.id.notification_close
                rv.setViewVisibility(close, View.VISIBLE)
            }
        } else {
            if (persistent) {
                pause = R.id.control_previous
                if (shouldFastForward) {
                    fastForward = R.id.control_pause
                } else {
                    next = R.id.control_pause
                }
                close = R.id.control_next
            } else {
                if (shouldFastForward) {
                    rewind = R.id.control_previous
                    fastForward = R.id.control_next
                } else {
                    previous = R.id.control_previous
                    next = R.id.control_next
                }

                pause = R.id.control_pause
            }
        }

        if (isSingleFile) {
            if (previous > 0) {
                rv.setViewVisibility(previous, View.GONE)
                previous = 0
            }
            if (rewind > 0) {
                rv.setViewVisibility(rewind, View.GONE)
                rewind = 0
            }

            if (next > 0) {
                rv.setViewVisibility(next, View.GONE)
                next = 0
            }

            if (fastForward > 0) {
                rv.setViewVisibility(fastForward, View.GONE)
                fastForward = 0
            }
        }

        var pendingIntent: PendingIntent
        if (previous > 0) {
            val prevIntent = Intent("KEYCODE_MEDIA_PREVIOUS")
            prevIntent.component = ComponentName(context, DownloadService::class.java)
            prevIntent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            pendingIntent = PendingIntent.getService(context, 0, prevIntent, 0)
            rv.setOnClickPendingIntent(previous, pendingIntent)
        }
        if (rewind > 0) {
            val rewindIntent = Intent("KEYCODE_MEDIA_REWIND")
            rewindIntent.component = ComponentName(context, DownloadService::class.java)
            rewindIntent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_REWIND))
            pendingIntent = PendingIntent.getService(context, 0, rewindIntent, 0)
            rv.setOnClickPendingIntent(rewind, pendingIntent)
        }
        if (playing) {
            val pauseIntent = Intent("KEYCODE_MEDIA_PLAY_PAUSE")
            pauseIntent.component = ComponentName(context, DownloadService::class.java)
            pauseIntent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            pendingIntent = PendingIntent.getService(context, 0, pauseIntent, 0)
            rv.setOnClickPendingIntent(pause, pendingIntent)
        } else {
            val prevIntent = Intent("KEYCODE_MEDIA_START")
            prevIntent.component = ComponentName(context, DownloadService::class.java)
            prevIntent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            pendingIntent = PendingIntent.getService(context, 0, prevIntent, 0)
            rv.setOnClickPendingIntent(pause, pendingIntent)
        }
        if (next > 0) {
            val nextIntent = Intent("KEYCODE_MEDIA_NEXT")
            nextIntent.component = ComponentName(context, DownloadService::class.java)
            nextIntent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
            pendingIntent = PendingIntent.getService(context, 0, nextIntent, 0)
            rv.setOnClickPendingIntent(next, pendingIntent)
        }
        if (fastForward > 0) {
            val fastForwardIntent = Intent("KEYCODE_MEDIA_FAST_FORWARD")
            fastForwardIntent.component = ComponentName(context, DownloadService::class.java)
            fastForwardIntent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
            pendingIntent = PendingIntent.getService(context, 0, fastForwardIntent, 0)
            rv.setOnClickPendingIntent(fastForward, pendingIntent)
        }
        if (close > 0) {
            val prevIntent = Intent("KEYCODE_MEDIA_STOP")
            prevIntent.component = ComponentName(context, DownloadService::class.java)
            prevIntent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
            pendingIntent = PendingIntent.getService(context, 0, prevIntent, 0)
            rv.setOnClickPendingIntent(close, pendingIntent)
        }
    }

    fun hidePlayingNotification(context: Context, downloadService: DownloadService, handler: Handler) {
        playShowing = false

        // Remove notification and remove the service from the foreground
        handler.post {
            stopForeground(downloadService, true)

            if (persistentPlayingShowing) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID_PLAYING)
                persistentPlayingShowing = false
            }
        }

        // Get downloadNotification in foreground if playing
        if (downloadShowing) {
            showDownloadingNotification(context, downloadService, handler, downloadService.currentDownloading, downloadService.backgroundDownloads.size)
        }

        // Update widget
        DSubWidgetProvider.notifyInstances(context, downloadService, false)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun getPlayingNotificationChannel(context: Context) {
        if (playingChannel == null) {
            playingChannel = NotificationChannel("now-playing-channel", "Now Playing", NotificationManager.IMPORTANCE_LOW)
            playingChannel!!.description = "Now playing notification"

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(playingChannel!!)
        }

    }

    fun showDownloadingNotification(context: Context, downloadService: DownloadService, handler: Handler, file: DownloadFile?, size: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getDownloadingNotificationChannel(context)
        }

        val cancelIntent = Intent(context, DownloadService::class.java)
        cancelIntent.action = DownloadService.CANCEL_DOWNLOADS
        val cancelPI = PendingIntent.getService(context, 0, cancelIntent, 0)

        val currentDownloading: String = file?.song?.title ?: "none"
        val currentSize: String = if (file != null) {
            Util.formatLocalizedBytes(file.estimatedSize, context)
        } else {
            "0"
        }

        val builder: NotificationCompat.Builder
        builder = NotificationCompat.Builder(context)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.resources.getString(R.string.download_downloading_title, size))
                .setContentText(context.resources.getString(R.string.download_downloading_summary, currentDownloading))
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText(context.resources.getString(R.string.download_downloading_summary_expanded, currentDownloading, currentSize)))
                .setProgress(10, 5, true)
                .setOngoing(true)
                .addAction(R.drawable.notification_close,
                        context.resources.getString(R.string.common_cancel),
                        cancelPI)
                .setChannelId("downloading-channel")

        val notificationIntent = Intent(context, SubsonicFragmentActivity::class.java)
        notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD_VIEW, true)
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        builder.setContentIntent(PendingIntent.getActivity(context, 2, notificationIntent, 0))

        val notification = builder.build()
        downloadShowing = true
        if (playShowing) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID_DOWNLOADING, notification)
        } else {
            downloadForeground = true
            handler.post { startForeground(downloadService, NOTIFICATION_ID_DOWNLOADING, notification) }
        }

    }

    fun hideDownloadingNotification(context: Context, downloadService: DownloadService, handler: Handler) {
        downloadShowing = false
        if (playShowing) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID_DOWNLOADING)
        } else {
            downloadForeground = false
            handler.post { stopForeground(downloadService, true) }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun getDownloadingNotificationChannel(context: Context) {
        if (downloadingChannel == null) {
            downloadingChannel = NotificationChannel("downloading-channel", "Downloading Notification", NotificationManager.IMPORTANCE_LOW)
            downloadingChannel!!.description = "Ongoing downloading notification to keep the service alive"

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(downloadingChannel!!)
        }

    }

    @TargetApi(Build.VERSION_CODES.O)
    fun shutGoogleUpNotification(downloadService: DownloadService) {
        // On Android O+, service crashes if startForeground isn't called within 5 seconds of starting
        getDownloadingNotificationChannel(downloadService)

        val builder: NotificationCompat.Builder
        builder = NotificationCompat.Builder(downloadService)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(downloadService.resources.getString(R.string.download_downloading_title, 0))
                .setContentText(downloadService.resources.getString(R.string.download_downloading_summary, "Temp"))
                .setChannelId("downloading-channel")

        val notification = builder.build()
        startForeground(downloadService, NOTIFICATION_ID_SHUT_GOOGLE_UP, notification)
        stopForeground(downloadService, true)
    }

    @JvmStatic
    fun showSyncNotification(context: Context, stringId: Int, extra: String, extraId: String?) {
        var extra = extra
        if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_SYNC_NOTIFICATION, true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSyncNotificationChannel(context)
            }

            val builder: NotificationCompat.Builder
            builder = NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.stat_notify_sync)
                    .setContentTitle(context.resources.getString(stringId))
                    .setContentText(extra)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(extra.replace(", ", "\n")))
                    .setOngoing(false)
                    .setGroup(NOTIFICATION_SYNC_GROUP)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setChannelId("sync-channel")
                    .setAutoCancel(true)

            val notificationIntent = Intent(context, SubsonicFragmentActivity::class.java)
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            var tab: String? = null
            var type: String? = null
            when (stringId) {
                R.string.sync_new_albums -> type = "newest"
                R.string.sync_new_playlists -> tab = "Playlist"
                R.string.sync_new_podcasts -> tab = "Podcast"
                R.string.sync_new_starred -> type = "starred"
            }

            if (tab != null) {
                notificationIntent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, tab)
            }
            if (type != null) {
                notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type)
            }
            if (extraId != null) {
                notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_ID, extraId)
            }

            builder.setContentIntent(PendingIntent.getActivity(context, stringId, notificationIntent, 0))

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(stringId, builder.build())
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun getSyncNotificationChannel(context: Context) {
        if (syncChannel == null) {
            syncChannel = NotificationChannel("sync-channel", "Sync Notifications", NotificationManager.IMPORTANCE_MIN)
            syncChannel!!.description = "Sync notifications"

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(syncChannel!!)
        }

    }

    private fun startForeground(downloadService: DownloadService, notificationId: Int, notification: Notification) {
        downloadService.startForeground(notificationId, notification)
        downloadService.setIsForeground(true)
    }

    private fun stopForeground(downloadService: DownloadService, removeNotification: Boolean) {
        downloadService.stopForeground(removeNotification)
        downloadService.setIsForeground(false)
    }
}
