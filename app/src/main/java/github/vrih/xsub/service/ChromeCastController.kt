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

package github.vrih.xsub.service

import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.images.WebImage
import github.vrih.xsub.R
import github.vrih.xsub.domain.PlayerState
import github.vrih.xsub.util.Util
import java.util.*

/**
 * Created by owner on 2/9/14.
 */
class ChromeCastController(downloadService: DownloadService) : RemoteController(downloadService) {

    private var error = false
    private var isStopping = false
    private var afterUpdateComplete: Runnable? = null

    private var mediaPlayer: RemoteMediaClient? = null
    private var gain = 0.5
    private var cachedProgress: Int = 0
    private var cachedDuration: Int = 0
    private val TAG = "ChromeCastController"

    private val rmcCallback = object : RemoteMediaClient.Callback() {
        override fun onQueueStatusUpdated() {
            super.onQueueStatusUpdated()
            val mediaQueueItem = mediaPlayer!!.currentItem
            val mediaQueue = mediaPlayer!!.mediaQueue
            Log.w("CAST", "Callback $mediaQueueItem$mediaQueue")
            if (mediaQueueItem != null) {
                val index = mediaQueue.indexOfItemWithId(mediaQueueItem.itemId)
                downloadService.setCurrentPlaying(index)
            }
        }
    }

    override fun create(playing: Boolean, seconds: Int) {
        downloadService.setPlayerState(PlayerState.PREPARING)

        //ConnectionCallbacks connectionCallbacks = new ConnectionCallbacks(playing, seconds);
        //		ConnectionFailedListener connectionFailedListener = new ConnectionFailedListener();
    }

