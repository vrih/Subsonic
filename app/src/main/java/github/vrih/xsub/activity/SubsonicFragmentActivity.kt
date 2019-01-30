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
package github.vrih.xsub.activity

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import github.vrih.xsub.R
import github.vrih.xsub.domain.MusicDirectory
import github.vrih.xsub.domain.PlayerQueue
import github.vrih.xsub.domain.PlayerState
import github.vrih.xsub.domain.ServerInfo
import github.vrih.xsub.fragments.*
import github.vrih.xsub.service.DownloadFile
import github.vrih.xsub.service.DownloadService
import github.vrih.xsub.service.MusicServiceFactory
import github.vrih.xsub.updates.Updater
import github.vrih.xsub.util.*
import java.io.File
import java.util.*

/**
 * Created by Scott on 10/14/13.
 */
class SubsonicFragmentActivity : SubsonicActivity(), DownloadService.OnSongChangedListener {

    private var nowPlayingFragment: NowPlayingFragment? = null
    private var secondaryFragment: SubsonicFragment? = null
    private var mainToolbar: Toolbar? = null

    private var lastBackPressTime: Long = 0
    private var currentPlaying: DownloadFile? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            var fragmentType: String? = intent.getStringExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE)
            var firstRun = false
            if (fragmentType == null) {
                fragmentType = Util.openToTab(this)
                if (fragmentType != null) {
                    firstRun = true
                }
            }

            if ("" == fragmentType || fragmentType == null || firstRun) {
                // Initial startup stuff
                if (!sessionInitialized) {
                    loadSession()
                }
            }
        }

        super.onCreate(savedInstanceState)
        if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_EXIT)) {
            stopService(Intent(this, DownloadService::class.java))
            finish()
            imageLoader.clearCache()
            DrawableTint.clearCache()
        } else if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD_VIEW)) {
            intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, "Download")
            lastSelectedPosition = R.id.drawer_downloading
        }
        setContentView(R.layout.abstract_fragment_activity)

        if (findViewById<View>(R.id.fragment_container) != null && savedInstanceState == null) {
            var fragmentType: String? = intent.getStringExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE)
            if (fragmentType == null) {
                fragmentType = Util.openToTab(this)
                if (fragmentType != null) {
                    intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, fragmentType)
                    lastSelectedPosition = getDrawerItemId(fragmentType)
                }

                val item = drawerList.menu.findItem(lastSelectedPosition)
                if (item != null) {
                    item.isChecked = true
                }
            } else {
                lastSelectedPosition = getDrawerItemId(fragmentType)
            }

            currentFragment = getNewFragment(fragmentType)
            if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_ID)) {
                var currentArguments = currentFragment.arguments
                if (currentArguments == null) {
                    currentArguments = Bundle()
                }
                currentArguments.putString(Constants.INTENT_EXTRA_NAME_ID, intent.getStringExtra(Constants.INTENT_EXTRA_NAME_ID))
                currentFragment.arguments = currentArguments
            }
            currentFragment.setPrimaryFragment(true)
            supportFragmentManager.beginTransaction().add(R.id.fragment_container, currentFragment, currentFragment.supportTag.toString() + "").commit()

            if (intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY) != null) {
                val fragment = SearchFragment()
                replaceFragment(fragment, fragment.supportTag)
            }

            // If a album type is set, switch to that album type view
            val albumType = intent.getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE)
            if (albumType != null) {
                val fragment = SelectDirectoryFragment()

                val args = Bundle()
                args.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, albumType)
                args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 20)
                args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0)

                fragment.arguments = args
                replaceFragment(fragment, fragment.supportTag)
            }
        }

        if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD)) {
            // Post this later so it actually runs
            handler.postDelayed({ openNowPlaying() }, 200)

            intent.removeExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD)
        }

        mainToolbar = findViewById(R.id.main_toolbar)

        setSupportActionBar(mainToolbar)
    }

    override fun onPostCreate(bundle: Bundle?) {
        super.onPostCreate(bundle)

        showInfoDialog()
        checkUpdates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (currentFragment != null && intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY) != null) {
            if (currentFragment is SearchFragment) {
                val query = intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY)
                val autoplay = intent.getBooleanExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false)
                val artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
                val album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
                val title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)

                if (query != null) {
                    (currentFragment as SearchFragment).search(query, autoplay, artist, album, title)
                }
                getIntent().removeExtra(Constants.INTENT_EXTRA_NAME_QUERY)
            } else {
                setIntent(intent)

                val fragment = SearchFragment()
                replaceFragment(fragment, fragment.supportTag)
            }
        } else if (intent.getBooleanExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, false)) {
        } else {
            setIntent(intent)
        }
        if (drawer != null) {
            drawer.closeDrawers()
        }
    }

    public override fun onResume() {
        super.onResume()

        if (intent.hasExtra(Constants.INTENT_EXTRA_VIEW_ALBUM)) {
            val fragment = SelectDirectoryFragment()
            val args = Bundle()
            args.putString(Constants.INTENT_EXTRA_NAME_ID, intent.getStringExtra(Constants.INTENT_EXTRA_NAME_ID))
            args.putString(Constants.INTENT_EXTRA_NAME_NAME, intent.getStringExtra(Constants.INTENT_EXTRA_NAME_NAME))
            args.putString(Constants.INTENT_EXTRA_SEARCH_SONG, intent.getStringExtra(Constants.INTENT_EXTRA_SEARCH_SONG))
            if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_ARTIST)) {
                args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true)
            }
            if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID)) {
                args.putString(Constants.INTENT_EXTRA_NAME_CHILD_ID, intent.getStringExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID))
            }
            fragment.arguments = args

            replaceFragment(fragment, fragment.supportTag)
            intent.removeExtra(Constants.INTENT_EXTRA_VIEW_ALBUM)
        }

        UserUtil.seedCurrentUser(this)
        createAccount()
        runWhenServiceAvailable { downloadService!!.addOnSongChangedListener(this@SubsonicFragmentActivity, true) }
    }

    public override fun onPause() {
        super.onPause()
        val downloadService = downloadService
        downloadService?.removeOnSongChangeListener(this)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        if (secondaryFragment != null) {
            savedInstanceState.putString(Constants.MAIN_NOW_PLAYING_SECONDARY, secondaryFragment!!.tag)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        val id = savedInstanceState.getString(Constants.MAIN_NOW_PLAYING)
        val fm = supportFragmentManager
        nowPlayingFragment = fm.findFragmentByTag(id) as NowPlayingFragment?

        val secondaryId = savedInstanceState.getString(Constants.MAIN_NOW_PLAYING_SECONDARY)
        if (secondaryId != null) {
            secondaryFragment = fm.findFragmentByTag(secondaryId) as SubsonicFragment?

            nowPlayingFragment!!.setPrimaryFragment(false)
            secondaryFragment!!.setPrimaryFragment(true)

            val trans = supportFragmentManager.beginTransaction()
            trans.hide(nowPlayingFragment!!)
            trans.commit()
        }

        if (drawerToggle != null && backStack.size > 0) {
            drawerToggle.isDrawerIndicatorEnabled = false
        }
    }

    override fun setContentView(viewId: Int) {
        super.setContentView(viewId)
        if (drawerToggle != null) {
            drawerToggle.isDrawerIndicatorEnabled = true
        }
    }

    override fun onBackPressed() {
        if (onBackPressedSupport()) {
            if (!Util.disableExitPrompt(this) && lastBackPressTime < System.currentTimeMillis() - 4000) {
                lastBackPressTime = System.currentTimeMillis()
                Util.toast(this, R.string.main_back_confirm)
            } else {
                finish()
            }
        }
    }

    public override fun onBackPressedSupport(): Boolean {
        return super.onBackPressedSupport()
    }

    public override fun startFragmentActivity(fragmentType: String) {
        // Create a transaction that does all of this
        val trans = supportFragmentManager.beginTransaction()

        // Clear existing stack
        for (i in backStack.indices.reversed()) {
            trans.remove(backStack[i])
        }
        trans.remove(currentFragment)
        backStack.clear()

        // Create new stack
        currentFragment = getNewFragment(fragmentType)
        currentFragment.setPrimaryFragment(true)
        trans.add(R.id.fragment_container, currentFragment, currentFragment.supportTag.toString() + "")
        // Done, cleanup
        trans.commit()
        invalidateOptionsMenu()

        recreateSpinner()
        if (drawer != null) {
            drawer.closeDrawers()
        }

        if (secondaryContainer != null) {
            secondaryContainer.visibility = View.GONE
        }
        if (drawerToggle != null) {
            drawerToggle.isDrawerIndicatorEnabled = true
        }
    }

    private fun getNewFragment(fragmentType: String?): SubsonicFragment {
        return when (fragmentType) {
            "Artist" -> SelectArtistFragment()
            "Now Playing" -> NowPlayingFragment()
            "Playlist" -> SelectPlaylistFragment()
            "Queue" -> QueueFragment()
            "Podcast" -> SelectPodcastsFragment()
            "Bookmark" -> SelectBookmarkFragment()
            "Internet Radio" -> SelectInternetRadioStationFragment()
            "Share" -> SelectShareFragment()
            "Admin" -> AdminFragment()
            "Download" -> DownloadFragment()
            else -> MainFragment()
        }
    }

    private fun checkUpdates() {
        try {
            val version = packageManager.getPackageInfo(packageName, 0).versionName
            val ver = Integer.parseInt(version.replace(".", ""))
            val updater = Updater(ver)
            updater.checkUpdates(this)
        } catch (e: Exception) {

        }

    }

    private fun loadSession() {
        loadSettings()
        if (!Util.isOffline(this) && ServerInfo.canBookmark()) {
            loadBookmarks()
        }
        // If we are on Subsonic 5.2+, save play queue
        if (ServerInfo.canSavePlayQueue(this) && !Util.isOffline(this)) {
            loadRemotePlayQueue()
        }

        sessionInitialized = true
    }

    private fun loadSettings() {
        PreferenceManager.setDefaultValues(this, R.xml.settings_appearance, false)
        PreferenceManager.setDefaultValues(this, R.xml.settings_cache, false)
        PreferenceManager.setDefaultValues(this, R.xml.settings_drawer, false)
        PreferenceManager.setDefaultValues(this, R.xml.settings_sync, false)
        PreferenceManager.setDefaultValues(this, R.xml.settings_playback, false)

        val prefs = Util.getPreferences(this)
        if (!prefs.contains(Constants.PREFERENCES_KEY_CACHE_LOCATION) || prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null) == null) {
            resetCacheLocation(prefs)
        } else {
            val path = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null)
            val cacheLocation = File(path)
            if (FileUtil.cannotWrite(cacheLocation)) {
                // Only warn user if there is a difference saved
                if (resetCacheLocation(prefs)) {
                    Util.info(this, R.string.common_warning, R.string.settings_cache_location_reset)
                }
            }
        }

        if (!prefs.contains(Constants.PREFERENCES_KEY_SERVER_COUNT)) {
            val editor = prefs.edit()
            editor.putInt(Constants.PREFERENCES_KEY_SERVER_COUNT, 1)
            editor.apply()
        }
    }

    private fun resetCacheLocation(prefs: SharedPreferences): Boolean {
        val newDirectory = FileUtil.getDefaultMusicDirectory(this).path
        val oldDirectory = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null)
        return if (newDirectory == null || newDirectory == oldDirectory) {
            false
        } else {
            val editor = prefs.edit()
            editor.putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, newDirectory)
            editor.apply()
            true
        }
    }

    private fun loadBookmarks() {
        val context = this
        object : SilentBackgroundTask<Void>(context) {
            @Throws(Throwable::class)
            public override fun doInBackground(): Void? {
                val musicService = MusicServiceFactory.getMusicService(context)
                musicService.getBookmarks(true, context, null)

                return null
            }

            public override fun error(error: Throwable) {
                Log.e(TAG, "Failed to get bookmarks", error)
            }
        }.execute()
    }

    private fun loadRemotePlayQueue() {
        if (Util.getPreferences(this).getBoolean(Constants.PREFERENCES_KEY_RESUME_PLAY_QUEUE_NEVER, false)) {
            return
        }

        val context = this
        object : SilentBackgroundTask<Void>(this) {
            private var playerQueue: PlayerQueue? = null

            override fun doInBackground(): Void? {
                try {
                    val musicService = MusicServiceFactory.getMusicService(context)
                    val remoteState = musicService.getPlayQueue(context, null)

                    // Make sure we wait until download service is ready
                    var downloadService = downloadService
                    while (downloadService == null || downloadService.isNotInitialized) {
                        Util.sleepQuietly(100L)
                        downloadService = getDownloadService()
                    }

                    // If we had a remote state and it's changed is more recent than our existing state
                    if (remoteState?.changed != null) {
                        // Check if changed + 30 seconds since some servers have slight skew
                        val remoteChange = Date(remoteState.changed!!.time - ALLOWED_SKEW)
                        val localChange = downloadService.lastStateChanged
                        if (localChange.before(remoteChange)) {
                            playerQueue = remoteState
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get playing queue to server", e)
                }

                return null
            }

            override fun done(arg: Void) {
                if (!context.isDestroyedCompat && playerQueue != null) {
                    promptRestoreFromRemoteQueue(playerQueue!!)
                }
            }
        }.execute()
    }

    private fun promptRestoreFromRemoteQueue(remoteState: PlayerQueue) {
        val builder = AlertDialog.Builder(this)
        val message = resources.getString(R.string.common_confirm_message, resources.getString(R.string.download_restore_play_queue).toLowerCase(), Util.formatDate(remoteState.changed))
        builder.setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.common_confirm)
                .setMessage(message)
                .setPositiveButton(R.string.common_ok) { _, _ ->
                    object : SilentBackgroundTask<Void>(this@SubsonicFragmentActivity) {
                        override fun doInBackground(): Void? {
                            val downloadService = downloadService
                            downloadService!!.clear()
                            downloadService.download(remoteState.songs, false, false, false, false, remoteState.currentPlayingIndex, remoteState.currentPlayingPosition)
                            return null
                        }
                    }.execute()
                }
                .setNeutralButton(R.string.common_cancel) { _, _ ->
                    object : SilentBackgroundTask<Void>(this@SubsonicFragmentActivity) {
                        override fun doInBackground(): Void? {
                            val downloadService = downloadService
                            downloadService!!.serializeQueue(false)
                            return null
                        }
                    }.execute()
                }
                .setNegativeButton(R.string.common_never) { _, _ ->
                    object : SilentBackgroundTask<Void>(this@SubsonicFragmentActivity) {
                        override fun doInBackground(): Void? {
                            val downloadService = downloadService
                            downloadService!!.serializeQueue(false)

                            val editor = Util.getPreferences(this@SubsonicFragmentActivity).edit()
                            editor.putBoolean(Constants.PREFERENCES_KEY_RESUME_PLAY_QUEUE_NEVER, true)
                            editor.apply()
                            return null
                        }
                    }.execute()
                }

        builder.create().show()
    }

    private fun createAccount() {
        val context = this

        object : SilentBackgroundTask<Void>(this) {
            override fun doInBackground(): Void? {
                val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
                val account = Account(Constants.SYNC_ACCOUNT_NAME, Constants.SYNC_ACCOUNT_TYPE)
                accountManager.addAccountExplicitly(account, null, null)

                val prefs = Util.getPreferences(context)
                val syncEnabled = prefs.getBoolean(Constants.PREFERENCES_KEY_SYNC_ENABLED, true)
                val syncInterval = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_SYNC_INTERVAL, "60")!!)

                // Add enabled/frequency to playlist/podcasts syncing
                ContentResolver.setSyncAutomatically(account, Constants.SYNC_ACCOUNT_PLAYLIST_AUTHORITY, syncEnabled)
                ContentResolver.addPeriodicSync(account, Constants.SYNC_ACCOUNT_PLAYLIST_AUTHORITY, Bundle(), 60L * syncInterval)
                ContentResolver.setSyncAutomatically(account, Constants.SYNC_ACCOUNT_PODCAST_AUTHORITY, syncEnabled)
                ContentResolver.addPeriodicSync(account, Constants.SYNC_ACCOUNT_PODCAST_AUTHORITY, Bundle(), 60L * syncInterval)

                // Add for starred/recently added
                ContentResolver.setSyncAutomatically(account, Constants.SYNC_ACCOUNT_STARRED_AUTHORITY, syncEnabled && prefs.getBoolean(Constants.PREFERENCES_KEY_SYNC_STARRED, false))
                ContentResolver.addPeriodicSync(account, Constants.SYNC_ACCOUNT_STARRED_AUTHORITY, Bundle(), 60L * syncInterval)
                ContentResolver.setSyncAutomatically(account, Constants.SYNC_ACCOUNT_MOST_RECENT_AUTHORITY, syncEnabled && prefs.getBoolean(Constants.PREFERENCES_KEY_SYNC_MOST_RECENT, false))
                ContentResolver.addPeriodicSync(account, Constants.SYNC_ACCOUNT_MOST_RECENT_AUTHORITY, Bundle(), 60L * syncInterval)
                return null
            }

            override fun done(result: Void?) {

            }
        }.execute()
    }

    private fun showInfoDialog() {
        if (!infoDialogDisplayed) {
            infoDialogDisplayed = true
            if (Util.getRestUrl(this, null).contains("demo.subsonic.org")) {
                Util.info(this, R.string.main_welcome_title, R.string.main_welcome_text)
            }
        }
    }

    override fun onSongChanged(currentPlaying: DownloadFile?, currentPlayingIndex: Int, shouldFastForward: Boolean) {
        this.currentPlaying = currentPlaying
    }

    override fun onSongsChanged(songs: List<DownloadFile>?, currentPlaying: DownloadFile?, currentPlayingIndex: Int?, shouldFastForward: Boolean?) {
        if (this.currentPlaying !== currentPlaying || this.currentPlaying == null) {
            onSongChanged(currentPlaying, currentPlayingIndex!!, shouldFastForward!!)
        }
    }

    override fun onSongProgress(currentPlaying: DownloadFile?, millisPlayed: Int, duration: Int?, isSeekable: Boolean) {

    }

    override fun onStateUpdate(playerState: PlayerState) {
        if (playerState === PlayerState.STARTED) R.drawable.media_pause else R.drawable.media_start
    }

    override fun onMetadataUpdate(entry: MusicDirectory.Entry?, fieldChange: Int) {}

    companion object {
        private val TAG = SubsonicFragmentActivity::class.java.simpleName
        private var infoDialogDisplayed: Boolean = false
        private var sessionInitialized = false
        private const val ALLOWED_SKEW = 30000L
    }
}
