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

 Copyright 2009 (C) Sindre Mehus
 */
package github.vrih.xsub.service

import android.annotation.TargetApi
import android.app.Service
import android.content.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.AudioEffect
import android.net.wifi.WifiManager
import android.os.*
import android.util.Log
import android.view.KeyEvent
import androidx.collection.LruCache
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import github.daneren2005.serverproxy.BufferProxy
import github.vrih.xsub.R
import github.vrih.xsub.activity.SubsonicActivity
import github.vrih.xsub.audiofx.AudioEffectsController
import github.vrih.xsub.audiofx.EqualizerController
import github.vrih.xsub.domain.*
import github.vrih.xsub.domain.PlayerState.*
import github.vrih.xsub.domain.RemoteControlState.CHROMECAST
import github.vrih.xsub.domain.RemoteControlState.LOCAL
import github.vrih.xsub.receiver.AudioNoisyReceiver
import github.vrih.xsub.receiver.MediaButtonIntentReceiver
import github.vrih.xsub.util.*
import github.vrih.xsub.util.MediaRouteManager
import github.vrih.xsub.util.compat.RemoteControlClientBase
import github.vrih.xsub.util.tags.BastpUtil
import github.vrih.xsub.view.UpdateView
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class DownloadService: Service() {

    var remoteControlClient:RemoteControlClientBase? = null
        private set

    private val binder = SimpleServiceBinder(this)
    private var mediaPlayerLooper:Looper? = null
    private var mediaPlayer:MediaPlayer? = null
    private var nextMediaPlayer:MediaPlayer? = null
    private var audioSessionId:Int = 0
    private var nextSetup = false
    private val downloadList = ArrayList<DownloadFile>()
    private val backgroundDownloadList = ArrayList<DownloadFile>()
    private val toDelete = ArrayList<DownloadFile>()
    private val handler = Handler()
    private var mediaPlayerHandler:Handler? = null
    private val lifecycleSupport = DownloadServiceLifecycleSupport(this)
    private var shufflePlayBuffer:ShufflePlayBuffer? = null
    private var artistRadioBuffer:ArtistRadioBuffer? = null

    private val downloadFileCache = LruCache<MusicDirectory.Entry, DownloadFile>(100)
    private val cleanupCandidates = ArrayList<DownloadFile>()
    private val scrobbler = Scrobbler()
    var remoteController:RemoteController? = null
        private set
    var currentPlaying:DownloadFile? = null
    var currentPlayingIndex = -1
        private set
    private var nextPlaying:DownloadFile? = null
    var currentDownloading:DownloadFile? = null
        private set
    private var bufferTask:SilentBackgroundTask<*>? = null
    private var nextPlayingTask:SilentBackgroundTask<*>? = null
    private var playerState = IDLE
    private var nextPlayerState = IDLE
    private var removePlayed:Boolean = false
    private var shufflePlay:Boolean = false
    var isArtistRadio:Boolean = false
        private set
    private val onSongChangedListeners = CopyOnWriteArrayList<OnSongChangedListener>()
    var downloadListUpdateRevision:Long = 0
        private set
    var suggestedPlaylistName:String? = null
        private set
    var suggestedPlaylistId:String? = null
        private set
    private var wakeLock:PowerManager.WakeLock? = null
    private var wifiLock:WifiManager.WifiLock? = null
    private var keepScreenOn:Boolean = false
    private var cachedPosition = 0
    private var downloadOngoing = false
    private var volume = 1.0f
    private var delayUpdateProgress = DEFAULT_DELAY_UPDATE_PROGRESS
    @get:Synchronized
    var isFrontal = false
    private var effectsController:AudioEffectsController? = null
    private var remoteState = LOCAL
    private var positionCache:PositionCache? = null
    private var proxy:BufferProxy? = null

    private var sleepTimer:Timer? = null
    private var timerDuration:Int = 0
    private var timerStart:Long = 0
    private var autoPlayStart = false
    private var runListenersOnInit = false

    private val audioNoisyIntent = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private var audioNoisyReceiver:AudioNoisyReceiver? = null

    private var mediaRouter:MediaRouteManager? = null
        private set

    // Variables to manage getCurrentPosition sometimes starting from an arbitrary non-zero number
    private var subtractNextPosition:Long = 0
    private var subtractPosition = 0

    val isNotInitialized:Boolean
        get() = true

    val lastStateChanged:Date
        @Synchronized get() =lifecycleSupport.lastChange
    var isRemovePlayed:Boolean
        get() =removePlayed
        @Synchronized set(enabled) {
            removePlayed = enabled
            if (removePlayed)
            {
                checkDownloads()
                lifecycleSupport.serializeDownloadQueue()
            }
            val editor = Util.getPreferences(this).edit()
            editor.putBoolean(Constants.PREFERENCES_KEY_REMOVE_PLAYED, enabled)
            editor.apply()
        }

    var isShufflePlayEnabled:Boolean
        get() =shufflePlay
        @Synchronized set(enabled) {
            shufflePlay = enabled
            if (shufflePlay)
            {
                checkDownloads()
            }
            val editor = Util.getPreferences(this).edit()
            editor.putInt(Constants.PREFERENCES_KEY_SHUFFLE_MODE, if (enabled) SHUFFLE_MODE_ALL else SHUFFLE_MODE_NONE)
            editor.apply()
        }

    var repeatMode:RepeatMode
        get() =Util.getRepeatMode(this)
        set(repeatMode) {
            Util.setRepeatMode(this, repeatMode)
            setNextPlaying()
        }
    private val nextPlayingIndex:Int
        get() {
            var index = currentPlayingIndex
            if (index != -1)
            {
                val repeatMode = repeatMode
                when (repeatMode) {
                    RepeatMode.OFF -> index += 1
                    RepeatMode.ALL -> index = (index + 1) % size()
                    RepeatMode.SINGLE -> {}
                }

                index = checkNextIndexValid(index, repeatMode)
            }
            return index
        }

    val songs:List<DownloadFile>
        get() =downloadList

    val isCurrentPlayingSingle:Boolean
        get() = currentPlaying != null && currentPlaying?.song is InternetRadioStation

    val downloads:List<DownloadFile>
        @Synchronized get() {
            val temp = ArrayList<DownloadFile>()
            temp.addAll(downloadList)
            temp.addAll(backgroundDownloadList)
            return temp
        }

    val recentDownloads:List<DownloadFile>
        @Synchronized get() {
            val from = Math.max(currentPlayingIndex - 10, 0)
            val songsToKeep = Math.max(Util.getPreloadCount(this), 20)
            val to = Math.min(currentPlayingIndex + songsToKeep, Math.max(downloadList.size - 1, 0))
            val temp = downloadList.subList(from, to)
            temp.addAll(backgroundDownloadList)
            return temp
        }

    val backgroundDownloads:List<DownloadFile>
        get() =backgroundDownloadList

    val playerPosition:Int
        get() {
            try
            {
                if (playerState === IDLE || playerState === DOWNLOADING || playerState === PREPARING && remoteState !== CHROMECAST)
                {
                    return 0
                }
                return if (remoteState !== LOCAL) {
                    remoteController!!.remotePosition * 1000
                } else {
                    Math.max(0, cachedPosition - subtractPosition)
                }
            }
            catch (x:Exception) {
                handleError(x)
                return 0
            }

        }

    val playerDuration:Int
        @Synchronized get() {
            if (playerState !== IDLE && playerState !== DOWNLOADING && playerState !== PlayerState.PREPARING && remoteState !== CHROMECAST)
            {
                val duration = if (remoteState === LOCAL) {
                    try {
                        mediaPlayer!!.duration
                    } catch (x:Exception) {
                        0
                    }

                } else {
                    remoteController!!.remoteDuration * 1000
                }

                if (duration != 0)
                {
                    return duration
                }
            }

            if (currentPlaying != null)
            {
                val duration = currentPlaying!!.song.duration
                if (duration != null)
                {
                    return duration * 1000
                }
            }

            return 0
        }

    val equalizerAvailable:Boolean
        get() =effectsController!!.isAvailable

    // If we failed, we are going to try to reinitialize the MediaPlayer
    // Resetup media player
    // Don't try again, just resetup media player and continue on
    // Restart from same position and state we left off in
    val equalizerController:EqualizerController?
        get() {
            var controller:EqualizerController?
            try
            {
                controller = effectsController!!.equalizerController
                if (controller!!.equalizer == null)
                {
                    throw Exception("Failed to get EQ")
                }
            }
            catch (e:Exception) {
                Log.w(TAG, "Failed to start EQ, retrying with new mediaPlayer: $e")
                val pos = playerPosition
                mediaPlayer!!.pause()
                Util.sleepQuietly(10L)
                reset()

                try
                {
                    mediaPlayer!!.audioSessionId = audioSessionId
                    mediaPlayer!!.setDataSource(currentPlaying!!.file.canonicalPath)

                    controller = effectsController!!.equalizerController
                    if (controller!!.equalizer == null)
                    {
                        throw Exception("Failed to get EQ")
                    }
                }
                catch (e2:Exception) {
                    Log.w(TAG, "Failed to setup EQ even after reinitialization")
                    controller = null
                }

                play(currentPlayingIndex, false, pos)
            }

            return controller
        }
    val remoteSelector:MediaRouteSelector
        get() =mediaRouter!!.selector

    private val isSeekable:Boolean
        get() = when {
            remoteState === LOCAL -> currentPlaying?.isWorkDone != null && playerState !== PREPARING
            remoteController != null -> remoteController!!.isSeekable
            else -> false
        }

    val isRemoteEnabled:Boolean
        get() =remoteState !== LOCAL

    val sleepTimeRemaining:Int
        get() =(timerStart + timerDuration * 60 * 1000 - System.currentTimeMillis()).toInt() / 1000

    private val isPastCutoff:Boolean
        get() =isPastCutoff(playerPosition, playerDuration)

    var playbackSpeed:Float
        get() =if (currentPlaying == null)
            1.0f
        else {
            if (currentPlaying!!.isSong)
                Util.getPreferences(this).getFloat(Constants.PREFERENCES_KEY_SONG_PLAYBACK_SPEED, 1.0f)
            else
                Util.getPreferences(this).getFloat(Constants.PREFERENCES_KEY_PLAYBACK_SPEED, 1.0f)
        }
        set(playbackSpeed) {
            if (currentPlaying!!.isSong)
                Util.getPreferences(this).edit().putFloat(Constants.PREFERENCES_KEY_SONG_PLAYBACK_SPEED, playbackSpeed).apply()
            else
                Util.getPreferences(this).edit().putFloat(Constants.PREFERENCES_KEY_PLAYBACK_SPEED, playbackSpeed).apply()
            if (mediaPlayer != null && (playerState === PREPARED || playerState === STARTED || playerState === PAUSED || playerState === PAUSED_TEMP))
            {
                applyPlaybackParamsMain()
            }

            delayUpdateProgress = Math.round(DEFAULT_DELAY_UPDATE_PROGRESS / playbackSpeed).toLong()
        }

    override fun onCreate() {
        super.onCreate()

        val prefs = Util.getPreferences(this)
        Thread(Runnable {
            Looper.prepare()

            mediaPlayer = MediaPlayer()
            mediaPlayer!!.setWakeMode(this@DownloadService, PowerManager.PARTIAL_WAKE_LOCK)

            // We want to change audio session id's between upgrading Android versions.  Upgrading to Android 7.0 is broken (probably updated session id format)
            audioSessionId = -1
            val id = prefs.getInt(Constants.CACHE_AUDIO_SESSION_ID, -1)
            val versionCode = prefs.getInt(Constants.CACHE_AUDIO_SESSION_VERSION_CODE, -1)
            if (versionCode == Build.VERSION.SDK_INT && id != -1)
            {
                try
                {
                    audioSessionId = id
                    mediaPlayer!!.audioSessionId = audioSessionId
                }
                catch (e:Throwable) {
                    Log.w(TAG, "Failed to use cached audio session", e)
                    audioSessionId = -1
                }

            }

            if (audioSessionId == -1)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                } else {
                    @Suppress("DEPRECATION")
                    mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                try
                {
                    audioSessionId = mediaPlayer!!.audioSessionId

                    val editor = prefs.edit()
                    editor.putInt(Constants.CACHE_AUDIO_SESSION_ID, audioSessionId)
                    editor.putInt(Constants.CACHE_AUDIO_SESSION_VERSION_CODE, Build.VERSION.SDK_INT)
                    editor.apply()
                }
                catch (t:Throwable) {
                    // Froyo or lower
                }

            }

            mediaPlayer!!.setOnErrorListener{ _, what, more ->
                handleError(Exception("MediaPlayer error: $what ($more)"))
                false
            }

            /*try {
                            Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
                            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
                            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
                            sendBroadcast(i);
                        } catch(Throwable e) {
                            // Froyo or lower
                        }*/

            effectsController = AudioEffectsController(this@DownloadService, audioSessionId)
            if (prefs.getBoolean(Constants.PREFERENCES_EQUALIZER_ON, false))
            {
                equalizerController
            }

            mediaPlayerLooper = Looper.myLooper()
            mediaPlayerHandler = Handler(mediaPlayerLooper)

            if (runListenersOnInit)
            {
                onSongsChanged()
                onSongProgress()
                onStateUpdate()
            }

            Looper.loop()
        }, "DownloadService").start()

        Util.registerMediaButtonEventReceiver(this)
        audioNoisyReceiver = AudioNoisyReceiver()
        registerReceiver(audioNoisyReceiver, audioNoisyIntent)

        if (remoteControlClient == null)
        {
            // Use the remote control APIs (if available) to set the playback state
            remoteControlClient = RemoteControlClientBase.createInstance()
            val mediaButtonReceiverComponent = ComponentName(packageName, MediaButtonIntentReceiver::class.java.name)
            remoteControlClient!!.register(this, mediaButtonReceiverComponent)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.javaClass.name)
        wakeLock!!.setReferenceCounted(false)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "downloadServiceLock")

        timerDuration = try
        {
            Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_SLEEP_TIMER_DURATION, "5")!!)
        }
        catch (e:Throwable) {
            5
        }

        sleepTimer = null

        keepScreenOn = prefs.getBoolean(Constants.PREFERENCES_KEY_KEEP_SCREEN_ON, false)

        mediaRouter = MediaRouteManager(this)

        instance = this
        shufflePlayBuffer = ShufflePlayBuffer(this)
        artistRadioBuffer = ArtistRadioBuffer(this)
        lifecycleSupport.onCreate()

        if (Build.VERSION.SDK_INT >= 26)
        {
            Notifications.shutGoogleUpNotification(this)
        }
    }

    override fun onStartCommand(intent:Intent, flags:Int, startId:Int):Int {
        super.onStartCommand(intent, flags, startId)
        lifecycleSupport.onStart(intent)
        if (Build.VERSION.SDK_INT >= 26 && !this.isFrontal)
        {
            Notifications.shutGoogleUpNotification(this)
        }
        return Service.START_NOT_STICKY
    }

    override fun onTrimMemory(level:Int) {
        val imageLoader = SubsonicActivity.getStaticImageLoader(this)
        if (imageLoader != null)
        {
            Log.i(TAG, "Memory Trim Level: $level")
            when {
                level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> when {
                    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> imageLoader.onLowMemory(0.75f)
                    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> imageLoader.onLowMemory(0.50f)
                    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> imageLoader.onLowMemory(0.25f)
                }
                level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> imageLoader.onLowMemory(0.25f)
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> imageLoader.onLowMemory(0.75f)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null

        if (currentPlaying != null) currentPlaying!!.playing = false
        if (sleepTimer != null)
        {
            sleepTimer!!.cancel()
            sleepTimer!!.purge()
        }
        lifecycleSupport.onDestroy()

        try
        {
            val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            sendBroadcast(i)
        }
        catch (e:Throwable) {
            // Froyo or lower
        }

        mediaPlayer!!.release()
        if (nextMediaPlayer != null)
        {
            nextMediaPlayer!!.release()
        }
        mediaPlayerLooper!!.quit()
        shufflePlayBuffer!!.shutdown()
        effectsController!!.release()
        if (remoteControlClient != null)
        {
            remoteControlClient!!.unregister(this)
            remoteControlClient = null
        }

        if (bufferTask != null)
        {
            bufferTask!!.cancel()
            bufferTask = null
        }
        if (nextPlayingTask != null)
        {
            nextPlayingTask!!.cancel()
            nextPlayingTask = null
        }
        if (remoteController != null)
        {
            remoteController!!.stop()
            remoteController!!.shutdown()
        }
        if (proxy != null)
        {
            proxy!!.stop()
            proxy = null
        }
        if (audioNoisyReceiver != null)
        {
            unregisterReceiver(audioNoisyReceiver)
        }
        mediaRouter!!.destroy()
        Notifications.hidePlayingNotification(this, this, handler)
        Notifications.hideDownloadingNotification(this, this, handler)
    }

    override fun onBind(intent:Intent):IBinder? {
        return binder
    }

    fun post(r:Runnable) {
        handler.post(r)
    }
    fun postDelayed(r:Runnable, millis:Long) {
        handler.postDelayed(r, millis)
    }

    @Synchronized  fun download(station:InternetRadioStation) {
        clear()
        download(listOf(station as MusicDirectory.Entry), false, true, false, false)
    }
    @Synchronized  fun download(songs:List<MusicDirectory.Entry>, save:Boolean, autoplay:Boolean, playNext:Boolean, shuffle:Boolean) {
        download(songs, save, autoplay, playNext, shuffle, 0, 0)
    }
    @Synchronized  fun download(songs: List<MusicDirectory.Entry>, save:Boolean, autoplay:Boolean, playNext: Boolean, shuffle:Boolean, start:Int, position:Int) {
        isShufflePlayEnabled = false
        setArtistRadio(null)
        var offset = 1
        val noNetwork = !Util.isOffline(this) && !Util.isNetworkConnected(this)
        var warnNetwork = false
        var newDownloadList = ArrayList<DownloadFile>()

        if (songs.isEmpty()) return

        if (isCurrentPlayingSingle) clear()

        if (playNext)
        {
            if (autoplay && currentPlayingIndex >= 0)
            {
                offset = 0
            }
            for (song in songs)
            {
                val downloadFile = DownloadFile(this, song, save)
                newDownloadList.add(downloadFile)
                addToDownloadList(downloadFile, currentPlayingIndex + offset)
                if (noNetwork && !warnNetwork)
                {
                    if (!downloadFile.isCompleteFileAvailable)
                    {
                        warnNetwork = true
                    }
                }
                offset++
            }

            if (remoteState === LOCAL || remoteController != null && remoteController!!.isNextSupported)
            {
                setNextPlaying()
            }
        }
        else
        {
            val size = size()
            val index = currentPlayingIndex

            // Add each song to the local playlist
            for (song in songs)
            {
                val downloadFile = DownloadFile(this, song, save)
                newDownloadList.add(downloadFile)
                addToDownloadList(downloadFile, -1)
                if (noNetwork && !warnNetwork)
                {
                    if (!downloadFile.isCompleteFileAvailable) warnNetwork = true
                }
            }

            if (!autoplay && size - 1 == index) setNextPlaying()
        }
        downloadListUpdateRevision++
        onSongsChanged()
        alterRemotePlaylist(newDownloadList, playNext)

        if (shuffle)
        {
            shuffle()
        }
        if (warnNetwork)
        {
            Util.toast(this, R.string.select_album_no_network)
        }

        if (autoplay)
        {
            play(start, true, position)
        }
        else if (start != 0 || position != 0)
        {
            play(start, false, position)
        }
        else
        {
            val cp = currentPlaying
            if (cp == null)
            {
                currentPlaying = downloadList[0]
                currentPlayingIndex = 0
                currentPlaying?.playing = true
            }
            else
            {
                currentPlayingIndex = downloadList.indexOf(cp)
            }
            checkDownloads()
        }
        lifecycleSupport.serializeDownloadQueue()
    }
    private fun addToDownloadList(file:DownloadFile, offset:Int) {
        if (offset == -1)
        {
            downloadList.add(file)
        }
        else
        {
            downloadList.add(offset, file)
        }
    }
    @Synchronized  fun downloadBackground(songs:List<MusicDirectory.Entry>, save:Boolean) {
        for (song in songs)
        {
            val downloadFile = DownloadFile(this, song, save)
            if (!downloadFile.isWorkDone || downloadFile.shouldSave() && !downloadFile.isSaved)
            {
                // Only add to list if there is work to be done
                backgroundDownloadList.add(downloadFile)
            }
            else if (downloadFile.isSaved && !save)
            {
                // Quickly unpin song instead of adding it to work to be done
                downloadFile.unpin()
            }
        }
        downloadListUpdateRevision++

        if (!Util.isOffline(this) && !Util.isNetworkConnected(this))
        {
            Util.toast(this, R.string.select_album_no_network)
        }

        checkDownloads()
        lifecycleSupport.serializeDownloadQueue()
    }

    @Synchronized private fun updateRemotePlaylist() {
        val playlist = ArrayList<DownloadFile>()
        val cp = currentPlaying
        if (cp != null)
        {
            var startIndex = downloadList.indexOf(cp) - REMOTE_PLAYLIST_PREV
            if (startIndex < 0)
            {
                startIndex = 0
            }

            val size = size()
            var endIndex = downloadList.indexOf(cp) + REMOTE_PLAYLIST_NEXT
            if (endIndex > size)
            {
                endIndex = size
            }
            for (i in startIndex until endIndex)
            {
                playlist.add(downloadList[i])
            }
        }

        if (remoteState !== LOCAL && remoteController != null)
        {
            remoteController!!.updatePlaylist()
        }
        remoteControlClient!!.updatePlaylist(playlist)
    }


    @Synchronized private fun alterRemotePlaylist(songs: List<DownloadFile>, playNext: Boolean) {
        val playlist = ArrayList<DownloadFile>()

        val cp = currentPlaying
        if (cp != null)
        {
            var startIndex = downloadList.indexOf(cp) - REMOTE_PLAYLIST_PREV
            if (startIndex < 0)
            {
                startIndex = 0
            }

            val size = size()
            var endIndex = downloadList.indexOf(cp) + REMOTE_PLAYLIST_NEXT
            if (endIndex > size)
            {
                endIndex = size
            }
            for (i in startIndex until endIndex)
            {
                playlist.add(downloadList[i])
            }
        }

        Log.w("CAST", "remote state $remoteState")
        if (remoteState !== LOCAL && remoteController != null) {
            if (playNext) {
                remoteController!!.insertPlaylist(songs, currentPlayingIndex)
            } else {
                Log.w("CAST", "Append")
                remoteController!!.appendPlaylist(songs)
            }
        }
        remoteControlClient!!.updatePlaylist(playlist)
    }

    @Synchronized  fun restore(songs:List<MusicDirectory.Entry>, toDelete:List<MusicDirectory.Entry>?, currentPlayingIndex:Int, currentPlayingPosition:Int) {
        val prefs = Util.getPreferences(this)
        val newState = RemoteControlState.values()[prefs.getInt(Constants.PREFERENCES_KEY_CONTROL_MODE, 0)]
        if (newState !== LOCAL)
        {
            val id = prefs.getString(Constants.PREFERENCES_KEY_CONTROL_ID, null)
            setRemoteState(newState, null, id)
        }
        if (prefs.getBoolean(Constants.PREFERENCES_KEY_REMOVE_PLAYED, false))
        {
            removePlayed = true
        }
        val startShufflePlay = prefs.getInt(Constants.PREFERENCES_KEY_SHUFFLE_MODE, SHUFFLE_MODE_NONE)
        download(songs, false, false, false, false)
        if (startShufflePlay != SHUFFLE_MODE_NONE)
        {
            if (startShufflePlay == SHUFFLE_MODE_ALL)
            {
                shufflePlay = true
            }
            else if (startShufflePlay == SHUFFLE_MODE_ARTIST)
            {
                isArtistRadio = true
                artistRadioBuffer!!.restoreArtist(prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_MODE_EXTRA, null))
            }
            val editor = prefs.edit()
            editor.putInt(Constants.PREFERENCES_KEY_SHUFFLE_MODE, startShufflePlay)
            editor.apply()
        }
        if (currentPlayingIndex != -1)
        {
            while (mediaPlayer == null)
            {
                Util.sleepQuietly(50L)
            }

            play(currentPlayingIndex, autoPlayStart, currentPlayingPosition)
            autoPlayStart = false
        }

        if (toDelete != null)
        {
            for (entry in toDelete)
            {
                this.toDelete.add(forSong(entry))
            }
        }

        suggestedPlaylistName = prefs.getString(Constants.PREFERENCES_KEY_PLAYLIST_NAME, null)
        suggestedPlaylistId = prefs.getString(Constants.PREFERENCES_KEY_PLAYLIST_ID, null)
    }

    fun setArtistRadio(artistId:String?) {
        if (artistId == null)
        {
            isArtistRadio = false
        }
        else
        {
            isArtistRadio = true
            artistRadioBuffer!!.setArtist(artistId)
        }

        val editor = Util.getPreferences(this).edit()
        editor.putInt(Constants.PREFERENCES_KEY_SHUFFLE_MODE, if (artistId != null) SHUFFLE_MODE_ARTIST else SHUFFLE_MODE_NONE)
        if (artistId != null)
        {
            editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_MODE_EXTRA, artistId)
        }
        editor.apply()
    }

    @Synchronized  fun shuffle() {
        downloadList.shuffle()
        currentPlayingIndex = downloadList.indexOf(currentPlaying)
        if (currentPlaying != null)
        {
            downloadList.removeAt(currentPlayingIndex)
            downloadList.add(0, currentPlaying!!)
            currentPlayingIndex = 0
        }
        downloadListUpdateRevision++
        onSongsChanged()
        lifecycleSupport.serializeDownloadQueue()
        updateRemotePlaylist()
        setNextPlaying()
    }

    fun getKeepScreenOn():Boolean {
        return keepScreenOn
    }

    fun setKeepScreenOn(keepScreenOn:Boolean) {
        this.keepScreenOn = keepScreenOn

        val prefs = Util.getPreferences(this)
        val editor = prefs.edit()
        editor.putBoolean(Constants.PREFERENCES_KEY_KEEP_SCREEN_ON, keepScreenOn)
        editor.apply()
    }

    @Synchronized  fun forSong(song:MusicDirectory.Entry):DownloadFile {
        var returnFile:DownloadFile? = null
        for (downloadFile in downloadList)
        {
            if (downloadFile.song == song)
            {
                if (downloadFile.isDownloading && downloadFile.isDownloadRunning && downloadFile.partialFile.exists() || downloadFile.isWorkDone)
                {
                    // If downloading, return immediately
                    return downloadFile
                }
                else
                {
                    // Otherwise, check to make sure there isn't a background download going on first
                    returnFile = downloadFile
                }
            }
        }
        for (downloadFile in backgroundDownloadList)
        {
            if (downloadFile.song == song)
            {
                return downloadFile
            }
        }

        if (returnFile != null)
        {
            return returnFile
        }

        var downloadFile = downloadFileCache.get(song)
        if (downloadFile == null)
        {
            downloadFile = DownloadFile(this, song, false)
            downloadFileCache.put(song, downloadFile)
        }
        return downloadFile
    }

    @Synchronized fun clearBackground() {
        if (currentDownloading != null && backgroundDownloadList.contains(currentDownloading!!))
        {
            currentDownloading!!.cancelDownload()
            currentDownloading = null
        }
        backgroundDownloadList.clear()
        downloadListUpdateRevision++
        Notifications.hideDownloadingNotification(this, this, handler)
    }

    @Synchronized  fun clearIncomplete() {
        val iterator = downloadList.iterator()
        while (iterator.hasNext())
        {
            val downloadFile = iterator.next()
            if (!downloadFile.isCompleteFileAvailable)
            {
                iterator.remove()

                // Reset if the current playing song has been removed
                if (currentPlaying === downloadFile)
                {
                    reset()
                }

                currentPlayingIndex = downloadList.indexOf(currentPlaying)
            }
        }
        lifecycleSupport.serializeDownloadQueue()
        updateRemotePlaylist()
        onSongsChanged()
    }

    fun setOnline(online:Boolean) {
        if (online)
        {
            mediaRouter!!.addOnlineProviders()
        }
        else
        {
            mediaRouter!!.removeOnlineProviders()
        }
        if (shufflePlay)
        {
            isShufflePlayEnabled = false
        }
        if (isArtistRadio)
        {
            setArtistRadio(null)
        }

        lifecycleSupport.post{
            if (online)
            {
                checkDownloads()
            }
            else
            {
                clearIncomplete()
            }
        }
    }
    fun userSettingsChanged() {
        mediaRouter!!.buildSelector()
    }

    @Synchronized  fun size():Int {
        return downloadList.size
    }

    @Synchronized  fun clear() {
        // Delete podcast if fully listened to
        val position = playerPosition
        val duration = playerDuration
        val cutoff = isPastCutoff(position, duration, true)
        if (currentPlaying != null && currentPlaying!!.song is PodcastEpisode && !currentPlaying!!.isSaved)
        {
            if (cutoff)
            {
                currentPlaying!!.delete()
            }
        }
        for (podcast in toDelete)
        {
            podcast.delete()
        }
        toDelete.clear()

        // Clear bookmarks from current playing if past a certain point
        if (cutoff)
        {
            clearCurrentBookmark()
        }
        else
        {
            // Check if we should be adding a new bookmark here
            checkAddBookmark()
        }
        if (currentPlaying != null)
        {
            scrobbler.conditionalScrobble(this, currentPlaying, position, duration, cutoff)
        }

        reset()
        downloadList.clear()
        onSongsChanged()
        if (currentDownloading != null && !backgroundDownloadList.contains(currentDownloading!!))
        {
            currentDownloading?.cancelDownload()
            currentDownloading = null
        }
        setCurrentPlaying(null)

        lifecycleSupport.serializeDownloadQueue()

        updateRemotePlaylist()
        setNextPlaying()
        if (proxy != null)
        {
            proxy!!.stop()
            proxy = null
        }

        suggestedPlaylistName = null
        suggestedPlaylistId = null

        isShufflePlayEnabled = false
        setArtistRadio(null)
        checkDownloads()
    }

    @Synchronized  fun remove(downloadFile:DownloadFile) {
        if (downloadFile === currentDownloading)
        {
            currentDownloading!!.cancelDownload()
            currentDownloading = null
        }
        if (downloadFile === currentPlaying)
        {
            reset()
            setCurrentPlaying(null)
        }
        downloadList.remove(downloadFile)
        currentPlayingIndex = downloadList.indexOf(currentPlaying)
        backgroundDownloadList.remove(downloadFile)
        downloadListUpdateRevision++
        onSongsChanged()
        lifecycleSupport.serializeDownloadQueue()
        updateRemotePlaylist()
        if (downloadFile === nextPlaying)
        {
            setNextPlaying()
        }

        checkDownloads()
    }
    @Synchronized  fun removeBackground(downloadFile:DownloadFile) {
        if (downloadFile === currentDownloading && downloadFile !== currentPlaying && downloadFile !== nextPlaying)
        {
            currentDownloading!!.cancelDownload()
            currentDownloading = null
        }

        backgroundDownloadList.remove(downloadFile)
        downloadListUpdateRevision++
        checkDownloads()
    }

    @Synchronized  fun delete(songs:List<MusicDirectory.Entry>) {
        for (song in songs)
        {
            forSong(song).delete()
        }
    }

    @Synchronized fun setCurrentPlaying(currentPlayingIndex:Int) {
        if(remoteState == CHROMECAST){
            remoteController?.setCurrentPlaying(currentPlayingIndex)
        }
        try
        {
            setCurrentPlaying(downloadList[currentPlayingIndex])
        }
        catch (x:IndexOutOfBoundsException) {
            // Ignored
        }

    }

    @Synchronized internal fun setCurrentPlaying(currentPlaying:DownloadFile?) {
        if (this.currentPlaying != null)
        {
            this.currentPlaying!!.playing = false
        }
        if (delayUpdateProgress != DEFAULT_DELAY_UPDATE_PROGRESS && !isNextPlayingSameAlbum(currentPlaying, this.currentPlaying))
        {
            //			resetPlaybackSpeed();
        }
        this.currentPlaying = currentPlaying
        if (currentPlaying == null)
        {
            currentPlayingIndex = -1
            setPlayerState(IDLE)
        }
        else
        {
            currentPlayingIndex = downloadList.indexOf(currentPlaying)
        }

        if (currentPlaying != null && currentPlaying.song != null)
        {
            Util.broadcastNewTrackInfo(this, currentPlaying.song)

            if (remoteControlClient != null)
            {
                remoteControlClient!!.updateMetadata(this, currentPlaying.song)
            }
        }
        else
        {
            Util.broadcastNewTrackInfo(this, null)
            Notifications.hidePlayingNotification(this, this, handler)
        }
        onSongChanged()
    }

    @Synchronized private fun setNextPlaying() {
        val prefs = Util.getPreferences(this@DownloadService)

        // Only obey gapless playback for local
        if (remoteState === LOCAL)
        {
            val gaplessPlayback = prefs.getBoolean(Constants.PREFERENCES_KEY_GAPLESS_PLAYBACK, true)
            if (!gaplessPlayback)
            {
                nextPlaying = null
                nextPlayerState = IDLE
                return
            }
        }
        setNextPlayerState(IDLE)

        val index = nextPlayingIndex

        if (nextPlayingTask != null)
        {
            nextPlayingTask!!.cancel()
            nextPlayingTask = null
        }
        resetNext()

        if (index < size() && index != -1 && index != currentPlayingIndex)
        {
            nextPlaying = downloadList[index]

            if (remoteState === LOCAL)
            {
                nextPlayingTask = CheckCompletionTask(nextPlaying)
                nextPlayingTask!!.execute()
            }
            else if (remoteController != null && remoteController!!.isNextSupported)
            {
                remoteController!!.changeNextTrack(nextPlaying)
            }
        }
        else
        {
            if (remoteState === LOCAL)
            {
                // resetNext();
            }
            else if (remoteController != null && remoteController!!.isNextSupported)
            {
                remoteController!!.changeNextTrack(nextPlaying)
            }
            nextPlaying = null
        }
    }
    private fun checkNextIndexValid(idx:Int, repeatMode:RepeatMode):Int {
        var index = idx
        val startIndex = index
        val size = size()
        if (index < size && index != -1)
        {
            if (!Util.isAllowedToDownload(this))
            {
                var next = downloadList[index]
                while (!next.isCompleteFileAvailable)
                {
                    index++

                    if (index >= size)
                    {
                        if (repeatMode === RepeatMode.ALL)
                        {
                            index = 0
                        }
                        else
                        {
                            return -1
                        }
                    }
                    else if (index == startIndex)
                    {
                        handler.post{ Util.toast(this@DownloadService, R.string.download_playerstate_mobile_disabled) }
                        return -1
                    }

                    next = downloadList[index]
                }
            }
        }

        return index
    }

    fun getToDelete():List<DownloadFile> {
        return toDelete
    }

    @Synchronized  fun shouldFastForward():Boolean {
        return size() == 1 || currentPlaying != null && !currentPlaying!!.isSong
    }

    /** Plays either the current song (resume) or the first/next one in queue.  */
    @Synchronized  fun play() {
        val current = currentPlayingIndex
        if (current == -1)
        {
            play(0)
        }
        else
        {
            play(current)
        }
    }

    @Synchronized  fun play(index:Int) {
        play(index, true)
    }
    @Synchronized  fun play(downloadFile:DownloadFile) {
        play(downloadList.indexOf(downloadFile))
    }
    @Synchronized private fun play(index:Int, start:Boolean) {
        play(index, start, 0)
    }
    @Synchronized  fun play(index:Int, start:Boolean, position:Int) {
        val size = this.size()
        cachedPosition = 0
        if (index < 0 || index >= size)
        {
            reset()
            if (index >= size && size != 0)
            {
                setCurrentPlaying(0)
                Notifications.hidePlayingNotification(this, this, handler)
            }
            else
            {
                setCurrentPlaying(null)
            }
            lifecycleSupport.serializeDownloadQueue()
        }
        else
        {
            if (nextPlayingTask != null)
            {
                nextPlayingTask!!.cancel()
                nextPlayingTask = null
            }
            setCurrentPlaying(index)
            if (start && remoteState !== LOCAL)
            {
                if (remoteState === CHROMECAST)
                {
                    remoteController!!.changeTrack(index, downloadList, position)
                }
                else
                {
                    remoteController!!.changeTrack(index, currentPlaying, position)
                }
            }
            if (remoteState === LOCAL)
            {
                bufferAndPlay(position, start)
                checkDownloads()
                setNextPlaying()
            }
            else
            {
                checkDownloads()
            }
        }
    }
    @Synchronized private fun playNext() {
        if (nextPlaying != null && nextPlayerState === PlayerState.PREPARED)
        {
            if (!nextSetup)
            {
                playNext(true)
            }
            else
            {
                nextSetup = false
                playNext(false)
            }
        }
        else
        {
            onSongCompleted()
        }
    }
    @Synchronized private fun playNext(start:Boolean) {
        Util.broadcastPlaybackStatusChange(this, currentPlaying!!.song, PlayerState.PREPARED)

        // Swap the media players since nextMediaPlayer is ready to play
        subtractPosition = 0
        if (start)
        {
            nextMediaPlayer!!.start()
        }
        else if (!nextMediaPlayer!!.isPlaying)
        {
            Log.w(TAG, "nextSetup lied about it's state!")
            nextMediaPlayer!!.start()
        }
        else
        {
            Log.i(TAG, "nextMediaPlayer already playing")

            // Next time the cachedPosition is updated, use that as position 0
            subtractNextPosition = System.currentTimeMillis()
        }
        val tmp = mediaPlayer
        mediaPlayer = nextMediaPlayer
        nextMediaPlayer = tmp
        setCurrentPlaying(nextPlaying)
        setPlayerState(PlayerState.STARTED)
        setupHandlers(currentPlaying!!, false, start)
        applyPlaybackParamsMain()
        setNextPlaying()

        // Proxy should not be being used here since the next player was already setup to play
        if (proxy != null)
        {
            proxy!!.stop()
            proxy = null
        }
        checkDownloads()
        updateRemotePlaylist()
    }

    /** Plays or resumes the playback, depending on the current player state.  */
    @Synchronized  fun togglePlayPause() {
        if (playerState === PAUSED || playerState === COMPLETED || playerState === STOPPED)
        {
            start()
        }
        else if (playerState === STOPPED || playerState === IDLE)
        {
            autoPlayStart = true
            play()
        }
        else if (playerState === STARTED)
        {
            pause()
        }
    }

    /**
     * @param pos position in media in ms
     */
    @Synchronized  fun seekTo(pos:Int) {
        var position = pos
        if (position < 0)
        {
            position = 0
        }

        try
        {
            if (remoteState !== LOCAL)
            {
                remoteController!!.changePosition(position / 1000)
            }
            else
            {
                if (proxy != null && currentPlaying!!.isCompleteFileAvailable)
                {
                    doPlay(currentPlaying!!, position, playerState === STARTED)
                    return
                }

                mediaPlayer!!.seekTo(position)
                subtractPosition = 0
            }
            cachedPosition = position

            onSongProgress()
            if (playerState === PAUSED)
            {
                lifecycleSupport.serializeDownloadQueue()
            }
        }
        catch (x:Exception) {
            handleError(x)
        }

    }
    @Synchronized  fun rewind():Int {
        return seekToWrapper(Integer.parseInt(Util.getPreferences(this).getString(Constants.PREFERENCES_KEY_REWIND_INTERVAL, "10")!!) * -1000)
    }
    @Synchronized  fun fastForward():Int {
        return seekToWrapper(Integer.parseInt(Util.getPreferences(this).getString(Constants.PREFERENCES_KEY_FASTFORWARD_INTERVAL, "30")!!) * 1000)
    }

    /**
     * @param difference seek delta in ms
     * @return new location
     */
    private fun seekToWrapper(difference:Int):Int {
        val msPlayed = Math.max(0, playerPosition)
        val duration = playerDuration

        val seekTo:Int
        seekTo = if (msPlayed + difference > duration)
        {
            duration
        }
        else
        {
            msPlayed + difference
        }
        seekTo(seekTo)

        return seekTo
    }

    @Synchronized  fun previous() {
        // If using Chromecast then rely on chromecast queue behaviour
        if (remoteState === CHROMECAST)
        {
            remoteController!!.previous()
            return
        }

        var index = currentPlayingIndex
        if (index == -1)
        {
            return
        }

        // If only one song, just skip within song
        if (shouldFastForward())
        {
            rewind()
            return
        }
        else if (playerState === PREPARING || playerState === PREPARED)
        {
            return
        }

        // Restart song if played more than five seconds.
        if (playerPosition > 5000 || index == 0 && repeatMode !== RepeatMode.ALL)
        {
            seekTo(0)
        }
        else
        {
            if (index == 0)
            {
                index = size()
            }

            play(index - 1, playerState !== PAUSED && playerState !== STOPPED && playerState !== IDLE)
        }
    }

    @Synchronized operator  fun next() {
        next(false)
    }
    @Synchronized private fun next(forceCutoff:Boolean) {
        next(forceCutoff, false)
    }
    @Synchronized  fun next(forceCutoff:Boolean, forceStart:Boolean) {
        // If only one song, just skip within song
        if (shouldFastForward())
        {
            fastForward()
            return
        }
        else if (playerState === PREPARING || playerState === PREPARED)
        {
            return
        }

        // Delete podcast if fully listened to
        val position = playerPosition
        val duration = playerDuration
        val cutoff:Boolean
        if (forceCutoff)
        {
            cutoff = true
        }
        else
        {
            cutoff = isPastCutoff(position, duration)
        }
        if (currentPlaying != null && currentPlaying!!.song is PodcastEpisode && !currentPlaying!!.isSaved)
        {
            if (cutoff)
            {
                toDelete.add(currentPlaying!!)
            }
        }
        if (cutoff)
        {
            clearCurrentBookmark()
        }
        if (currentPlaying != null)
        {
            scrobbler.conditionalScrobble(this, currentPlaying, position, duration, cutoff)
        }

        if (remoteState === CHROMECAST)
        {
            remoteController!!.next()
        }
        else
        {
            val index = currentPlayingIndex
            var nextPlayingIndex = nextPlayingIndex
            // Make sure to actually go to next when repeat song is on
            if (index == nextPlayingIndex)
            {
                nextPlayingIndex++
            }
            if (index != -1 && nextPlayingIndex < size())
            {
                play(nextPlayingIndex, playerState !== PAUSED && playerState !== STOPPED && playerState !== IDLE || forceStart)
            }
        }
    }

    fun onSongCompleted() {
        setPlayerStateCompleted()
        postPlayCleanup()
        play(nextPlayingIndex)
    }
    fun onNextStarted(nextPlaying:DownloadFile) {
        setPlayerStateCompleted()
        postPlayCleanup()
        setCurrentPlaying(nextPlaying)
        setPlayerState(STARTED)
        setNextPlayerState(IDLE)
    }

    @Synchronized  fun pause() {
        pause(false)
    }
    @Synchronized  fun pause(temp:Boolean) {
        try
        {
            if (playerState === STARTED)
            {
                if (remoteState !== LOCAL)
                {
                    remoteController!!.stop()
                }
                else
                {
                    mediaPlayer!!.pause()
                }
                setPlayerState(if (temp) PAUSED_TEMP else PAUSED)
            }
            else if (playerState === PAUSED_TEMP)
            {
                setPlayerState(if (temp) PAUSED_TEMP else PAUSED)
            }
        }
        catch (x:Exception) {
            handleError(x)
        }

    }

    @Synchronized  fun stop() {
        try
        {
            Log.w("CAST", "DS remote state: $remoteState")
            if (playerState === STARTED)
            {
                if (remoteState !== LOCAL)
                {
                    remoteController!!.stop()
                    setPlayerState(STOPPED)
                    handler.post{ mediaRouter!!.setDefaultRoute() }
                }
                else
                {
                    mediaPlayer!!.pause()
                    setPlayerState(STOPPED)
                }
            }
            else if (playerState === PAUSED)
            {
                setPlayerState(STOPPED)
            }
        }
        catch (x:Exception) {
            handleError(x)
        }

    }

    @Synchronized  fun start() {
        try
        {
            if (remoteState !== LOCAL)
            {
                remoteController!!.start()
            }
            else
            {
                // Only start if done preparing
                if (playerState !== PREPARING)
                {
                    mediaPlayer!!.start()
                    applyPlaybackParamsMain()
                }
                else
                {
                    // Otherwise, we need to set it up to start when done preparing
                    autoPlayStart = true
                }
            }
            setPlayerState(STARTED)
        }
        catch (x:Exception) {
            handleError(x)
        }

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Synchronized  fun reset() {
        if (bufferTask != null)
        {
            bufferTask!!.cancel()
            bufferTask = null
        }
        try
        {
            // Only set to idle if it's not being killed to start RemoteController
            if (remoteState === LOCAL)
            {
                setPlayerState(IDLE)
            }
            mediaPlayer!!.setOnErrorListener(null)
            mediaPlayer!!.setOnCompletionListener(null)
            if (nextSetup)
            {
                mediaPlayer!!.setNextMediaPlayer(null)
                nextSetup = false
            }
            mediaPlayer!!.reset()
            subtractPosition = 0
        }
        catch (x:Exception) {
            handleError(x)
        }

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Synchronized private fun resetNext() {
        try
        {
            if (nextMediaPlayer != null)
            {
                if (nextSetup)
                {
                    mediaPlayer!!.setNextMediaPlayer(null)
                }
                nextSetup = false

                nextMediaPlayer!!.setOnCompletionListener(null)
                nextMediaPlayer!!.setOnErrorListener(null)
                nextMediaPlayer!!.reset()
                nextMediaPlayer!!.release()
                nextMediaPlayer = null
            }
            else if (nextSetup)
            {
                nextSetup = false
            }
        }
        catch (e:Exception) {
            Log.w(TAG, "Failed to reset next media player")
        }

    }

    fun getPlayerState():PlayerState {
        return playerState
    }

    fun getNextPlayerState():PlayerState {
        return nextPlayerState
    }

    @Synchronized  fun setPlayerState(playerState:PlayerState) {
        Log.i(TAG, this.playerState.name + " -> " + playerState.name + " (" + currentPlaying + ")")

        if (playerState === PAUSED)
        {
            lifecycleSupport.serializeDownloadQueue()
            if (!isPastCutoff)
            {
                checkAddBookmark(true)
            }
        }

        val show = playerState === PlayerState.STARTED
        val pause = playerState === PlayerState.PAUSED
        val hide = playerState === PlayerState.STOPPED
        Util.broadcastPlaybackStatusChange(this, if (currentPlaying != null) currentPlaying!!.song else null, playerState)

        this.playerState = playerState

        if (playerState === STARTED)
        {
            val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            Util.requestAudioFocus(this, audioManager)
        }

        if (show)
        {
            Notifications.showPlayingNotification(this, this, handler, currentPlaying!!.song)
        }
        else if (pause)
        {
            val prefs = Util.getPreferences(this)
            if (prefs.getBoolean(Constants.PREFERENCES_KEY_PERSISTENT_NOTIFICATION, false))
            {
                Notifications.showPlayingNotification(this, this, handler, currentPlaying!!.song)
            }
            else
            {
                Notifications.hidePlayingNotification(this, this, handler)
            }
        }
        else if (hide)
        {
            Notifications.hidePlayingNotification(this, this, handler)
        }
        if (remoteControlClient != null)
        {
            remoteControlClient!!.setPlaybackState(playerState.remoteControlClientPlayState, currentPlayingIndex, size())
        }

        if (playerState === STARTED)
        {
            scrobbler.scrobble(this, currentPlaying, false, false)
        }
        else if (playerState === COMPLETED)
        {
            scrobbler.scrobble(this, currentPlaying, true, true)
        }

        //if(playerState == STARTED && positionCache == null) {
        if ((playerState === STARTED || playerState === PREPARING && remoteState === CHROMECAST) && positionCache == null)
        {
            positionCache = if (remoteState === LOCAL) {
                LocalPositionCache()
            } else {
                PositionCache()
            }
            val thread = Thread(positionCache, "PositionCache")
            thread.start()
        }
        else if (playerState !== STARTED && positionCache != null)
        {
            positionCache!!.stop()
            positionCache = null
        }


        if (remoteState !== LOCAL)
        {
            if (playerState === STARTED)
            {
                if (!wifiLock!!.isHeld)
                {
                    wifiLock!!.acquire()
                }
            }
            else if (playerState === PAUSED && wifiLock!!.isHeld)
            {
                wifiLock!!.release()
            }
        }

        if (remoteController != null && remoteController!!.isNextSupported)
        {
            if (playerState === PREPARING || playerState === IDLE)
            {
                nextPlayerState = IDLE
            }
        }

        onStateUpdate()
    }

    private fun setPlayerStateCompleted() {
        // Acquire a temporary wakelock
        acquireWakelock()

        Log.i(TAG, this.playerState.name + " -> " + PlayerState.COMPLETED + " (" + currentPlaying + ")")
        this.playerState = PlayerState.COMPLETED
        positionCache?.stop()
        positionCache = null
        scrobbler.scrobble(this, currentPlaying, true, true)

        onStateUpdate()
    }

    private open inner class PositionCache:Runnable {
        internal open var isRunning = true

        internal open fun stop() {
            isRunning = false
        }

        override fun run() {
            // Stop checking position before the song reaches completion
            while (isRunning)
            {
                try
                {
                    onSongProgress()
                    Thread.sleep(delayUpdateProgress)
                }
                catch (e:Exception) {
                    isRunning = false
                    positionCache = null
                }

            }
        }
    }
    private inner class LocalPositionCache:PositionCache() {
        override var isRunning = true

        public override fun stop() {
            isRunning = false
        }

        override fun run() {
            // Stop checking position before the song reaches completion
            while (isRunning)
            {
                try
                {
                    if (mediaPlayer != null && playerState === STARTED)
                    {
                        val newPosition = mediaPlayer!!.currentPosition

                        // If sudden jump in position, something is wrong
                        if (subtractNextPosition == 0L && newPosition > cachedPosition + 5000)
                        {
                            // Only 1 second should have gone by, subtract the rest
                            subtractPosition += newPosition - cachedPosition - 1000
                        }

                        cachedPosition = newPosition

                        if (subtractNextPosition > 0)
                        {
                            // Subtraction amount is current position - how long ago onCompletionListener was called
                            subtractPosition = cachedPosition - (System.currentTimeMillis() - subtractNextPosition).toInt()
                            if (subtractPosition < 0)
                            {
                                subtractPosition = 0
                            }
                            subtractNextPosition = 0
                        }
                    }
                    onSongProgress(cachedPosition < 2000)
                    Thread.sleep(delayUpdateProgress)
                }
                catch (e:Exception) {
                    Log.w(TAG, "Crashed getting current position", e)
                    isRunning = false
                    positionCache = null
                }

            }
        }
    }

    @Synchronized  fun setNextPlayerState(playerState:PlayerState) {
        Log.i(TAG, "Next: " + this.nextPlayerState.name + " -> " + playerState.name + " (" + nextPlaying + ")")
        this.nextPlayerState = playerState
    }

    fun setSuggestedPlaylistName(name:String, id:String) {
        this.suggestedPlaylistName = name
        this.suggestedPlaylistId = id

        val editor = Util.getPreferences(this).edit()
        editor.putString(Constants.PREFERENCES_KEY_PLAYLIST_NAME, name)
        editor.putString(Constants.PREFERENCES_KEY_PLAYLIST_ID, id)
        editor.apply()
    }

    fun setRemoteEnabled(newState:RemoteControlState) {
        if (instance != null)
        {
            setRemoteEnabled(newState, null)
        }
    }
    fun setRemoteEnabled(newState:RemoteControlState, ref:Any?) {
        setRemoteState(newState, ref)

        val info = mediaRouter!!.selectedRoute
        val routeId = info.id

        val editor = Util.getPreferences(this).edit()
        editor.putInt(Constants.PREFERENCES_KEY_CONTROL_MODE, newState.value)
        editor.putString(Constants.PREFERENCES_KEY_CONTROL_ID, routeId)
        editor.apply()
    }
    private fun setRemoteState(newState:RemoteControlState, ref:Any?, routeId:String? = null) {
        // Don't try to do anything if already in the correct state
        if (remoteState === newState)
        {
            return
        }

        val isPlaying = playerState === STARTED
        val position = playerPosition

        if (remoteController != null)
        {
            remoteController!!.stop()
            setPlayerState(PlayerState.IDLE)
            remoteController!!.shutdown()
            remoteController = null

            if (newState === LOCAL)
            {
                mediaRouter!!.setDefaultRoute()
            }
        }

        Log.i(TAG, remoteState.name + " => " + newState.name + " (" + currentPlaying + ")")
        remoteState = newState
        when (newState) {
            RemoteControlState.JUKEBOX_SERVER -> remoteController = JukeboxController(this, handler)
            CHROMECAST, RemoteControlState.DLNA -> {
                Log.w("CAST", "ref: " + ref.toString())
                if (ref == null)
                {
                    remoteState = LOCAL
                }
                remoteController = ref as RemoteController?
            }
            LOCAL -> if (wifiLock!!.isHeld)
            {
                wifiLock!!.release()
            }
            else -> if (wifiLock!!.isHeld)
            {
                wifiLock!!.release()
            }
        }

        if (remoteState !== LOCAL)
        {
            if (!wifiLock!!.isHeld)
            {
                wifiLock!!.acquire()
            }
        }
        else if (wifiLock!!.isHeld)
        {
            wifiLock!!.release()
        }

        if (remoteController != null)
        {
            remoteController!!.create(isPlaying, position / 1000)
        }
        else
        {
            play(currentPlayingIndex, isPlaying, position)
        }

        if (remoteState !== LOCAL)
        {
            reset()

            // Cancel current download, if necessary.
            if (currentDownloading != null)
            {
                currentDownloading!!.cancelDownload()
            }

            // Cancels current setup tasks
            if (bufferTask != null && bufferTask!!.isRunning)
            {
                bufferTask!!.cancel()
                bufferTask = null
            }
            if (nextPlayingTask != null && nextPlayingTask!!.isRunning)
            {
                nextPlayingTask!!.cancel()
                nextPlayingTask = null
            }

            if (nextPlayerState !== IDLE)
            {
                setNextPlayerState(IDLE)
            }
        }
        checkDownloads()

        if (routeId != null)
        {
            val delayedReconnect = Runnable {
                val info = mediaRouter!!.getRouteForId(routeId)
                if (info == null)
                {
                    setRemoteState(LOCAL, null)
                }
                else if (newState === RemoteControlState.CHROMECAST)
                {
                    val controller = mediaRouter!!.getRemoteController(info)
                    if (controller != null)
                    {
                        setRemoteState(RemoteControlState.CHROMECAST, controller)
                    }
                }
                mediaRouter!!.stopScan()
            }

            handler.post{
                mediaRouter!!.startScan()
                val info = mediaRouter!!.getRouteForId(routeId)
                if (info == null)
                {
                    handler.postDelayed(delayedReconnect, 2000L)
                }
                else if (newState === RemoteControlState.CHROMECAST)
                {
                    val controller = mediaRouter!!.getRemoteController(info)
                    if (controller != null)
                    {
                        setRemoteState(RemoteControlState.CHROMECAST, controller)
                    }
                }
            }
        }
    }

    fun registerRoute(router:MediaRouter) {
        if (remoteControlClient != null)
        {
            remoteControlClient!!.registerRoute(router)
        }
    }
    fun unregisterRoute(router:MediaRouter) {
        if (remoteControlClient != null)
        {
            remoteControlClient!!.unregisterRoute(router)
        }
    }

    fun updateRemoteVolume(up:Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    fun startRemoteScan() {
        mediaRouter!!.startScan()
    }

    fun stopRemoteScan() {
        mediaRouter!!.stopScan()
    }

    @Synchronized private fun bufferAndPlay(position:Int, start:Boolean) {
        if (!currentPlaying!!.isCompleteFileAvailable && !currentPlaying!!.isStream)
        {
            if (Util.isAllowedToDownload(this))
            {
                reset()

                bufferTask = BufferTask(currentPlaying!!, position, start)
                bufferTask!!.execute()
            }
            else
            {
                next(false, start)
            }
        }
        else
        {
            doPlay(currentPlaying!!, position, start)
        }
    }

    /**
     * @param downloadFile file to play
     * @param position position in file in ms
     * @param start commence playing if true
     */
    @Synchronized private fun doPlay(downloadFile:DownloadFile, position:Int, start:Boolean) {
        try
        {
            subtractPosition = 0
            mediaPlayer!!.setOnCompletionListener(null)
            mediaPlayer!!.setOnPreparedListener(null)
            mediaPlayer!!.setOnErrorListener(null)
            mediaPlayer!!.reset()
            setPlayerState(IDLE)
            try
            {
                mediaPlayer!!.audioSessionId = audioSessionId
            }
            catch (e:Throwable) {
                mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
            }

            var dataSource:String?
            var isPartial = false
            if (downloadFile.isStream)
            {
                dataSource = downloadFile.stream
                Log.i(TAG, "Data Source: " + dataSource!!)
            }
            else
            {
                downloadFile.playing = true
                val file = if (downloadFile.isCompleteFileAvailable) downloadFile.completeFile else downloadFile.partialFile
                isPartial = file == downloadFile.partialFile
                downloadFile.updateModificationDate()

                dataSource = file.absolutePath
                if (isPartial && !Util.isOffline(this))
                {
                    if (proxy == null)
                    {
                        proxy = BufferProxy(this)
                        proxy!!.start()
                    }
                    proxy!!.setBufferFile(downloadFile)
                    dataSource = proxy!!.getPrivateAddress(dataSource)
                    Log.i(TAG, "Data Source: " + dataSource!!)
                }
                else if (proxy != null)
                {
                    proxy!!.stop()
                    proxy = null
                }
            }

            mediaPlayer!!.setDataSource(dataSource)
            setPlayerState(PREPARING)

            mediaPlayer!!.setOnBufferingUpdateListener{ _, percent ->
                Log.i(TAG, "Buffered $percent%")
                if (percent == 100)
                {
                    mediaPlayer!!.setOnBufferingUpdateListener(null)
                }
            }

            mediaPlayer!!.setOnPreparedListener{ mediaPlayer ->
                try
                {
                    setPlayerState(PREPARED)

                    synchronized (this@DownloadService) {
                        if (position != 0)
                        {
                            Log.i(TAG, "Restarting player from position $position")
                            mediaPlayer.seekTo(position)
                        }
                        cachedPosition = position

                        applyReplayGain(mediaPlayer, downloadFile)

                        if (start || autoPlayStart)
                        {
                            mediaPlayer.start()
                            applyPlaybackParamsMain()
                            setPlayerState(STARTED)

                            // Disable autoPlayStart after done
                            autoPlayStart = false
                        }
                        else
                        {
                            setPlayerState(PAUSED)
                            onSongProgress()
                        }

                        updateRemotePlaylist()
                    }

                    // Only call when starting, setPlayerState(PAUSED) already calls this
                    if (start)
                    {
                        lifecycleSupport.serializeDownloadQueue()
                    }
                }
                catch (x:Exception) {
                    handleError(x)
                }
            }

            setupHandlers(downloadFile, isPartial, start)

            mediaPlayer!!.prepareAsync()
        }
        catch (x:Exception) {
            handleError(x)
        }

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Synchronized private fun setupNext(downloadFile:DownloadFile?) {
        try
        {
            val file = if (downloadFile!!.isCompleteFileAvailable) downloadFile.completeFile else downloadFile.partialFile
            resetNext()

            // Exit when using remote controllers
            if (remoteState !== LOCAL)
            {
                return
            }

            nextMediaPlayer = MediaPlayer()
            nextMediaPlayer!!.setWakeMode(this@DownloadService, PowerManager.PARTIAL_WAKE_LOCK)
            try
            {
                nextMediaPlayer!!.audioSessionId = audioSessionId
            }
            catch (e:Throwable) {
                nextMediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
            }

            nextMediaPlayer!!.setDataSource(file.path)
            setNextPlayerState(PREPARING)

            nextMediaPlayer!!.setOnPreparedListener(MediaPlayer.OnPreparedListener { mp ->
                // Changed to different while preparing so ignore
                if (nextMediaPlayer !== mp)
                {
                    return@OnPreparedListener
                }

                try
                {
                    setNextPlayerState(PREPARED)

                    if (playerState === PlayerState.STARTED || playerState === PlayerState.PAUSED)
                    {
                        mediaPlayer!!.setNextMediaPlayer(nextMediaPlayer)
                        nextSetup = true
                    }

                    applyReplayGain(nextMediaPlayer, downloadFile)
                }
                catch (x:Exception) {
                    handleErrorNext(x)
                }
            })

            nextMediaPlayer!!.setOnErrorListener{ _, what, extra ->
                Log.w(TAG, "Error on playing next ($what, $extra): $downloadFile")
                true
            }

            nextMediaPlayer!!.prepareAsync()
        }
        catch (x:Exception) {
            handleErrorNext(x)
        }

    }

    private fun setupHandlers(downloadFile:DownloadFile, isPartial:Boolean, isPlaying:Boolean) {
        val duration = if (downloadFile.song.duration == null) 0 else downloadFile.song.duration!! * 1000
        mediaPlayer!!.setOnErrorListener{ _, what, extra ->
            Log.w(TAG, "Error on playing file ($what, $extra): $downloadFile")
            val pos = playerPosition
            reset()
            if (!isPartial || downloadFile.isWorkDone && Math.abs(duration - pos) < 10000)
            {
                playNext()
            }
            else
            {
                downloadFile.playing = false
                doPlay(downloadFile, pos, isPlaying)
                downloadFile.playing = true
            }
            true
        }

        mediaPlayer!!.setOnCompletionListener{
            setPlayerStateCompleted()

            val pos = playerPosition
            Log.i(TAG, "Ending position $pos of $duration")
            if (!isPartial || downloadFile.isWorkDone && Math.abs(duration - pos) < 10000 || nextSetup)
            {
                playNext()
                postPlayCleanup(downloadFile)
            }
            else
            {
                // If file is not completely downloaded, restart the playback from the current position.
                synchronized (this@DownloadService) {
                    if (downloadFile.isWorkDone)
                    {
                        // Complete was called early even though file is fully buffered
                        Log.i(TAG, "Requesting restart from $pos of $duration")
                        reset()
                        downloadFile.playing = false
                        doPlay(downloadFile, pos, true)
                        downloadFile.playing = true
                    }
                    else
                    {
                        Log.i(TAG, "Requesting restart from $pos of $duration")
                        reset()
                        bufferTask = BufferTask(downloadFile, pos, true)
                        bufferTask!!.execute()
                    }
                }
                checkDownloads()
            }
        }
    }

    fun setSleepTimerDuration(duration:Int) {
        timerDuration = duration
    }

    fun startSleepTimer() {
        if (sleepTimer != null)
        {
            sleepTimer!!.cancel()
            sleepTimer!!.purge()
        }

        sleepTimer = Timer()

        sleepTimer!!.schedule(object:TimerTask() {
            override fun run() {
                pause()
                sleepTimer!!.cancel()
                sleepTimer!!.purge()
                sleepTimer = null
            }

        }, (timerDuration * 60 * 1000).toLong())
        timerStart = System.currentTimeMillis()
    }

    fun stopSleepTimer() {
        if (sleepTimer != null)
        {
            sleepTimer!!.cancel()
            sleepTimer!!.purge()
        }
        sleepTimer = null
    }

    fun getSleepTimer():Boolean {
        return sleepTimer != null
    }

    fun setVolume(volume:Float) {
        if (mediaPlayer != null && (playerState === STARTED || playerState === PAUSED || playerState === STOPPED))
        {
            try
            {
                this.volume = volume
                reapplyVolume()
            }
            catch (e:Exception) {
                Log.w(TAG, "Failed to set volume")
            }

        }
    }

    private fun reapplyVolume() {
        applyReplayGain(mediaPlayer, currentPlaying)
    }

    @Synchronized  fun swap(mainList:Boolean, from:Int, swapTo:Int) {
        var to = swapTo
        val list = if (mainList) downloadList else backgroundDownloadList
        val max = list.size
        if (to >= max)
        {
            to = max - 1
        }
        else if (to < 0)
        {
            to = 0
        }

        val movedSong = list.removeAt(from)
        list.add(to, movedSong)
        currentPlayingIndex = downloadList.indexOf(currentPlaying)
        if (mainList)
        {
            if (remoteState === LOCAL || remoteController != null && remoteController!!.isNextSupported)
            {
                // Moving next playing, current playing, or moving a song to be next playing
                if (movedSong === nextPlaying || movedSong === currentPlaying || currentPlayingIndex + 1 == to)
                {
                    setNextPlaying()
                }
            }
            else
            {
                updateRemotePlaylist()
            }
        }
    }

    @Synchronized  fun serializeQueue() {
        serializeQueue(true)
    }
    @Synchronized  fun serializeQueue(serializeRemote:Boolean) {
        if (playerState === PlayerState.PAUSED)
        {
            lifecycleSupport.serializeDownloadQueue(serializeRemote)
        }
    }

    private fun handleError(x:Exception) {
        Log.w(TAG, "Media player error: $x", x)
        if (mediaPlayer != null)
        {
            try
            {
                mediaPlayer!!.reset()
            }
            catch (e:Exception) {
                Log.e(TAG, "Failed to reset player in error handler")
            }

        }
        setPlayerState(IDLE)
    }
    private fun handleErrorNext(x:Exception) {
        Log.w(TAG, "Next Media player error: $x", x)
        try
        {
            nextMediaPlayer!!.reset()
        }
        catch (e:Exception) {
            Log.e(TAG, "Failed to reset next media player", x)
        }

        setNextPlayerState(IDLE)
    }

    @Synchronized  fun checkDownloads() {
        if (!Util.isExternalStoragePresent() || !lifecycleSupport.isExternalStorageAvailable)
        {
            return
        }

        if (removePlayed)
        {
            checkRemovePlayed()
        }
        if (shufflePlay)
        {
            checkShufflePlay()
        }
        if (isArtistRadio)
        {
            checkArtistRadio()
        }

        if (!Util.isAllowedToDownload(this))
        {
            return
        }

        if (downloadList.isEmpty() && backgroundDownloadList.isEmpty())
        {
            return
        }
        if (currentPlaying != null && currentPlaying!!.isStream)
        {
            return
        }

        // Need to download current playing and not casting?
        if (currentPlaying != null && remoteState === LOCAL && currentPlaying !== currentDownloading && !currentPlaying!!.isWorkDone)
        {
            // Cancel current download, if necessary.
            if (currentDownloading != null)
            {
                currentDownloading!!.cancelDownload()
            }

            currentDownloading = currentPlaying
            currentDownloading!!.download()
            cleanupCandidates.add(currentDownloading!!)
        }
        else if (currentDownloading == null || currentDownloading!!.isWorkDone || currentDownloading!!.isFailed && (!downloadList.isEmpty() && remoteState === LOCAL || !backgroundDownloadList.isEmpty()))
        {
            currentDownloading = null
            val n = size()

            var preloaded = 0

            if (n != 0 && (remoteState === LOCAL || Util.shouldCacheDuringCasting(this)))
            {
                var start = if (currentPlaying == null) 0 else currentPlayingIndex
                if (start == -1)
                {
                    start = 0
                }
                var i = start
                do
                {
                    val downloadFile = downloadList[i]
                    if (!downloadFile.isWorkDone && !downloadFile.isFailedMax)
                    {
                        if (downloadFile.shouldSave() || preloaded < Util.getPreloadCount(this))
                        {
                            currentDownloading = downloadFile
                            currentDownloading!!.download()
                            cleanupCandidates.add(currentDownloading!!)
                            if (i == start + 1)
                            {
                                setNextPlayerState(DOWNLOADING)
                            }
                            break
                        }
                    }
                    else if (currentPlaying !== downloadFile)
                    {
                        preloaded++
                    }

                    i = (i + 1) % n
                }
                while (i != start)
            }

            if ((preloaded + 1 == n || preloaded >= Util.getPreloadCount(this) || downloadList.isEmpty() || remoteState !== LOCAL) && !backgroundDownloadList.isEmpty())
            {
                var i = 0
                while (i < backgroundDownloadList.size)
                {
                    val downloadFile = backgroundDownloadList[i]
                    if (downloadFile.isWorkDone && (!downloadFile.shouldSave() || downloadFile.isSaved) || downloadFile.isFailedMax)
                    {
                        // Don't need to keep list like active song list
                        backgroundDownloadList.removeAt(i)
                        downloadListUpdateRevision++
                        i--
                    }
                    else
                    {
                        currentDownloading = downloadFile
                        currentDownloading!!.download()
                        cleanupCandidates.add(currentDownloading!!)
                        break
                    }
                    i++
                }
            }
        }// Find a suitable target for download.

        if (!backgroundDownloadList.isEmpty())
        {
            Notifications.showDownloadingNotification(this, this, handler, currentDownloading, backgroundDownloadList.size)
            downloadOngoing = true
        }
        else if (backgroundDownloadList.isEmpty() && downloadOngoing)
        {
            Notifications.hideDownloadingNotification(this, this, handler)
            downloadOngoing = false
        }

        // Delete obsolete .partial and .complete files.
        cleanup()
    }

    @Synchronized private fun checkRemovePlayed() {
        var changed = false
        val prefs = Util.getPreferences(this)
        val keepCount = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_KEEP_PLAYED_CNT, "0")!!)
        while (currentPlayingIndex > keepCount)
        {
            downloadList.removeAt(0)
            currentPlayingIndex = downloadList.indexOf(currentPlaying)
            changed = true
        }

        if (changed)
        {
            downloadListUpdateRevision++
            onSongsChanged()
        }
    }

    @Synchronized private fun checkShufflePlay() {

        // Get users desired random playlist size
        val prefs = Util.getPreferences(this)
        val listSize = Math.max(1, Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_RANDOM_SIZE, "20")!!))
        val wasEmpty = downloadList.isEmpty()

        val revisionBefore = downloadListUpdateRevision

        // First, ensure that list is at least 20 songs long.
        val size = size()
        if (size < listSize)
        {
            for (song in shufflePlayBuffer!!.get(listSize - size))
            {
                val downloadFile = DownloadFile(this, song, false)
                downloadList.add(downloadFile)
                downloadListUpdateRevision++
            }
        }

        val currIndex = if (currentPlaying == null) 0 else currentPlayingIndex

        // Only shift playlist if playing song #5 or later.
        if (currIndex > 4)
        {
            val songsToShift = currIndex - 2
            for (song in shufflePlayBuffer!!.get(songsToShift))
            {
                downloadList.add(DownloadFile(this, song, false))
                downloadList[0].cancelDownload()
                downloadList.removeAt(0)
                downloadListUpdateRevision++
            }
        }
        currentPlayingIndex = downloadList.indexOf(currentPlaying)

        if (revisionBefore != downloadListUpdateRevision)
        {
            onSongsChanged()
            updateRemotePlaylist()
        }

        if (wasEmpty && !downloadList.isEmpty())
        {
            play(0)
        }
    }

    @Synchronized private fun checkArtistRadio() {
        // Get users desired random playlist size
        val prefs = Util.getPreferences(this)
        val listSize = Math.max(1, Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_RANDOM_SIZE, "20")!!))
        val wasEmpty = downloadList.isEmpty()

        val revisionBefore = downloadListUpdateRevision

        // First, ensure that list is at least 20 songs long.
        val size = size()
        if (size < listSize)
        {
            for (song in artistRadioBuffer!!.get(listSize - size))
            {
                val downloadFile = DownloadFile(this, song, false)
                downloadList.add(downloadFile)
                downloadListUpdateRevision++
            }
        }

        val currIndex = if (currentPlaying == null) 0 else currentPlayingIndex

        // Only shift playlist if playing song #5 or later.
        if (currIndex > 4)
        {
            val songsToShift = currIndex - 2
            for (song in artistRadioBuffer!!.get(songsToShift))
            {
                downloadList.add(DownloadFile(this, song, false))
                downloadList[0].cancelDownload()
                downloadList.removeAt(0)
                downloadListUpdateRevision++
            }
        }
        currentPlayingIndex = downloadList.indexOf(currentPlaying)

        if (revisionBefore != downloadListUpdateRevision)
        {
            onSongsChanged()
            updateRemotePlaylist()
        }

        if (wasEmpty && !downloadList.isEmpty())
        {
            play(0)
        }
    }

    @Synchronized private fun cleanup() {
        val iterator = cleanupCandidates.iterator()
        while (iterator.hasNext())
        {
            val downloadFile = iterator.next()
            if (downloadFile !== currentPlaying && downloadFile !== currentDownloading)
            {
                if (downloadFile.cleanup())
                {
                    iterator.remove()
                }
            }
        }
    }
    private fun postPlayCleanup(downloadFile:DownloadFile? = currentPlaying) {
        if (downloadFile == null)
        {
            return
        }

        // Finished loading, delete when list is cleared
        if (downloadFile.song is PodcastEpisode)
        {
            toDelete.add(downloadFile)
        }
        clearCurrentBookmark(downloadFile.song)
    }
    private fun isPastCutoff(position:Int, duration:Int, allowSkipping:Boolean = false):Boolean {
        if (currentPlaying == null)
        {
            return false
        }

        // Make cutoff a maximum of 10 minutes
        val cutoffPoint = Math.max((duration * DELETE_CUTOFF).toInt(), duration - 10 * 60 * 1000)
        var isPastCutoff = duration > 0 && position > cutoffPoint

        // Check to make sure song isn't within 10 seconds of where it was created
        val entry = currentPlaying!!.song
        if (entry?.bookmark != null)
        {
            val bookmark = entry.bookmark
            if (position < bookmark!!.position + 10000)
            {
                isPastCutoff = false
            }
        }

        // Check to make sure we aren't in a series of similar content before deleting bookmark
        if (isPastCutoff && allowSkipping)
        {
            // Check to make sure:
            // Is an audio book
            // Next playing exists and is not a wrap around or a shuffle
            // Next playing is from same context as current playing, so not at end of list
            if (entry!!.isAudioBook && nextPlaying != null && downloadList.indexOf(nextPlaying!!) != 0 && !shufflePlay
                    && entry.parent != null && entry.parent == nextPlaying!!.song.parent)
            {
                isPastCutoff = false
            }
        }

        return isPastCutoff
    }

    private fun clearCurrentBookmark() {
        // If current is null, nothing to do
        if (currentPlaying == null)
        {
            return
        }

        clearCurrentBookmark(currentPlaying!!.song)
    }
    private fun clearCurrentBookmark(entry:MusicDirectory.Entry) {
        // If no bookmark, move on
        if (entry.bookmark == null)
        {
            return
        }

        // If supposed to delete
        object:SilentBackgroundTask<Void>(this) {
            @Throws(Throwable::class)
            public override fun doInBackground():Void? {
                val musicService = MusicServiceFactory.getMusicService(this@DownloadService)
                entry.bookmark = null
                musicService.deleteBookmark(entry, this@DownloadService, null)

                val found = UpdateView.findEntry(entry)
                if (found != null)
                {
                    found.bookmark = null
                }
                return null
            }

            public override fun error(error:Throwable) {
                Log.e(TAG, "Failed to delete bookmark", error)

                val msg:String
                if (error is OfflineException || error is ServerTooOldException)
                {
                    msg = getErrorMessage(error)
                }
                else
                {
                    msg = this@DownloadService.resources.getString(R.string.bookmark_deleted_error, entry.title) + " " + getErrorMessage(error)
                }

                Util.toast(this@DownloadService, msg, false)
            }
        }.execute()
    }
    private fun checkAddBookmark(updateMetadata:Boolean = false) {
        // Don't do anything if no current playing
        if (currentPlaying == null || !ServerInfo.canBookmark())
        {
            return
        }

        val entry = currentPlaying!!.song
        val duration = playerDuration

        // If song is podcast or long go ahead and auto add a bookmark
        if (entry.isPodcast || entry.isAudioBook || duration > 10L * 60L * 1000L)
        {
            val context = this
            val position = playerPosition

            // Don't bother when at beginning
            if (position < 5000L)
            {
                return
            }

            object:SilentBackgroundTask<Void>(context) {
                @Throws(Throwable::class)
                public override fun doInBackground():Void? {
                    val musicService = MusicServiceFactory.getMusicService(context)
                    entry.bookmark = Bookmark(position)
                    musicService.createBookmark(entry, position, "Auto created by DSub", context, null)

                    val found = UpdateView.findEntry(entry)
                    if (found != null)
                    {
                        found.bookmark = Bookmark(position)
                    }
                    if (updateMetadata)
                    {
                        onMetadataUpdate(METADATA_UPDATED_BOOKMARK)
                    }

                    return null
                }

                public override fun error(error:Throwable) {
                    Log.w(TAG, "Failed to create automatic bookmark", error)

                    val msg:String = if (error is OfflineException || error is ServerTooOldException) {
                        getErrorMessage(error)
                    } else {
                        context.resources.getString(R.string.download_save_bookmark_failed) + getErrorMessage(error)
                    }

                    Util.toast(context, msg, false)
                }
            }.execute()
        }
    }

    private fun applyReplayGain(mediaPlayer:MediaPlayer?, downloadFile:DownloadFile?) {
        if (currentPlaying == null)
        {
            return
        }

        val prefs = Util.getPreferences(this)
        try
        {
            var adjust = 0f
            if (prefs.getBoolean(Constants.PREFERENCES_KEY_REPLAY_GAIN, false))
            {
                val rg = BastpUtil.getReplayGainValues(downloadFile!!.file.canonicalPath) /* track, album */
                var singleAlbum = false

                val replayGainType = prefs.getString(Constants.PREFERENCES_KEY_REPLAY_GAIN_TYPE, "1")
                // 1 => Smart replay gain
                if ("1" == replayGainType)
                {
                    // Check if part of at least <REQUIRED_ALBUM_MATCHES> consequetive songs of the same album

                    val index = downloadList.indexOf(downloadFile)
                    if (index != -1)
                    {
                        val albumName = downloadFile.song.album
                        var matched = 0

                        // Check forwards
                        run{
                            var i = index + 1
                            while (i < downloadList.size && matched < REQUIRED_ALBUM_MATCHES) {
                                if (Util.equals(albumName, downloadList[i].song.album)) {
                                    matched++
                                } else {
                                    break
                                }
                                i++
                            }
                        }

                        // Check backwards
                        var i = index - 1
                        while (i >= 0 && matched < REQUIRED_ALBUM_MATCHES)
                        {
                            if (Util.equals(albumName, downloadList[i].song.album))
                            {
                                matched++
                            }
                            else
                            {
                                break
                            }
                            i--
                        }

                        if (matched >= REQUIRED_ALBUM_MATCHES)
                        {
                            singleAlbum = true
                        }
                    }
                }
                else if ("2" == replayGainType)
                {
                    singleAlbum = true
                }// 2 => Use album tags
                // 3 => Use track tags
                // Already false, no need to do anything here


                // If playing a single album or no track gain, use album gain
                adjust = if ((singleAlbum || rg[0] == 0f) && rg[1] != 0f) {
                    rg[1]
                } else {
                    // Otherwise, give priority to track gain
                    rg[0]
                }

                if (adjust == 0f)
                {
                    /* No RG value found: decrease volume for untagged song if requested by user */
                    val untagged = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_REPLAY_GAIN_UNTAGGED, "0")!!)
                    adjust = (untagged - 150) / 10f
                }
                else
                {
                    val bump = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_REPLAY_GAIN_BUMP, "150")!!)
                    adjust += (bump - 150) / 10f
                }
            }

            var rgResult = Math.pow(10.0, (adjust / 20).toDouble()).toFloat() * volume
            if (rgResult > 1.0f)
            {
                rgResult = 1.0f /* android would IGNORE the change if this is > 1 and we would end up with the wrong volume */
            }
            else if (rgResult < 0.0f)
            {
                rgResult = 0.0f
            }
            mediaPlayer!!.setVolume(rgResult, rgResult)
        }
        catch (e:IOException) {
            Log.w(TAG, "Failed to apply replay gain values", e)
        }

    }
    private fun resetPlaybackSpeed() {
        Util.getPreferences(this).edit().remove(Constants.PREFERENCES_KEY_PLAYBACK_SPEED).apply()
        Util.getPreferences(this).edit().remove(Constants.PREFERENCES_KEY_SONG_PLAYBACK_SPEED).apply()
    }

    @Synchronized private fun applyPlaybackParamsMain() {
        applyPlaybackParams(mediaPlayer)
    }

    @Synchronized private fun isNextPlayingSameAlbum(currentPlaying:DownloadFile?, nextPlaying:DownloadFile?):Boolean {
        return if (currentPlaying == null || nextPlaying == null) {
            false
        } else {
            currentPlaying.song.album == nextPlaying.song.album
        }
    }

    @Synchronized private fun applyPlaybackParams(mediaPlayer:MediaPlayer?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            val playbackSpeed = playbackSpeed

            try
            {
                if (Math.abs(playbackSpeed - 1.0) > 0.01 || mediaPlayer?.playbackParams != null)
                {
                    val playbackParams = PlaybackParams()
                    playbackParams.speed = playbackSpeed
                    mediaPlayer!!.playbackParams = playbackParams
                }
            }
            catch (e:Exception) {
                Log.e(TAG, "Error while applying media player params", e)
            }

        }
    }

    fun toggleStarred() {
        val currentPlaying = this.currentPlaying ?: return

        UpdateHelper.toggleStarred(this, currentPlaying.song, object:UpdateHelper.OnStarChange() {
            override fun starChange(starred:Boolean) {
                if (currentPlaying === this@DownloadService.currentPlaying)
                {
                    onMetadataUpdate(METADATA_UPDATED_STAR)
                }
            }

            override fun starCommited(starred:Boolean) {

            }
        })
    }
    fun toggleRating(rating:Int) {
        if (currentPlaying == null)
        {
            return
        }

        val entry = currentPlaying!!.song
        if (entry.getRating() == rating)
        {
            setRating(0)
        }
        else
        {
            setRating(rating)
        }
    }
    private fun setRating(rating:Int) {
        val currentPlaying = this.currentPlaying ?: return
        val entry = currentPlaying.song

        // Immediately skip to the next song if down thumbed
        if (rating == 1 && size() > 1)
        {
            next(true)
        }
        else if (rating == 1 && size() == 1)
        {
            stop()
        }

        UpdateHelper.setRating(this, entry, rating, object:UpdateHelper.OnRatingChange() {
            override fun ratingChange() {
                if (currentPlaying === this@DownloadService.currentPlaying)
                {
                    onMetadataUpdate(METADATA_UPDATED_RATING)
                }
            }
        })
    }
    private fun acquireWakelock() {
        wakeLock!!.acquire(30000)
    }

    fun handleKeyEvent(keyEvent:KeyEvent) {
        lifecycleSupport.handleKeyEvent(keyEvent)
    }

    fun addOnSongChangedListener(listener:OnSongChangedListener, run:Boolean) {
        onSongChangedListeners.addIfAbsent(listener)

        if (run)
        {
            onSongsChanged()
            onSongProgress()
            onStateUpdate()
            onMetadataUpdate(METADATA_UPDATED_ALL)
        }
        else
        {
            runListenersOnInit = true
        }
    }

    fun removeOnSongChangeListener(listener:OnSongChangedListener) {
        onSongChangedListeners.remove(listener)
    }

    private fun onSongChanged() {
        val atRevision = downloadListUpdateRevision
        val shouldFastForward = shouldFastForward()
        for (listener in onSongChangedListeners)
        {
            handler.post{
                if (downloadListUpdateRevision == atRevision && instance != null)
                {
                    listener.onSongChanged(currentPlaying, currentPlayingIndex, shouldFastForward)

                    val entry = if (currentPlaying != null) currentPlaying!!.song else null
                    listener.onMetadataUpdate(entry, METADATA_UPDATED_ALL)
                }
            }
        }

        if (!onSongChangedListeners.isEmpty())
        {
            onSongProgress()
        }
    }

    private fun onSongsChanged() {
        val atRevision = downloadListUpdateRevision
        val shouldFastForward = shouldFastForward()
        for (listener in onSongChangedListeners)
        {
            handler.post{
                if (downloadListUpdateRevision == atRevision && instance != null)
                {
                    listener.onSongsChanged(downloadList, currentPlaying, currentPlayingIndex, shouldFastForward)
                }
            }
        }
    }

    private fun onSongProgress() {
        onSongProgress(true)
    }
    @Synchronized private fun onSongProgress(manual:Boolean) {
        val atRevision = downloadListUpdateRevision
        val duration = playerDuration
        val isSeekable = isSeekable
        val position = playerPosition
        val index = currentPlayingIndex
        val queueSize = size()

        for (listener in onSongChangedListeners)
        {
            handler.post{
                if (downloadListUpdateRevision == atRevision && instance != null)
                {
                    listener.onSongProgress(currentPlaying, position, duration, isSeekable)
                }
            }
        }

        if (manual)
        {
            handler.post{
                if (remoteControlClient != null)
                {
                    remoteControlClient!!.setPlaybackState(playerState.remoteControlClientPlayState, index, queueSize)
                }
            }
        }

        // Setup next playing at least a couple of seconds into the song since both Chromecast and some DLNA clients report PLAYING when still PREPARING
        if (position > 2000 && remoteController != null && remoteController!!.isNextSupported)
        {
            if (playerState === STARTED && nextPlayerState === IDLE)
            {
                setNextPlaying()
            }
        }
    }
    private fun onStateUpdate() {
        val atRevision = downloadListUpdateRevision
        for (listener in onSongChangedListeners)
        {
            handler.post{
                if (downloadListUpdateRevision == atRevision && instance != null)
                {
                    listener.onStateUpdate(playerState)
                }
            }
        }
    }

    fun onMetadataUpdate(updateType:Int) {
        for (listener in onSongChangedListeners)
        {
            handler.post{
                if (instance != null)
                {
                    val entry = if (currentPlaying != null) currentPlaying!!.song else null
                    listener.onMetadataUpdate(entry, updateType)
                }
            }
        }

        handler.post{
            if (currentPlaying != null)
            {
                remoteControlClient!!.metadataChanged(currentPlaying!!.song)
            }
        }
    }

    fun setIsForeground(b: Boolean) {
        isFrontal = b
    }

    private inner class BufferTask internal constructor(
            private val downloadFile:DownloadFile,
            private val position:Int,
            private val start:Boolean)
        :SilentBackgroundTask<Void>(instance) {
        private val expectedFileSize:Long
        private val partialFile:File = downloadFile.partialFile

        init{

            // Calculate roughly how many bytes BUFFER_LENGTH_SECONDS corresponds to.
            val bitRate = downloadFile.bitRate
            val byteCount = Math.max(100000, bitRate * 1024L / 8L * 5L)

            // Find out how large the file should grow before resuming playback.
            Log.i(TAG, "Buffering from position $position and bitrate $bitRate")
            expectedFileSize = position * bitRate / 8 + byteCount
        }

        @Throws(InterruptedException::class)
        public override fun doInBackground():Void? {
            setPlayerState(DOWNLOADING)

            while (!bufferComplete())
            {
                Thread.sleep(1000L)
                if (isCancelled || downloadFile.isFailedMax)
                {
                    return null
                }
                else if (!downloadFile.isFailedMax && !downloadFile.isDownloading)
                {
                    checkDownloads()
                }
            }
            doPlay(downloadFile, position, start)

            return null
        }

        private fun bufferComplete():Boolean {
            val completeFileAvailable = downloadFile.isWorkDone
            val size = partialFile.length()

            Log.i(TAG, "Buffering $partialFile ($size/$expectedFileSize, $completeFileAvailable)")
            return completeFileAvailable || size >= expectedFileSize
        }

        override fun toString():String {
            return "BufferTask ($downloadFile)"
        }
    }

    private inner class CheckCompletionTask internal constructor(private val downloadFile:DownloadFile?):SilentBackgroundTask<Void>(instance) {
        private val partialFile:File? = downloadFile?.partialFile

        @Throws(InterruptedException::class)
        public override fun doInBackground():Void? {
            if (downloadFile == null)
            {
                return null
            }

            // Do an initial sleep so this prepare can't compete with main prepare
            Thread.sleep(5000L)
            while (!bufferComplete())
            {
                Thread.sleep(5000L)
                if (isCancelled)
                {
                    return null
                }
            }

            // Start the setup of the next media player
            mediaPlayerHandler!!.post{
                if (!this@CheckCompletionTask.isCancelled)
                {
                    setupNext(downloadFile)
                }
            }
            return null
        }

        private fun bufferComplete():Boolean {
            val completeFileAvailable = downloadFile!!.isWorkDone
            Log.i(TAG, "Buffering next " + partialFile + " (" + partialFile!!.length() + "): " + completeFileAvailable)
            return completeFileAvailable && (playerState === PlayerState.STARTED || playerState === PlayerState.PAUSED)
        }

        override fun toString():String {
            return "CheckCompletionTask ($downloadFile)"
        }
    }

    interface OnSongChangedListener {
        fun onSongChanged(currentPlaying:DownloadFile?, currentPlayingIndex:Int, shouldFastForward:Boolean)
        fun onSongsChanged(songs:List<DownloadFile>?, currentPlaying:DownloadFile?, currentPlayingIndex:Int?, shouldFastForward:Boolean?)
        fun onSongProgress(currentPlaying:DownloadFile?, millisPlayed:Int, duration:Int?, isSeekable:Boolean)
        fun onStateUpdate(playerState:PlayerState)
        fun onMetadataUpdate(entry:MusicDirectory.Entry?, fieldChange:Int)
    }

    companion object {
        private val TAG = DownloadService::class.java.simpleName

        const val CMD_PLAY = "github.vrih.xsub.CMD_PLAY"
        const val CMD_TOGGLEPAUSE = "github.vrih.xsub.CMD_TOGGLEPAUSE"
        const val CMD_PAUSE = "github.vrih.xsub.CMD_PAUSE"
        const val CMD_STOP = "github.vrih.xsub.CMD_STOP"
        const val CMD_PREVIOUS = "github.vrih.xsub.CMD_PREVIOUS"
        const val CMD_NEXT = "github.vrih.xsub.CMD_NEXT"
        const val CANCEL_DOWNLOADS = "github.vrih.xsub.CANCEL_DOWNLOADS"
        const val START_PLAY = "github.vrih.xsub.START_PLAYING"
        private const val DEFAULT_DELAY_UPDATE_PROGRESS = 1000L
        private const val DELETE_CUTOFF = 0.84
        private const val REQUIRED_ALBUM_MATCHES = 4
        private const val REMOTE_PLAYLIST_PREV = 10
        private const val REMOTE_PLAYLIST_NEXT = 40
        private const val SHUFFLE_MODE_NONE = 0
        private const val SHUFFLE_MODE_ALL = 1
        private const val SHUFFLE_MODE_ARTIST = 2

        const val METADATA_UPDATED_ALL = 0
        private const val METADATA_UPDATED_STAR = 1
        private const val METADATA_UPDATED_RATING = 2
        const val METADATA_UPDATED_BOOKMARK = 4
        const val METADATA_UPDATED_COVER_ART = 8
        @JvmStatic
        var instance:DownloadService? = null
            private set

        @JvmStatic
        fun startService(context:Context, int:Intent?) {
            val intent = int ?: Intent(context, DownloadService::class.java)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT < 26 || powerManager.isIgnoringBatteryOptimizations(intent.getPackage()))
            {
                context.startService(intent)
            }
            else
            {
                context.startForegroundService(intent)
            }
        }
    }
}