    override fun start() {
        Log.w(TAG, "Attempting to start chromecast song")

        if (error) {
            error = false
            Log.w(TAG, "Attempting to restart song")
            //startSong(downloadService.getCurrentPlaying(), true, 0);
            return
        }

        try {
            val result = mediaPlayer!!.play()
            result.setResultCallback { result ->
                if (result.status.isSuccess) {
                    downloadService.setPlayerState(PlayerState.STARTED)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start")
        }

    }

    override fun stop() {
        Log.w(TAG, "Attempting to stop chromecast song")
        try {
            mediaPlayer!!.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause")
        }

    }

    override fun next() {
        mediaPlayer!!.queueNext(null)
    }

    override fun previous() {
        mediaPlayer!!.queuePrev(null)
    }

    override fun shutdown() {
        try {
            if (mediaPlayer != null && !error) {
                mediaPlayer!!.stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop mediaPlayer", e)
        }

        try {
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to shutdown application", e)
        }

        if (proxy != null) {
            proxy.stop()
            proxy = null
        }
    }

    override fun updatePlaylist() {
        if (downloadService.currentPlaying == null) {
            //startSong(null, false, 0);
        }
    }

    override fun setCurrentPlaying(index: Int){
        mediaPlayer?.queueJumpToItem(mediaPlayer?.mediaQueue?.itemIdAtIndex(index) ?: 0, null)

    }

    override fun insertPlaylist(songs: List<DownloadFile>, index: Int) {
        val queue = ArrayList<MediaQueueItem>()

        for (song in songs) queue.add(entryToQueueItem(song))
        mediaPlayer?.let {
            it.queueInsertItems(queue.toTypedArray(), it.mediaQueue?.itemIdAtIndex(index + 1) ?: 0, null)
        }
    }

    override fun appendPlaylist(songs: List<DownloadFile>) {
        Log.w("CAST", "Append $songs")
        for(song in songs)
            mediaPlayer!!.queueAppendItem(entryToQueueItem(song), null)
    }

    override fun changePosition(seconds: Int) {
        mediaPlayer!!.seek(seconds * 1000L)
    }

    override fun changeTrack(index: Int, downloadList: List<DownloadFile>, position: Int) {
        // Create Queue
        startSong(index, downloadList, true, position)
        //
    }

    override fun changeTrack(index: Int, song: DownloadFile, position: Int) {
        val dl = ArrayList<DownloadFile>()
        dl.add(song)
        changeTrack(index, dl, position)
    }

    override fun setVolume(volume: Int) {
        gain = volume / 10.0

        try {
            mediaPlayer!!.setStreamVolume(gain)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to the volume")
        }

    }

    override fun updateVolume(up: Boolean) {
        val delta = if (up) 0.1 else -0.1
        gain += delta
        gain = Math.max(gain, 0.0)
        gain = Math.min(gain, 1.0)

        try {
            mediaPlayer!!.setStreamVolume(gain)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to the volume")
        }

    }

    override fun getVolume(): Double {
        // TODO: Make sensible
        return 0.0
    }

    /**
     * @return position in secs
     */
    override fun getRemotePosition(): Int {
        // TODO: this should return ms
        return if (mediaPlayer != null) {
            cachedProgress / 1000
        } else {
            0
        }
    }

    /**
     * @return duration in secs
     */
    override fun getRemoteDuration(): Int {
        // TODO: this should return ms
        return if (mediaPlayer != null) {
            if (cachedDuration > 0) {
                cachedDuration / 1000
            } else (mediaPlayer!!.streamDuration / 1000L).toInt()
        } else {
            0
        }
    }

    private fun startSong(index: Int, playlist: List<DownloadFile>?, autoStart: Boolean, position: Int) {
        Log.w(TAG, "Starting song")

        if (playlist == null || playlist.isEmpty()) {
            try {
                if (mediaPlayer != null && !error && !isStopping) {
                    isStopping = true
                    mediaPlayer!!.stop().setResultCallback {
                        isStopping = false

                        if (afterUpdateComplete != null) {
                            afterUpdateComplete!!.run()
                            afterUpdateComplete = null
                        }
                    }
                }
            } catch (e: Exception) {
                // Just means it didn't need to be stopped
            }

            downloadService.setPlayerState(PlayerState.IDLE)
            return
        }

        downloadService.setPlayerState(PlayerState.PREPARING)

        val queue = ArrayList<MediaQueueItem>()

        try {
            for (file in playlist) queue.add(entryToQueueItem(file))

            val callback = ResultCallback<RemoteMediaClient.MediaChannelResult> { result ->
                when {
                    result.status.isSuccess -> downloadService.setPlayerState(PlayerState.STARTED)
                    // Handled in other handler
                    result.status.statusCode == CastStatusCodes.REPLACED -> Log.i(TAG, "Playlist was replaced")
                    else -> {
                        Log.e(TAG, "Failed to load: " + result.status.toString())
                        failedLoad()
                    }
                }
            }

            //mediaPlayer.load(mediaInfo, mlo).setResultCallback(callback);
            val queueList = arrayOfNulls<MediaQueueItem>(queue.size)
            val a = mediaPlayer!!.queueLoad(queue.toTypedArray(), index, MediaStatus.REPEAT_MODE_REPEAT_OFF, (position * 1000).toLong(), null)
            a.setResultCallback(callback)
            val rpl = RemoteMediaClient.ProgressListener { progress, duration ->
                cachedProgress = progress.toInt()
                cachedDuration = duration.toInt()
            }
            mediaPlayer!!.addProgressListener(rpl, 1000)
            mediaPlayer!!.registerCallback(rmcCallback)

        } catch (e: IllegalStateException) {
            Log.e(TAG, "Problem occurred with media during loading", e)
            failedLoad()
        } catch (e: Exception) {
            Log.e(TAG, "Problem opening media during loading", e)
            failedLoad()
        }

    }

    private fun entryToQueueItem(file: DownloadFile): MediaQueueItem {
        val song = file.song
        val musicService = MusicServiceFactory.getMusicService(downloadService)
        val url = getStreamUrl(musicService, file)

        // Setup song/video information
        val meta = MediaMetadata(if (song.isVideo) MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        meta.putString(MediaMetadata.KEY_TITLE, song.title)

        song.track?.let { meta.putInt(MediaMetadata.KEY_TRACK_NUMBER, it) }

        if (!song.isVideo) {
            meta.putString(MediaMetadata.KEY_ARTIST, song.artist)
            meta.putString(MediaMetadata.KEY_ALBUM_ARTIST, song.artist)
            meta.putString(MediaMetadata.KEY_ALBUM_TITLE, song.album)

            val coverArt = musicService.getCoverArtUrl(downloadService, song)

            meta.addImage(WebImage(Uri.parse(coverArt)))
        }

        val contentType: String?
        contentType = when {
            song.isVideo -> "application/x-mpegURL"
            song.transcodedContentType != null -> song.transcodedContentType
            song.contentType != null -> song.contentType
            else -> "audio/mpeg"
        }

        // Load it into a MediaInfo wrapper
        val mediaInfo = MediaInfo.Builder(url)
                .setContentType(contentType)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(meta)
                .build()

        return MediaQueueItem.Builder(mediaInfo).build()
    }

    private fun failedLoad() {
        Util.toast(downloadService, downloadService.resources.getString(R.string.download_failed_to_load))
        downloadService.setPlayerState(PlayerState.STOPPED)
        error = true
    }

    fun setSession(mCastSession: CastSession) {
        mediaPlayer = mCastSession.remoteMediaClient
    }

    companion object {
        private val TAG = ChromeCastController::class.java.simpleName
    }
}