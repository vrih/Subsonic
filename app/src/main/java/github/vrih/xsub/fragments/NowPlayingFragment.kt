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
package github.vrih.xsub.fragments

import android.annotation.TargetApi
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.GestureDetector.OnGestureListener
import android.view.View.OnClickListener
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.cast.framework.*
import com.shehabic.droppy.DroppyClickCallbackInterface
import com.shehabic.droppy.DroppyMenuPopup
import com.shehabic.droppy.animations.DroppyFadeInAnimation
import github.vrih.xsub.R
import github.vrih.xsub.activity.SubsonicFragmentActivity
import github.vrih.xsub.adapter.DownloadFileAdapter
import github.vrih.xsub.adapter.SectionAdapter
import github.vrih.xsub.domain.*
import github.vrih.xsub.domain.MusicDirectory.Entry
import github.vrih.xsub.domain.PlayerState.*
import github.vrih.xsub.service.*
import github.vrih.xsub.service.DownloadService.OnSongChangedListener
import github.vrih.xsub.util.*
import github.vrih.xsub.view.AutoRepeatButton
import github.vrih.xsub.view.FadeOutAnimation
import github.vrih.xsub.view.UpdateView
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class NowPlayingFragment : SubsonicFragment(), OnGestureListener, SectionAdapter.OnItemClickedListener<DownloadFile>, OnSongChangedListener {

    private var songTitleTextView: TextView? = null
    private var albumArtImageView: ImageView? = null
    private var positionTextView: TextView? = null
    private var durationTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var progressBar: SeekBar? = null
    private lateinit var previousButton: AutoRepeatButton
    private lateinit var nextButton: AutoRepeatButton
    private lateinit var rewindButton: AutoRepeatButton
    private lateinit var fastforwardButton: AutoRepeatButton
    private lateinit var pauseButton: View
    private lateinit var stopButton: View
    private lateinit var startButton: View
    private lateinit var repeatButton: ImageButton
    private lateinit var starButton: ImageButton
    private lateinit var bookmarkButton: ImageButton
    private lateinit var rateBadButton: ImageButton
    private lateinit var rateGoodButton: ImageButton
    private var playbackSpeedButton: ImageButton? = null

    private var executorService: ScheduledExecutorService? = null
    private var currentPlaying: DownloadFile? = null
    private var swipeDistance: Int = 0
    private var swipeVelocity: Int = 0
    private var hideControlsFuture: ScheduledFuture<*>? = null
    private var songList: MutableList<DownloadFile>? = null
    private var songListAdapter: DownloadFileAdapter? = null
    private var seekInProgress = false
    private var scrollWhenLoaded = false
    private var lastY = 0
    private var currentPlayingSize = 0
    private var timerMenu: MenuItem? = null
    private var speed: DroppySpeedControl? = null
    private val mSessionManagerListener = SessionManagerListenerImpl()
    private var mCastSession: CastSession? = null
    private var mCastContext: CastContext? = null
    private var mSessionManager: SessionManager? = null
    private var castController: ChromeCastController? = null
    private var lastKnownRemotePositionMs: Int = 0

    /**
     * Called when the activity is first created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mCastContext = CastContext.getSharedInstance(getContext()!!)
        Log.e("CAST", mCastContext.toString())
        mCastSession = mCastContext!!.sessionManager.currentCastSession
        Log.e("CAST", mCastSession.toString())
        mSessionManager = CastContext.getSharedInstance(getContext()!!).sessionManager
        mSessionManager!!.addSessionManagerListener(mSessionManagerListener, Session::class.java)
        val downloadService = downloadService

        mCastSession?.let {
            if (it.isConnected) {
                castController = castController ?: ChromeCastController(downloadService)
                castController?.setSession(it)
                downloadService!!.setRemoteEnabled(RemoteControlState.CHROMECAST, castController)
            }
        } ?: run { downloadService?.setRemoteEnabled(RemoteControlState.LOCAL) }

        downloadService?.addOnSongChangedListener(this@NowPlayingFragment, true)
    }

    override fun onResume() {
        mCastSession = mSessionManager!!.currentCastSession
        mSessionManager!!.addSessionManagerListener(mSessionManagerListener)
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        mSessionManager!!.removeSessionManagerListener(mSessionManagerListener)
        mCastSession = null
    }

    private inner class SessionManagerListenerImpl : SessionManagerListener<Session> {
        override fun onSessionEnded(session: Session, error: Int) = onApplicationDisconnected()
        override fun onSessionResumed(session: Session, wasSuspended: Boolean) = onApplicationConnected()
        override fun onSessionResumeFailed(session: Session, error: Int) = onApplicationDisconnected()
        override fun onSessionStarted(session: Session, sessionId: String) = onApplicationConnected()
        override fun onSessionStartFailed(session: Session, error: Int) = onApplicationDisconnected()
        override fun onSessionStarting(session: Session) {}

        override fun onSessionEnding(session: Session) {
            val downloadService = downloadService
            lastKnownRemotePositionMs = downloadService!!.playerPosition
            downloadService.pause()
        }

        override fun onSessionResuming(session: Session, sessionId: String) {}
        override fun onSessionSuspended(session: Session, reason: Int) {}

        private fun onApplicationConnected() {
            val downloadService = downloadService
            castController = castController ?: ChromeCastController(downloadService)
            castController?.setSession(mSessionManager!!.currentCastSession)
            castController?.resetPlaylist()

            val position = downloadService!!.playerPosition / 1000
            downloadService.setRemoteEnabled(RemoteControlState.CHROMECAST, castController)
            Log.w("CAST", "ds cp ${downloadService.currentPlaying}")
            if (null != downloadService.currentPlaying) {
                if (downloadService.getPlayerState() === PlayerState.STARTED || downloadService.getPlayerState() === PlayerState.PREPARING) {
                    //mVideoView.pause();
                    downloadService.play(downloadService.currentPlayingIndex, true, position)
                    return
                } else {
                    downloadService.setPlayerState(PlayerState.IDLE)
                }
            }
            downloadService.setPlayerState(PlayerState.IDLE)
            //  invalidateOptionsMenu();
        }

        private fun onApplicationDisconnected() {
            // Stop remote track, switch to local renderer and hit play
            Log.w("CAST", "Application disconnected")
            val downloadService = downloadService
            downloadService!!.setRemoteEnabled(RemoteControlState.LOCAL)
            Log.w("CAST", "Application disconnected position$lastKnownRemotePositionMs")
            // invalidateOptionsMenu();
        }
    }

    private fun checkCastConnection() {
        val downloadService = downloadService

        castController = castController ?: ChromeCastController(downloadService)
        mCastSession = mCastContext!!.sessionManager.currentCastSession

        mCastSession?.let {
            if (it.isConnected) {
                castController!!.setSession(it)
                downloadService!!.setRemoteEnabled(RemoteControlState.CHROMECAST, castController)
            } else {
                downloadService!!.setRemoteEnabled(RemoteControlState.LOCAL)
            }
        } ?: run { downloadService.setRemoteEnabled(RemoteControlState.LOCAL) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, bundle: Bundle?): View? {
        rootView = inflater.inflate(R.layout.download, container, false)
        setTitle(R.string.button_bar_now_playing)

        val w = context.windowManager
        val dm = DisplayMetrics()
        w.defaultDisplay.getMetrics(dm)
        swipeDistance = (dm.widthPixels + dm.heightPixels) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100
        swipeVelocity = (dm.widthPixels + dm.heightPixels) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100

        songTitleTextView = rootView.findViewById(R.id.download_song_title)
        albumArtImageView = rootView.findViewById(R.id.download_album_art_image)
        positionTextView = rootView.findViewById(R.id.download_position)
        durationTextView = rootView.findViewById(R.id.download_duration)
        statusTextView = rootView.findViewById(R.id.download_status)
        progressBar = rootView.findViewById(R.id.download_progress_bar)
        previousButton = rootView.findViewById(R.id.download_previous)
        nextButton = rootView.findViewById(R.id.download_next)
        rewindButton = rootView.findViewById(R.id.download_rewind)
        fastforwardButton = rootView.findViewById(R.id.download_fastforward)
        pauseButton = rootView.findViewById(R.id.download_pause)
        stopButton = rootView.findViewById(R.id.download_stop)
        startButton = rootView.findViewById(R.id.download_start)
        repeatButton = rootView.findViewById(R.id.download_repeat)
        bookmarkButton = rootView.findViewById(R.id.download_bookmark)
        rateBadButton = rootView.findViewById(R.id.download_rating_bad)
        rateGoodButton = rootView.findViewById(R.id.download_rating_good)
        playbackSpeedButton = rootView.findViewById(R.id.download_playback_speed)

        starButton = rootView.findViewById(R.id.download_star)
        if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_MENU_STAR, true)) {
            starButton.setOnClickListener {
                downloadService!!.toggleStarred()
                setControlsVisible(true)
            }
        } else {
            starButton.visibility = View.GONE
        }

        val touchListener = View.OnTouchListener { _, me -> gestureScanner.onTouchEvent(me) }
        pauseButton.setOnTouchListener(touchListener)
        stopButton.setOnTouchListener(touchListener)
        startButton.setOnTouchListener(touchListener)
        bookmarkButton.setOnTouchListener(touchListener)
        rateBadButton.setOnTouchListener(touchListener)
        rateGoodButton.setOnTouchListener(touchListener)
        playbackSpeedButton!!.setOnTouchListener(touchListener)
        albumArtImageView!!.setOnTouchListener { _, me ->
            if (me.action == MotionEvent.ACTION_DOWN) {
                lastY = me.rawY.toInt()
            }
            gestureScanner.onTouchEvent(me)
        }

        previousButton.setOnClickListener {
            warnIfStorageUnavailable()
            checkCastConnection()
            downloadService!!.previous()
            setControlsVisible(true)
        }
        previousButton.setOnRepeatListener { changeProgress(true) }

        nextButton.setOnClickListener {
            warnIfStorageUnavailable()
            checkCastConnection()
            downloadService!!.next()
            setControlsVisible(true)
        }
        nextButton.setOnRepeatListener { changeProgress(false) }

        rewindButton.setOnClickListener { changeProgress(true) }
        rewindButton.setOnRepeatListener { changeProgress(true) }

        fastforwardButton.setOnClickListener { changeProgress(false) }
        fastforwardButton.setOnRepeatListener { changeProgress(false) }


        pauseButton.setOnClickListener {
            checkCastConnection()
            downloadService!!.pause()
            mCastContext!!.sessionManager.removeSessionManagerListener(
                    mSessionManagerListener, CastSession::class.java)
        }

        stopButton.setOnClickListener {
            checkCastConnection()
            downloadService!!.stop()
            downloadService!!.reset()
            mCastContext!!.sessionManager.removeSessionManagerListener(
                    mSessionManagerListener, CastSession::class.java)
        }

        startButton.setOnClickListener {
            warnIfStorageUnavailable()
            checkCastConnection()

            start()
        }

        repeatButton.setOnClickListener {
            val repeatMode = downloadService!!.repeatMode.next()
            downloadService!!.repeatMode = repeatMode
            when (repeatMode) {
                RepeatMode.OFF -> Util.toast(context, R.string.download_repeat_off)
                RepeatMode.ALL -> Util.toast(context, R.string.download_repeat_all)
                RepeatMode.SINGLE -> Util.toast(context, R.string.download_repeat_single)
            }
            updateRepeatButton()
            setControlsVisible(true)
        }

        bookmarkButton.setOnClickListener {
            createBookmark()
            setControlsVisible(true)
        }

        rateBadButton.setOnClickListener(OnClickListener {
            val downloadService = downloadService ?: return@OnClickListener
            downloadService.toggleRating(1)
            setControlsVisible(true)
        })
        rateGoodButton.setOnClickListener(OnClickListener {
            val downloadService = downloadService ?: return@OnClickListener
            downloadService.toggleRating(5)
            setControlsVisible(true)
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setPlaybackSpeed()
        } else {
            playbackSpeedButton!!.visibility = View.GONE
        }

        val overlay = rootView.findViewById<View>(R.id.download_overlay_buttons)
        val overlayHeight = overlay?.height ?: -1
        albumArtImageView!!.setOnClickListener { view ->
            if (overlayHeight == -1 || lastY < view.bottom - overlayHeight) {
                setControlsVisible(true)
            }
        }

        progressBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                downloadService!!.seekTo(progressBar!!.progress)
                seekInProgress = false
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                seekInProgress = true
            }

            override fun onProgressChanged(seekBar: SeekBar, position: Int, fromUser: Boolean) {
                if (fromUser) {
                    positionTextView!!.text = Util.formatDuration(position / 1000)
                    setControlsVisible(true)
                }
            }
        })

        return rootView
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        val downloadService = downloadService
        menuInflater.inflate(R.menu.nowplaying, menu)
        CastButtonFactory.setUpMediaRouteButton(getContext(), menu, R.id.menu_mediaroutecast)

        if (downloadService?.getSleepTimer() == true) {
            val timeRemaining = downloadService.sleepTimeRemaining
            timerMenu = menu.findItem(R.id.menu_toggle_timer)
            if (timeRemaining > 1) {
                timerMenu!!.title = context.resources.getString(R.string.download_stop_time_remaining, Util.formatDuration(timeRemaining))
            } else {
                timerMenu!!.setTitle(R.string.menu_set_timer)
            }
        }
        if (downloadService?.getKeepScreenOn() == true) {
            menu.findItem(R.id.menu_screen_on_off).isChecked = true
        }
        if (downloadService?.isRemovePlayed == true) {
            menu.findItem(R.id.menu_remove_played).isChecked = true
        }

        val equalizerAvailable = downloadService != null && downloadService.equalizerAvailable
        val isRemoteEnabled = downloadService != null && downloadService.isRemoteEnabled
        if (equalizerAvailable && !isRemoteEnabled) {
            val prefs = Util.getPreferences(context)
            val equalizerOn = prefs.getBoolean(Constants.PREFERENCES_EQUALIZER_ON, false)
            if (equalizerOn && downloadService != null) {
                if (downloadService.equalizerController != null && downloadService.equalizerController!!.isEnabled) {
                    menu.findItem(R.id.menu_equalizer).isChecked = true
                }
            }
        } else {
            menu.removeItem(R.id.menu_equalizer)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isRemoteEnabled) {
            playbackSpeedButton!!.visibility = View.GONE
        } else {
            playbackSpeedButton!!.visibility = View.VISIBLE
        }


        if (downloadService != null) {
            if (downloadService.isCurrentPlayingSingle) {
                if (!Util.isOffline(context)) {
                    menu.removeItem(R.id.menu_save_playlist)
                }

                menu.removeItem(R.id.menu_batch_mode)
                menu.removeItem(R.id.menu_remove_played)
            }
        }

        if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_BATCH_MODE, false)) {
            menu.findItem(R.id.menu_batch_mode).isChecked = true
        }
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        return if (menuItemSelected(menuItem.itemId, null)) {
            true
        } else super.onOptionsItemSelected(menuItem)

    }

    override fun onCreateContextMenu(menu: Menu, menuInflater: MenuInflater, updateView: UpdateView<DownloadFile>, downloadFile: DownloadFile) {
        if (Util.isOffline(context)) {
            menuInflater.inflate(R.menu.nowplaying_context_offline, menu)
        } else {
            menuInflater.inflate(R.menu.nowplaying_context, menu)
            menu.findItem(R.id.song_menu_star).setTitle(if (downloadFile.song.isStarred) R.string.common_unstar else R.string.common_star)
        }

        if (downloadFile.song.parent == null) {
            menu.findItem(R.id.menu_show_album).isVisible = false
            menu.findItem(R.id.menu_show_artist).isVisible = false
        }

        MenuUtil.hideMenuItems(context, menu, updateView)
    }

    override fun onContextItemSelected(menuItem: MenuItem, updateView: UpdateView<DownloadFile>, downloadFile: DownloadFile): Boolean {
        return if (onContextItemSelected(menuItem, downloadFile.song)) {
            true
        } else menuItemSelected(menuItem.itemId, downloadFile)

    }

    private fun menuItemSelected(menuItemId: Int, song: DownloadFile?): Boolean {
        val songs: MutableList<Entry>
        when (menuItemId) {
            R.id.menu_show_album, R.id.menu_show_artist -> {
                val entry = song!!.song

                val intent = Intent(context, SubsonicFragmentActivity::class.java)
                intent.putExtra(Constants.INTENT_EXTRA_VIEW_ALBUM, true)
                val albumId: String?
                val albumName: String?
                if (menuItemId == R.id.menu_show_album) {
                    albumId = if (Util.isTagBrowsing(context)) {
                        entry.albumId
                    } else {
                        entry.parent
                    }
                    albumName = entry.album
                } else {
                    if (Util.isTagBrowsing(context)) {
                        albumId = entry.artistId
                    } else {
                        albumId = entry.grandParent
                        if (albumId == null) {
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID, entry.parent)
                        }
                    }
                    albumName = entry.artist
                    intent.putExtra(Constants.INTENT_EXTRA_NAME_ARTIST, true)
                }
                intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, albumId)
                intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, albumName)
                intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, "Artist")

                if (Util.isOffline(context)) {
                    try {
                        // This should only be successful if this is a online song in offline mode
                        Integer.parseInt(entry.parent!!)
                        val root = FileUtil.getMusicDirectory(context).path
                        var id = root + "/" + entry.path
                        id = id.substring(0, id.lastIndexOf("/"))
                        if (menuItemId == R.id.menu_show_album) {
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, id)
                        }
                        id = id.substring(0, id.lastIndexOf("/"))
                        if (menuItemId != R.id.menu_show_album) {
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, id)
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, entry.artist)
                            intent.removeExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID)
                        }
                    } catch (e: Exception) {
                        // Do nothing, entry.getParent() is fine
                    }

                }

                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                Util.startActivityWithoutTransition(context, intent)
                return true
            }
            R.id.menu_lyrics -> {
                val fragment = LyricsFragment()
                val args = Bundle()
                args.putString(Constants.INTENT_EXTRA_NAME_ARTIST, song!!.song.artist)
                args.putString(Constants.INTENT_EXTRA_NAME_TITLE, song.song.title)
                fragment.arguments = args

                replaceFragment(fragment)
                return true
            }
            R.id.menu_remove_all -> {
                Util.confirmDialog(context, R.string.download_menu_remove_all, "") { _, _ ->
                    object : SilentBackgroundTask<Void>(context) {
                        override fun doInBackground(): Void? {
                            downloadService!!.isShufflePlayEnabled = false
                            downloadService!!.clear()
                            return null
                        }

                        override fun done(result: Void) {
                            context.closeNowPlaying()
                        }
                    }.execute()
                }
                return true
            }
            R.id.menu_screen_on_off -> {
                if (downloadService!!.getKeepScreenOn()) {
                    context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    downloadService!!.setKeepScreenOn(false)
                } else {
                    context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    downloadService!!.setKeepScreenOn(true)
                }
                context.invalidateOptionsMenu()
                return true
            }
            R.id.menu_remove_played -> {
                downloadService!!.isRemovePlayed = !downloadService!!.isRemovePlayed
                context.invalidateOptionsMenu()
                return true
            }
            R.id.menu_shuffle -> {
                object : SilentBackgroundTask<Void>(context) {
                    override fun doInBackground(): Void? {
                        downloadService!!.shuffle()
                        return null
                    }

                    override fun done(result: Void) {
                        Util.toast(context, R.string.download_menu_shuffle_notification)
                    }
                }.execute()
                return true
            }
            R.id.menu_save_playlist -> {
                val entries = LinkedList<Entry>()
                for (downloadFile in downloadService!!.songs) entries.add(downloadFile.song)
                createNewPlaylist(entries, true)
                return true
            }
            R.id.menu_rate -> {
                UpdateHelper.setRating(context, song!!.song)
                return true
            }
            R.id.menu_toggle_timer -> {
                if (downloadService!!.getSleepTimer()) {
                    downloadService!!.stopSleepTimer()
                    context.invalidateOptionsMenu()
                } else {
                    startTimer()
                }
                return true
            }
            R.id.menu_info -> {
                displaySongInfo(song!!.song)
                return true
            }
            R.id.menu_share -> {
                songs = ArrayList(1)
                songs.add(song!!.song)
                createShare(songs)
                return true
            }
            R.id.menu_equalizer -> {
                val downloadService = downloadService
                if (downloadService != null) {
                    val controller = downloadService.equalizerController
                    if (controller != null) {
                        val fragment = EqualizerFragment()
                        replaceFragment(fragment)
                        setControlsVisible(true)

                        return true
                    }
                }

                // Any failed condition will get here
                Util.toast(context, "Failed to start equalizer.  Try restarting.")
                return true
            }
            R.id.menu_batch_mode -> {
                if (Util.isBatchMode(context)) {
                    Util.setBatchMode(context, false)
                    songListAdapter!!.notifyDataSetChanged()
                } else {
                    Util.setBatchMode(context, true)
                    songListAdapter!!.notifyDataSetChanged()
                }
                context.invalidateOptionsMenu()

                return true
            }
            else -> return false
        }
    }

    override fun onStart() {
        super.onStart()
        onResumeHandlers()
    }

    private fun onResumeHandlers() {
        executorService = Executors.newSingleThreadScheduledExecutor()
        setControlsVisible(true)

        val downloadService = downloadService
        if (downloadService != null && downloadService.getKeepScreenOn()) {
            context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        updateButtons()

        if (currentPlaying == null && downloadService != null && null == downloadService.currentPlaying) {
            imageLoader.loadImage(albumArtImageView, null as Entry?, true, false)
        }

        context.runWhenServiceAvailable {
            if (primaryFragment) {
                val downloadService = getDownloadService()
                downloadService!!.startRemoteScan()
                downloadService.addOnSongChangedListener(this@NowPlayingFragment, true)
            }
            updateRepeatButton()
            updateTitle()
        }
    }

    override fun onStop() {
        super.onStop()
        onPauseHandlers()
    }

    private fun onPauseHandlers() {
        if (executorService != null) {
            val downloadService = downloadService
            if (downloadService != null) {
                downloadService.stopRemoteScan()
                downloadService.removeOnSongChangeListener(this)
            }
        }
    }

    override fun setPrimaryFragment(primary: Boolean) {
        super.setPrimaryFragment(primary)
        if (rootView != null) {
            if (primary) {
                onResumeHandlers()
            } else {
                onPauseHandlers()
            }
        }
    }

    public override fun setTitle(title: Int) {
        this.title = context.resources.getString(title)
        if (this.primaryFragment) {
            context.title = this.title
        }
    }

    public override fun setSubtitle(title: CharSequence?) {
        this.subtitle = title
        if (this.primaryFragment) {
            context.setSubtitle(title)
        }
    }

    override fun getCurrentAdapter(): SectionAdapter<*>? {
        return songListAdapter
    }

    private fun scheduleHideControls() {
        if (hideControlsFuture != null) {
            hideControlsFuture!!.cancel(false)
        }

        val handler = Handler()
        val runnable = Runnable { handler.post { setControlsVisible(false) } }
        hideControlsFuture = executorService!!.schedule(runnable, 3000L, TimeUnit.MILLISECONDS)
    }

    private fun setControlsVisible(visible: Boolean) {
        if (downloadService?.isCurrentPlayingSingle == true) return

        try {
            FadeOutAnimation.createAndStart(rootView.findViewById(R.id.download_overlay_buttons), !visible, 1700L)

            if (visible) scheduleHideControls()
        } catch (e: Exception) {

        }

    }

    private fun updateButtons() {
        if (context == null) return

        if (Util.isOffline(context)) {
            bookmarkButton.visibility = View.GONE
            rateBadButton.visibility = View.GONE
            rateGoodButton.visibility = View.GONE
        } else {
            if (ServerInfo.canBookmark()) {
                bookmarkButton.visibility = View.VISIBLE
            } else {
                bookmarkButton.visibility = View.GONE
            }
            rateBadButton.visibility = View.VISIBLE
            rateGoodButton.visibility = View.VISIBLE
        }
    }

    // Scroll to current playing/downloading.
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun scrollToCurrent() {
        if (downloadService == null || songListAdapter == null) {
            scrollWhenLoaded = true
            return
        }
    }

    private fun startTimer() {
        val dialogView = context.layoutInflater.inflate(R.layout.start_timer, null)

        // Setup length label
        val lengthBox = dialogView.findViewById<TextView>(R.id.timer_length_label)
        val prefs = Util.getPreferences(context)
        val lengthString = prefs.getString(Constants.PREFERENCES_KEY_SLEEP_TIMER_DURATION, "5")
        val length = Integer.parseInt(lengthString!!)
        lengthBox.text = Util.formatDuration(length)

        // Setup length slider
        val lengthBar = dialogView.findViewById<SeekBar>(R.id.timer_length_bar)
        lengthBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    lengthBox.text = Util.formatDuration(getMinutes(progress))
                    seekBar.progress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        lengthBar.progress = length - 1

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.menu_set_timer)
                .setView(dialogView)
                .setPositiveButton(R.string.common_ok) { _, _ ->
                    val editor = prefs.edit()
                    editor.putString(
                            Constants.PREFERENCES_KEY_SLEEP_TIMER_DURATION,
                            Integer.toString(getMinutes(lengthBar.progress)))
                    editor.apply()

                    downloadService!!.setSleepTimerDuration(length)
                    downloadService!!.startSleepTimer()
                    context.invalidateOptionsMenu()
                }
                .setNegativeButton(R.string.common_cancel, null)
        val dialog = builder.create()
        dialog.show()
    }

    private fun getMinutes(progress: Int): Int {
        return when {
            progress < 30 -> progress + 1
            progress < 49 -> (progress - 30) * 5 + getMinutes(29)
            progress < 57 -> (progress - 48) * 30 + getMinutes(48)
            progress < 81 -> (progress - 56) * 60 + getMinutes(56)
            else -> (progress - 80) * 150 + getMinutes(80)
        }
    }

    private fun start() {
        val service = downloadService
        val state = service!!.getPlayerState()
        if (state === PAUSED || state === COMPLETED || state === STOPPED) {
            service.start()
        } else if (state === IDLE) {
            warnIfStorageUnavailable()
            val current = service.currentPlayingIndex
            // TODO: Use play() method.
            if (current == -1) {
                service.play(0)
            } else {
                service.play(current)
            }
        }
    }

    private fun changeProgress(rewind: Boolean) {
        val downloadService = downloadService ?: return

        object : SilentBackgroundTask<Void>(context) {
            var seekTo: Int = 0

            override fun doInBackground(): Void? {
                seekTo = if (rewind) {
                    downloadService.rewind()
                } else {
                    downloadService.fastForward()
                }
                return null
            }

            override fun done(result: Void?) {
                progressBar!!.progress = seekTo
            }
        }.execute()
    }

    private fun createBookmark() {
        val downloadService = downloadService ?: return

        val currentDownload = downloadService.currentPlaying ?: return

        val dialogView = context.layoutInflater.inflate(R.layout.create_bookmark, null)
        val commentBox = dialogView.findViewById<EditText>(R.id.comment_text)

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.download_save_bookmark_title)
                .setView(dialogView)
                .setPositiveButton(R.string.common_ok) { _, _ ->
                    createBookmark(currentDownload, commentBox.text.toString())
                }
                .setNegativeButton(R.string.common_cancel, null)
        val dialog = builder.create()
        dialog.show()
    }

    private fun createBookmark(currentDownload: DownloadFile, comment: String) {
        val downloadService = downloadService ?: return

        val currentSong = currentDownload.song
        val position = downloadService.playerPosition
        val oldBookmark = currentSong.bookmark
        currentSong.bookmark = Bookmark(position)
        bookmarkButton.setImageDrawable(
                DrawableTint.getTintedDrawable(
                        context,
                        R.drawable.ic_menu_bookmark_selected))

        object : SilentBackgroundTask<Void>(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                val musicService = MusicServiceFactory.getMusicService(context)
                musicService.createBookmark(currentSong, position, comment, context, null)

                object : UpdateHelper.EntryInstanceUpdater(currentSong) {
                    override fun update(found: Entry) {
                        found.bookmark = Bookmark(position)
                    }
                }.execute()

                return null
            }

            override fun done(result: Void) {
                Util.toast(context, R.string.download_save_bookmark)
                setControlsVisible(true)
            }

            override fun error(error: Throwable) {
                Log.w(TAG, "Failed to create bookmark", error)
                currentSong.bookmark = oldBookmark

                // If no bookmark at start, then return to no bookmark
                if (oldBookmark == null) {
                    val bookmark: Int = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        R.drawable.ic_action_bookmark
                    } else {
                        DrawableTint.getDrawableRes(context, R.attr.bookmark)
                    }
                    bookmarkButton.setImageResource(bookmark)
                }

                val msg: String = if (error is OfflineException || error is ServerTooOldException) {
                    getErrorMessage(error)
                } else {
                    context.resources.getString(R.string.download_save_bookmark_failed) + getErrorMessage(error)
                }

                Util.toast(context, msg, false)
            }
        }.execute()
    }

    override fun onDown(me: MotionEvent): Boolean {
        setControlsVisible(true)
        return false
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        val downloadService = downloadService
        if (downloadService == null || e1 == null || e2 == null) {
            return false
        }

        // Right to Left swipe
        var action = 0
        if (e1.x - e2.x > swipeDistance && Math.abs(velocityX) > swipeVelocity) {
            action = ACTION_NEXT
        } else if (e2.x - e1.x > swipeDistance && Math.abs(velocityX) > swipeVelocity) {
            action = ACTION_PREVIOUS
        } else if (e2.y - e1.y > swipeDistance && Math.abs(velocityY) > swipeVelocity) {
            action = ACTION_FORWARD
        } else if (e1.y - e2.y > swipeDistance && Math.abs(velocityY) > swipeVelocity) {
            action = ACTION_REWIND
        }// Bottom to Top swipe
        // Top to Bottom swipe
        // Left to Right swipe

        if (action > 0) {
            val performAction = action
            warnIfStorageUnavailable()
            object : SilentBackgroundTask<Void>(context) {
                override fun doInBackground(): Void? {
                    when (performAction) {
                        ACTION_NEXT -> downloadService.next()
                        ACTION_PREVIOUS -> downloadService.previous()
                        ACTION_FORWARD -> downloadService.fastForward()
                        ACTION_REWIND -> downloadService.rewind()
                    }
                    return null
                }
            }.execute()

            return true
        } else {
            return false
        }
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    override fun onItemClicked(updateView: UpdateView<DownloadFile>, item: DownloadFile) {
        warnIfStorageUnavailable()
        downloadService!!.play(item)
    }

    override fun onSongChanged(currentPlaying: DownloadFile?, currentPlayingIndex: Int, shouldFastForward: Boolean) {
        this.currentPlaying = currentPlaying
        setupSubtitle(currentPlayingIndex)

        updateMediaButton()
        updateTitle()
        setPlaybackSpeed()
    }

    private fun updateMediaButton() {
        val downloadService = downloadService
        if (downloadService!!.isCurrentPlayingSingle) {
            previousButton.visibility = View.GONE
            nextButton.visibility = View.GONE
            rewindButton.visibility = View.GONE
            fastforwardButton.visibility = View.GONE
        } else {
            if (downloadService.shouldFastForward()) {
                previousButton.visibility = View.GONE
                nextButton.visibility = View.GONE

                rewindButton.visibility = View.VISIBLE
                fastforwardButton.visibility = View.VISIBLE
            } else {
                previousButton.visibility = View.VISIBLE
                nextButton.visibility = View.VISIBLE

                rewindButton.visibility = View.GONE
                fastforwardButton.visibility = View.GONE
            }
        }
    }

    private fun setupSubtitle(currentPlayingIndex: Int) {
        if (currentPlaying != null) {
            val song = currentPlaying!!.song
            songTitleTextView!!.text = song.title
            imageLoader.loadImage(albumArtImageView, song, true, true)

            val downloadService = downloadService
            when {
                downloadService!!.isCurrentPlayingSingle -> setSubtitle(null)
                downloadService.isShufflePlayEnabled -> setSubtitle(context.resources.getString(R.string.download_playerstate_playing_shuffle))
                downloadService.isArtistRadio -> setSubtitle(context.resources.getString(R.string.download_playerstate_playing_artist_radio))
                else -> setSubtitle(context.resources.getString(R.string.download_playing_out_of, currentPlayingIndex + 1, currentPlayingSize))
            }
        } else {
            songTitleTextView!!.text = null
            imageLoader.loadImage(albumArtImageView, null as Entry?, true, false)
            setSubtitle(null)
        }
    }

    override fun onSongsChanged(songs: List<DownloadFile>?, currentPlaying: DownloadFile?, currentPlayingIndex: Int?, shouldFastForward: Boolean?) {
        currentPlayingSize = songs!!.size

        val downloadService = downloadService
        if (songListAdapter == null) {
            songList = ArrayList()
            songList!!.addAll(songs)
            songListAdapter = DownloadFileAdapter(context, songList, this@NowPlayingFragment)
        } else {
            songList!!.clear()
            songList!!.addAll(songs)
            songListAdapter!!.notifyDataSetChanged()
        }

        if (scrollWhenLoaded) {
            scrollToCurrent()
            scrollWhenLoaded = false
        }

        if (this.currentPlaying !== currentPlaying) {
            onSongChanged(currentPlaying, currentPlayingIndex!!, shouldFastForward!!)
            onMetadataUpdate(currentPlaying?.song, DownloadService.METADATA_UPDATED_ALL)
        } else {
            updateMediaButton()
            setupSubtitle(currentPlayingIndex!!)
        }

        if (downloadService.isCurrentPlayingSingle) {
            repeatButton.visibility = View.GONE
        } else {
            repeatButton.visibility = View.VISIBLE
        }
        setPlaybackSpeed()
    }

    override fun onSongProgress(currentPlaying: DownloadFile?, millisPlayed: Int, duration: Int?, isSeekable: Boolean) {
        if (currentPlaying != null) {
            val millisTotal = duration ?: 0

            positionTextView!!.text = Util.formatDuration(millisPlayed / 1000)
            if (millisTotal > 0) {
                durationTextView!!.text = Util.formatDuration(millisTotal / 1000)
            } else {
                durationTextView!!.text = "-:--"
            }
            progressBar!!.max = if (millisTotal == 0) 100 else millisTotal // Work-around for apparent bug.
            if (!seekInProgress) {
                progressBar!!.progress = millisPlayed
            }
            progressBar!!.isEnabled = isSeekable
        } else {
            positionTextView!!.text = "0:00"
            durationTextView!!.text = "-:--"
            progressBar!!.progress = 0
            progressBar!!.isEnabled = false
        }

        val downloadService = downloadService
        if (downloadService != null && downloadService.getSleepTimer() && timerMenu != null) {
            val timeRemaining = downloadService.sleepTimeRemaining
            if (timeRemaining > 1) {
                timerMenu!!.title = context.resources.getString(R.string.download_stop_time_remaining, Util.formatDuration(timeRemaining))
            } else {
                timerMenu!!.setTitle(R.string.menu_set_timer)
            }
        }
    }

    override fun onStateUpdate(playerState: PlayerState) {
        when (playerState) {
            PlayerState.DOWNLOADING -> if (currentPlaying != null) {
                if (Util.isWifiRequiredForDownload(context) || Util.isLocalNetworkRequiredForDownload(context)) {
                    statusTextView!!.text = context.resources.getString(R.string.download_playerstate_mobile_disabled)
                } else {
                    val bytes = currentPlaying!!.partialFile.length()
                    statusTextView!!.text = context.resources.getString(R.string.download_playerstate_downloading, Util.formatLocalizedBytes(bytes, context))
                }
            }
            PlayerState.PREPARING -> statusTextView!!.setText(R.string.download_playerstate_buffering)
            else -> if (currentPlaying != null) {
                val entry = currentPlaying!!.song
                if (entry.album != null) {
                    var artist = ""
                    if (entry.artist != null) {
                        artist = currentPlaying!!.song.artist!! + " - "
                    }
                    statusTextView!!.text = artist + entry.album!!
                } else {
                    statusTextView!!.text = null
                }
            } else {
                statusTextView!!.text = null
            }
        }

        when (playerState) {
            PlayerState.STARTED -> {
                pauseButton.visibility = View.VISIBLE
                stopButton.visibility = View.INVISIBLE
                startButton.visibility = View.INVISIBLE
            }
            PlayerState.DOWNLOADING, PlayerState.PREPARING -> {
                pauseButton.visibility = View.INVISIBLE
                stopButton.visibility = View.VISIBLE
                startButton.visibility = View.INVISIBLE
            }
            else -> {
                pauseButton.visibility = View.INVISIBLE
                stopButton.visibility = View.INVISIBLE
                startButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onMetadataUpdate(entry: Entry?, fieldChange: Int) {
        if (entry != null && entry.isStarred) {
            starButton.setImageDrawable(DrawableTint.getTintedDrawable(context, R.drawable.ic_toggle_star))
        } else {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                starButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.star_outline))
            } else {
                starButton.setImageResource(R.drawable.ic_toggle_star_outline_dark)
            }
        }

        val badRating: Int
        val goodRating: Int
        val bookmark: Int
        if (entry?.getRating() == 1) {
            rateBadButton.setImageDrawable(DrawableTint.getTintedDrawable(context, R.drawable.ic_action_rating_bad_selected))
        } else {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                badRating = R.drawable.ic_action_rating_bad
            } else {
                badRating = DrawableTint.getDrawableRes(context, R.attr.rating_bad)
            }
            rateBadButton.setImageResource(badRating)
        }

        if (entry?.getRating() == 5) {
            rateGoodButton.setImageDrawable(DrawableTint.getTintedDrawable(context, R.drawable.ic_action_rating_good_selected))
        } else {
            goodRating = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                R.drawable.ic_action_rating_good
            } else {
                DrawableTint.getDrawableRes(context, R.attr.rating_good)
            }
            rateGoodButton.setImageResource(goodRating)
        }

        if (entry?.bookmark != null) {
            bookmarkButton.setImageDrawable(DrawableTint.getTintedDrawable(context, R.drawable.ic_menu_bookmark_selected))
        } else {
            bookmark = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                R.drawable.ic_action_bookmark
            } else {
                DrawableTint.getDrawableRes(context, R.attr.bookmark)
            }
            bookmarkButton.setImageResource(bookmark)
        }

        if (entry != null && albumArtImageView != null && fieldChange == DownloadService.METADATA_UPDATED_COVER_ART) {
            imageLoader.loadImage(albumArtImageView, entry, true, true)
        }
    }

    private fun updateRepeatButton() {
        val downloadService = downloadService
        when (downloadService!!.repeatMode) {
            RepeatMode.OFF -> repeatButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.media_button_repeat_off))
            RepeatMode.ALL -> repeatButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.media_button_repeat_all))
            RepeatMode.SINGLE -> repeatButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.media_button_repeat_single))
        }
    }

    private fun updateTitle() {
        val downloadService = downloadService
        val playbackSpeed = downloadService!!.playbackSpeed

        var title = context.resources.getString(R.string.button_bar_now_playing)
        var stringRes = -1
        when (playbackSpeed) {
            0.5f -> stringRes = R.string.download_playback_speed_half
            1.5f -> stringRes = R.string.download_playback_speed_one_half
            2.0f -> stringRes = R.string.download_playback_speed_double
            3.0f -> stringRes = R.string.download_playback_speed_tripple
        }

        var playbackSpeedText: String? = null
        if (stringRes != -1) {
            playbackSpeedText = context.resources.getString(stringRes)
        } else if (Math.abs(playbackSpeed - 1.0) > 0.01) {
            playbackSpeedText = java.lang.Float.toString(playbackSpeed) + "x"
        }

        if (playbackSpeedText != null) {
            title += " ($playbackSpeedText)"
        }
        setTitle(title)
    }

    override fun getSelectedEntries(): List<Entry> {
        val selected: List<DownloadFile> = currentAdapter!!.selected as List<DownloadFile>
        val entries = ArrayList<Entry>()

        for (downloadFile in selected) {
            if (downloadFile.getSong() != null) {
                entries.add(downloadFile.getSong())
            }
        }

        return entries
    }

    private fun setPlaybackSpeed() {
        if (playbackSpeedButton!!.visibility == View.GONE) return
        speed = DroppySpeedControl(R.layout.set_playback_speed)
        val builder = DroppyMenuPopup.Builder(context, playbackSpeedButton)
        speed!!.isClickable = true
        val playbackSpeed: Float

        playbackSpeed = if (downloadService != null) downloadService!!.playbackSpeed else 1.0f

        val popup = builder.triggerOnAnchorClick(true).addMenuItem(speed).setPopupAnimation(DroppyFadeInAnimation()).build()
        speed!!.setOnSeekBarChangeListener(context, { v, _ ->
            val playbackSpeedBar = v as SeekBar
            val playbackSpeed = playbackSpeedBar.progress + 5
            setPlaybackSpeed(playbackSpeed / 10f)
        }, R.id.playback_speed_bar, R.id.playback_speed_label, playbackSpeed)
        speed!!.setOnClicks(context,
                DroppyClickCallbackInterface { _, id ->
                    var playbackSpeed = 1.0f
                    when (id) {
                        R.id.playback_speed_one_half -> playbackSpeed = 1.5f
                        R.id.playback_speed_double -> playbackSpeed = 2.0f
                        R.id.playback_speed_triple -> playbackSpeed = 3.0f
                        else -> {
                        }
                    }
                    setPlaybackSpeed(playbackSpeed)
                    speed!!.updateSeekBar(playbackSpeed)
                    popup.dismiss(true)
                }, R.id.playback_speed_normal, R.id.playback_speed_one_half, R.id.playback_speed_double,
                R.id.playback_speed_triple)
        speed!!.updateSeekBar(playbackSpeed)

    }

    private fun setPlaybackSpeed(playbackSpeed: Float) {
        val downloadService = downloadService ?: return

        downloadService.playbackSpeed = playbackSpeed
        updateTitle()
    }

    companion object {
        private val TAG = NowPlayingFragment::class.java.simpleName
        private const val PERCENTAGE_OF_SCREEN_FOR_SWIPE = 10
        private const val ACTION_PREVIOUS = 1
        private const val ACTION_NEXT = 2
        private const val ACTION_REWIND = 3
        private const val ACTION_FORWARD = 4
    }
}
