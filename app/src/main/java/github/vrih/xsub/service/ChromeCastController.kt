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
    private val maxPlaylistSize = 50

    private var mediaPlayer: RemoteMediaClient? = null
    private var gain = 0.5
    private var cachedProgress: Int = 0
    private var cachedDuration: Int = 0
    private var indexOffset = 0

    private val rmcCallback = object : RemoteMediaClient.Callback() {
        override fun onQueueStatusUpdated() {
            super.onQueueStatusUpdated()
            val mediaQueueItem = mediaPlayer!!.currentItem
            val mediaQueue = mediaPlayer!!.mediaQueue
            if (mediaQueueItem != null) {
                val index = mediaQueue.indexOfItemWithId(mediaQueueItem.itemId)
                downloadService.setCurrentPlaying(index + indexOffset)
            }

            // If there are more tracks then append to playlist.
            if(indexOffset > 0) {
                val songList = downloadService.songs
                val index = mediaQueue.indexOfItemWithId(mediaQueueItem.itemId)
                if(songList.size > indexOffset + maxPlaylistSize) {
                    appendPlaylist(listOf(downloadService.songs[index + indexOffset + maxPlaylistSize]))
                }
            }
        }
    }

    override fun create(playing: Boolean, seconds: Int) {
        downloadService.setPlayerState(PlayerState.PREPARING)

        //ConnectionCallbacks connectionCallbacks = new ConnectionCallbacks(playing, seconds);
        //		ConnectionFailedListener connectionFailedListener = new ConnectionFailedListener();
    }

    override fun start() {
        if (error) {
            error = false
            //startSong(downloadService.getCurrentPlaying(), true, 0);
            return
        }

        try {
            val result = mediaPlayer!!.play()
            result.setResultCallback { res ->
                if (res.status.isSuccess) {
                    downloadService.setPlayerState(PlayerState.STARTED)
                }
            }
        } catch (e: Exception) {
        }

    }

    override fun stop() {
        try {
            mediaPlayer!!.pause()
        } catch (e: Exception) {
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
        }

        try {
            mediaPlayer = null
        } catch (e: Exception) {
        }
    }

    override fun updatePlaylist() {
        if (downloadService.currentPlaying == null) {
            //startSong(null, false, 0);
        }
    }

    override fun setCurrentPlaying(index: Int){
        val adjustedIndex = index - indexOffset
        if(adjustedIndex in 0..(maxPlaylistSize - 1)) {
            mediaPlayer?.queueJumpToItem(mediaPlayer?.mediaQueue?.itemIdAtIndex(index - indexOffset)
                    ?: 0, null)
        } else {
        }
    }

    override fun insertPlaylist(songs: List<DownloadFile>, index: Int) {
        val queue = ArrayList<MediaQueueItem>()

        for (song in songs) queue.add(entryToQueueItem(song))
        mediaPlayer?.let {
            it.queueInsertItems(queue.toTypedArray(), it.mediaQueue?.itemIdAtIndex(index + 1) ?: 0, null)
        }
    }

    fun resetPlaylist(){
        mediaPlayer!!.mediaQueue.clear()
    }

    override fun appendPlaylist(songs: List<DownloadFile>) {
        Log.w("CAST", "Append $songs")
        // If the remote playlist is less than the maximum playlist size then we should assume it's
        // safe to append. If it is larger than the max then we'll do nothing in case we end up
        // with an out of order playlist

        if(mediaPlayer!!.mediaQueue.itemCount < maxPlaylistSize) {
            for (song in songs)
                mediaPlayer!!.queueAppendItem(entryToQueueItem(song), null)
        }
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

        if (playlist == null || playlist.isEmpty()) {
            try {
                if (mediaPlayer != null && !error && !isStopping) {
                    isStopping = true
                    mediaPlayer?.stop()?.setResultCallback {
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
            // There is a limit to the size of playlist that can be sent to chromecast. We limit to the next 20 tracks
            var shouldAdd = true
            for ((i, file )in playlist.withIndex()) {
                if (playlist.size > maxPlaylistSize) {
                    shouldAdd = when {
                        i < index -> false
                        i > index + 20 -> false
                        else -> true
                    }
                }
                if (shouldAdd) queue.add(entryToQueueItem(file))
            }

            // Reset index size to 0 if playlist was > MAX_PLAYLIST_SIZE
            val newIndex = if (playlist.size > maxPlaylistSize) 0 else index
            indexOffset = if(playlist.size > maxPlaylistSize) index else 0

            val callback = ResultCallback<RemoteMediaClient.MediaChannelResult> { result ->
                when {
                    result.status.isSuccess -> downloadService.setPlayerState(PlayerState.STARTED)
                    // Handled in other handler
                    result.status.statusCode == CastStatusCodes.REPLACED -> Log.i("CAST", "Playlist was replaced")
                    else -> {
                        failedLoad()
                    }
                }
            }

            val a = mediaPlayer!!.queueLoad(
                    queue.toTypedArray(),
                    newIndex,
                    MediaStatus.REPEAT_MODE_REPEAT_OFF,
                    position * 1000L,
                    null)
            a.setResultCallback(callback)
            val rpl = RemoteMediaClient.ProgressListener { progress, duration ->
                cachedProgress = progress.toInt()
                cachedDuration = duration.toInt()
            }
            mediaPlayer!!.addProgressListener(rpl, 1000)
            mediaPlayer!!.registerCallback(rmcCallback)

        } catch (e: IllegalStateException) {
            failedLoad()
        } catch (e: Exception) {
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

    companion object
}