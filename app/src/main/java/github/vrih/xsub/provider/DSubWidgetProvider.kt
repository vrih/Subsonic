/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2010 (C) Sindre Mehus
 */
package github.vrih.xsub.provider

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.Bitmap.Config
import android.graphics.PorterDuff.Mode
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import github.vrih.xsub.R
import github.vrih.xsub.activity.SubsonicActivity
import github.vrih.xsub.activity.SubsonicFragmentActivity
import github.vrih.xsub.domain.MusicDirectory
import github.vrih.xsub.domain.PlayerQueue
import github.vrih.xsub.service.DownloadService
import github.vrih.xsub.service.DownloadServiceLifecycleSupport
import github.vrih.xsub.util.Constants
import github.vrih.xsub.util.FileUtil
import github.vrih.xsub.util.Util

/**
 * Simple widget to show currently playing album art along
 * with play/pause and next track buttons.
 *
 *
 * Based on source code from the stock Android Music app.
 *
 * @author Sindre Mehus
 */
open class DSubWidgetProvider : AppWidgetProvider() {

     open var layout: Int = 0
        get() = 0

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        defaultAppWidget(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        notifyInstances(context, DownloadService.instance, false)
    }

    /**
     * Initialize given widgets to default state, where we launch Subsonic on default click
     * and hide actions if service not running.
     */
    private fun defaultAppWidget(context: Context, appWidgetIds: IntArray) {
        val res = context.resources
        val views = RemoteViews(context.packageName, layout)

        views.setTextViewText(R.id.artist, res.getText(R.string.widget_initial_text))
        if (layout == R.layout.appwidget4x2) {
            views.setTextViewText(R.id.album, "")
        }

        linkButtons(context, views)
        performUpdate(context, null, appWidgetIds, false)
    }

    private fun pushUpdate(context: Context, appWidgetIds: IntArray?, views: RemoteViews) {
        // Update specific list of appWidgetIds if given, otherwise default to all
        val manager = AppWidgetManager.getInstance(context)
        if (appWidgetIds != null) {
            manager.updateAppWidget(appWidgetIds, views)
        } else {
            manager.updateAppWidget(ComponentName(context, this.javaClass), views)
        }
    }

    /**
     * Handle a change notification coming over from [DownloadService]
     */
    fun notifyChange(context: Context, service: DownloadService?, playing: Boolean) {
        if (hasInstances(context)) {
            performUpdate(context, service, null, playing)
        }
    }

    /**
     * Check against [AppWidgetManager] if there are any instances of this widget.
     */
    private fun hasInstances(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val appWidgetIds = manager.getAppWidgetIds(ComponentName(context, javaClass))
        return appWidgetIds.size > 0
    }

