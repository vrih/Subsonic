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
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import github.vrih.xsub.R
import github.vrih.xsub.activity.SubsonicFragmentActivity
import github.vrih.xsub.adapter.DownloadFileAdapter
import github.vrih.xsub.adapter.SectionAdapter
import github.vrih.xsub.domain.MusicDirectory.Entry
import github.vrih.xsub.domain.PlayerState
import github.vrih.xsub.service.DownloadFile
import github.vrih.xsub.service.DownloadService
import github.vrih.xsub.service.DownloadService.OnSongChangedListener
import github.vrih.xsub.util.*
import github.vrih.xsub.view.FastScroller
import github.vrih.xsub.view.UpdateView
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class QueueFragment : SubsonicFragment(), SectionAdapter.OnItemClickedListener<DownloadFile>, OnSongChangedListener {
    private var emptyTextView: TextView? = null
    private var playlistView: RecyclerView? = null

    private var executorService: ScheduledExecutorService? = null
    private var currentPlaying: DownloadFile? = null
    private var songList: MutableList<DownloadFile>? = null
    private var songListAdapter: DownloadFileAdapter? = null
    private var scrollWhenLoaded = false
    private var currentPlayingSize = 0

    /**
     * Called when the activity is first created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val downloadService = downloadService

        downloadService?.addOnSongChangedListener(this@QueueFragment, true)

        primaryFragment = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, bundle: Bundle?): View? {
        rootView = inflater.inflate(R.layout.download_playlist, container, false)
        setTitle(R.string.button_bar_now_playing)

        val w = context.windowManager
        val dm = DisplayMetrics()
        w.defaultDisplay.getMetrics(dm)

        emptyTextView = rootView.findViewById(R.id.download_empty)
        playlistView = rootView.findViewById(R.id.download_list)
        val fastScroller = rootView.findViewById<FastScroller>(R.id.download_fast_scroller)
        fastScroller.attachRecyclerView(playlistView)
        setupLayoutManager(playlistView, false)
        val touchHelper = ItemTouchHelper(DownloadFileItemHelperCallback(this, true))
        touchHelper.attachToRecyclerView(playlistView)

        val touchListener = View.OnTouchListener { _, me -> gestureScanner.onTouchEvent(me) }
        emptyTextView!!.setOnTouchListener(touchListener)

        return rootView
    }

    override fun onMetadataUpdate(entry: Entry?, fieldChange: Int) {

    }


    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        val downloadService = downloadService
        //		if(Util.isOffline(context)) {
        //		menuInflater.inflate(R.menu.nowplaying_offline, menu);
        //} else {
        menuInflater.inflate(R.menu.nowplaying, menu)
        //	}

        if (downloadService != null) {
            if (downloadService.isCurrentPlayingSingle) {
                if (!Util.isOffline(context)) {
                    menu.removeItem(R.id.menu_save_playlist)
                }
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
            R.id.menu_remove_played -> {
                downloadService!!.isRemovePlayed = !downloadService!!.isRemovePlayed
                context.supportInvalidateOptionsMenu()
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
            else -> return false
        }
    }

    override fun onStart() {
        super.onStart()
        if (this.primaryFragment) {
            onResumeHandlers()
        }
    }

    private fun onResumeHandlers() {
        executorService = Executors.newSingleThreadScheduledExecutor()

        val downloadService = downloadService
        if (downloadService != null && downloadService.getKeepScreenOn()) {
            context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        context.runWhenServiceAvailable {
            if (primaryFragment) {
                val downloadService = getDownloadService()
                downloadService!!.startRemoteScan()
                downloadService.addOnSongChangedListener(this@QueueFragment, true)
            }
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
        context.title = this.title
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

    // Scroll to current playing/downloading.
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun scrollToCurrent() {
        if (downloadService == null || songListAdapter == null) {
            scrollWhenLoaded = true
            return
        }

        // Try to get position of current playing/downloading
        var position = songListAdapter!!.getItemPosition(currentPlaying)
        if (position == -1) {
            val currentDownloading = downloadService!!.currentDownloading
            position = songListAdapter!!.getItemPosition(currentDownloading)
        }

        // If found, scroll to it
        if (position != -1) {
            // RecyclerView.scrollToPosition just puts it on the screen (ie: bottom if scrolled below it)
            val layoutManager = playlistView!!.layoutManager as LinearLayoutManager?
            layoutManager!!.scrollToPositionWithOffset(position, 0)
        }
    }

    override fun onItemClicked(updateView: UpdateView<DownloadFile>, item: DownloadFile) {
        warnIfStorageUnavailable()
        downloadService!!.play(item)
    }

    override fun onSongChanged(currentPlaying: DownloadFile?, currentPlayingIndex: Int, shouldFastForward: Boolean) {
        this.currentPlaying = currentPlaying
        setupSubtitle(currentPlayingIndex)
        updateTitle()
    }

    private fun setupSubtitle(currentPlayingIndex: Int) {
        if (currentPlaying != null) {
            val downloadService = downloadService
            when {
                downloadService!!.isCurrentPlayingSingle -> setSubtitle(null)
                downloadService.isShufflePlayEnabled -> setSubtitle(context.resources.getString(R.string.download_playerstate_playing_shuffle))
                downloadService.isArtistRadio -> setSubtitle(context.resources.getString(R.string.download_playerstate_playing_artist_radio))
                else -> setSubtitle(context.resources.getString(R.string.download_playing_out_of, currentPlayingIndex + 1, currentPlayingSize))
            }
        } else {
            setSubtitle(null)
        }
    }

    override fun onSongsChanged(songs: List<DownloadFile>?, currentPlaying: DownloadFile?, currentPlayingIndex: Int?, shouldFastForward: Boolean?) {
        currentPlayingSize = songs!!.size

        val downloadService = downloadService
        if (downloadService!!.isShufflePlayEnabled) {
            emptyTextView!!.setText(R.string.download_shuffle_loading)
        } else {
            emptyTextView!!.setText(R.string.download_empty)
        }

        if (songListAdapter == null) {
            songList = ArrayList()
            songList!!.addAll(songs)
            songListAdapter = DownloadFileAdapter(context, songList, this@QueueFragment)
            playlistView!!.adapter = songListAdapter
        } else {
            songList!!.clear()
            songList!!.addAll(songs)
            songListAdapter!!.notifyDataSetChanged()
        }

        emptyTextView!!.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE

        if (scrollWhenLoaded) {
            scrollToCurrent()
            scrollWhenLoaded = false
        }

        if (this.currentPlaying !== currentPlaying) {
            onSongChanged(currentPlaying, currentPlayingIndex!!, shouldFastForward!!)
            onMetadataUpdate(currentPlaying?.song, DownloadService.METADATA_UPDATED_ALL)
        } else {
            setupSubtitle(currentPlayingIndex!!)
        }
    }

    override fun onSongProgress(currentPlaying: DownloadFile?, millisPlayed: Int, duration: Int?, isSeekable: Boolean) {
    }

    override fun onStateUpdate(playerState: PlayerState) {
    }

    private fun updateTitle() {
        val downloadService = downloadService
        val playbackSpeed = downloadService!!.playbackSpeed

        var title = context.resources.getString(R.string.queue)
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
            downloadFile.song?.let { entries.add(it) }
        }

        return entries
    }
}