    /**
     * Update all active widget instances by pushing changes
     */
    private fun performUpdate(context: Context, service: DownloadService?, appWidgetIds: IntArray?, playing: Boolean) {
        val res = context.resources
        val views = RemoteViews(context.packageName, layout)

        if (playing) {
            views.setViewVisibility(R.id.widget_root, View.VISIBLE)
        } else {
            // Hide widget
            val prefs = Util.getPreferences(context)
            if (prefs.getBoolean(Constants.PREFERENCES_KEY_HIDE_WIDGET, false)) {
                views.setViewVisibility(R.id.widget_root, View.GONE)
            }
        }

        // Get Entry from current playing DownloadFile
        var currentPlaying: MusicDirectory.Entry? = null
        if (service == null) {
            // Deserialize from playling list to setup
            try {
                val state = FileUtil.deserialize(context, DownloadServiceLifecycleSupport.FILENAME_DOWNLOADS_SER, PlayerQueue::class.java)
                if (state != null && state.currentPlayingIndex != -1) {
                    currentPlaying = state.songs[state.currentPlayingIndex]
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to grab current playing", e)
            }

        } else {
            currentPlaying = if (service.currentPlaying == null) null else service.currentPlaying!!.song
        }

        val title = currentPlaying?.title
        val artist = currentPlaying?.artist
        val album = currentPlaying?.album
        var errorState: CharSequence? = null

        // Show error message?
        val status = Environment.getExternalStorageState()
        if (status == Environment.MEDIA_SHARED || status == Environment.MEDIA_UNMOUNTED) {
            errorState = res.getText(R.string.widget_sdcard_busy)
        } else if (status == Environment.MEDIA_REMOVED) {
            errorState = res.getText(R.string.widget_sdcard_missing)
        } else if (currentPlaying == null) {
            errorState = res.getText(R.string.widget_initial_text)
        }

        if (errorState != null) {
            // Show error state to user
            views.setTextViewText(R.id.title, null)
            views.setTextViewText(R.id.artist, errorState)
            views.setTextViewText(R.id.album, "")
            if (layout != R.layout.appwidget4x1) {
                views.setImageViewResource(R.id.appwidget_coverart, R.drawable.appwidget_art_default)
            }
        } else {
            // No error, so show normal titles
            views.setTextViewText(R.id.title, title)
            views.setTextViewText(R.id.artist, artist)
            if (layout != R.layout.appwidget4x1) {
                views.setTextViewText(R.id.album, album)
            }
        }

        val playingIcon = if (playing) R.drawable.media_pause else R.drawable.media_start
        val forwardIcon = if (currentPlaying?.isSong == false) R.drawable.media_fastforward else R.drawable.media_forward
        val backwardIcon = if(currentPlaying?.isSong == false) R.drawable.media_rewind else R.drawable.media_backward

        // Set correct drawable for pause state
        views.setImageViewResource(R.id.control_play, playingIcon)
        views.setImageViewResource(R.id.control_next, forwardIcon)
        views.setImageViewResource(R.id.control_previous, backwardIcon)

        // Set the cover art
        try {
            var large = false
            if (layout != R.layout.appwidget4x1 && layout != R.layout.appwidget4x2) {
                large = true
            }
            val imageLoader = SubsonicActivity.getStaticImageLoader(context)
            var bitmap: Bitmap? = imageLoader?.getCachedImage(context, currentPlaying, large)

            if (bitmap == null) {
                // Set default cover art
                views.setImageViewResource(R.id.appwidget_coverart, R.drawable.appwidget_art_unknown)
            } else {
                bitmap = getRoundedCornerBitmap(bitmap)
                views.setImageViewBitmap(R.id.appwidget_coverart, bitmap)
            }
        } catch (x: Exception) {
            Log.e(TAG, "Failed to load cover art", x)
            views.setImageViewResource(R.id.appwidget_coverart, R.drawable.appwidget_art_unknown)
        }

        // Link actions buttons to intents
        linkButtons(context, views)

        pushUpdate(context, appWidgetIds, views)
    }

    /**
     * Link up various button actions using [PendingIntent].
     *
     */
    private fun linkButtons(context: Context, views: RemoteViews) {
        var intent = Intent(context, SubsonicFragmentActivity::class.java)
        intent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        var pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
        views.setOnClickPendingIntent(R.id.appwidget_coverart, pendingIntent)
        views.setOnClickPendingIntent(R.id.appwidget_top, pendingIntent)

        // Emulate media button clicks.
        intent = Intent("DSub.PLAY_PAUSE")
        intent.component = ComponentName(context, DownloadService::class.java)
        intent.action = DownloadService.CMD_TOGGLEPAUSE
        pendingIntent = PendingIntent.getService(context, 0, intent, 0)
        views.setOnClickPendingIntent(R.id.control_play, pendingIntent)

        intent = Intent("DSub.NEXT")  // Use a unique action name to ensure a different PendingIntent to be created.
        intent.component = ComponentName(context, DownloadService::class.java)
        intent.action = DownloadService.CMD_NEXT
        pendingIntent = PendingIntent.getService(context, 0, intent, 0)
        views.setOnClickPendingIntent(R.id.control_next, pendingIntent)

        intent = Intent("DSub.PREVIOUS")  // Use a unique action name to ensure a different PendingIntent to be created.
        intent.component = ComponentName(context, DownloadService::class.java)
        intent.action = DownloadService.CMD_PREVIOUS
        pendingIntent = PendingIntent.getService(context, 0, intent, 0)
        views.setOnClickPendingIntent(R.id.control_previous, pendingIntent)
    }

    companion object {
        private val TAG = DSubWidgetProvider::class.java.simpleName
        private var instance4x1: DSubWidget4x1? = null
        private var instance4x2: DSubWidget4x2? = null
        private var instance4x3: DSubWidget4x3? = null
        private var instance4x4: DSubWidget4x4? = null

        @Synchronized
        fun notifyInstances(context: Context, service: DownloadService?, playing: Boolean) {
            if (instance4x1 == null) {
                instance4x1 = DSubWidget4x1()
            }
            if (instance4x2 == null) {
                instance4x2 = DSubWidget4x2()
            }
            if (instance4x3 == null) {
                instance4x3 = DSubWidget4x3()
            }
            if (instance4x4 == null) {
                instance4x4 = DSubWidget4x4()
            }

            instance4x1!!.notifyChange(context, service, playing)
            instance4x2!!.notifyChange(context, service, playing)
            instance4x3!!.notifyChange(context, service, playing)
            instance4x4!!.notifyChange(context, service, playing)
        }

        /**
         * Round the corners of a bitmap for the cover art image
         */
        private fun getRoundedCornerBitmap(bitmap: Bitmap): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Config.ARGB_8888)
            val canvas = Canvas(output)

            val color = -0xbdbdbe
            val paint = Paint()
            val roundPx = 10f

            // Add extra width to the rect so the right side wont be rounded.
            val rect = Rect(0, 0, bitmap.width + roundPx.toInt(), bitmap.height)
            val rectF = RectF(rect)

            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            paint.color = color
            canvas.drawRoundRect(rectF, roundPx, roundPx, paint)

            paint.xfermode = PorterDuffXfermode(Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)

            return output
        }
    }
}
