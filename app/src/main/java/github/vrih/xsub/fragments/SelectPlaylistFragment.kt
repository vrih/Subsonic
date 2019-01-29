package github.vrih.xsub.fragments

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import github.vrih.xsub.R
import github.vrih.xsub.adapter.PlaylistAdapter
import github.vrih.xsub.adapter.SectionAdapter
import github.vrih.xsub.domain.Playlist
import github.vrih.xsub.domain.ServerInfo
import github.vrih.xsub.service.*
import github.vrih.xsub.util.*
import github.vrih.xsub.view.UpdateView
import java.util.*

class SelectPlaylistFragment : SelectRecyclerFragment<Playlist>() {

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_LARGE_ALBUM_ART, true)) {
            largeAlbums = true
        }
    }

    override fun onCreateContextMenu(menu: Menu, menuInflater: MenuInflater, updateView: UpdateView<Playlist>, playlist: Playlist) {
        if (Util.isOffline(context)) {
            menuInflater.inflate(R.menu.select_playlist_context_offline, menu)
        } else {
            menuInflater.inflate(R.menu.select_playlist_context, menu)

            if (SyncUtil.isSyncedPlaylist(context, playlist.id)) {
                menu.removeItem(R.id.playlist_menu_sync)
            } else {
                menu.removeItem(R.id.playlist_menu_stop_sync)
            }

            if (!ServerInfo.checkServerVersion("1.8")) {
                menu.removeItem(R.id.playlist_update_info)
            } else if (playlist.public && !playlist.id!!.contains(".m3u") && UserUtil.getCurrentUsername(context) != playlist.owner) {
                menu.removeItem(R.id.playlist_update_info)
                menu.removeItem(R.id.playlist_menu_delete)
            }
        }

        recreateContextMenu(menu)
    }

    override fun onContextItemSelected(menuItem: MenuItem, updateView: UpdateView<Playlist>, playlist: Playlist): Boolean {
        val fragment: SubsonicFragment
        val args: Bundle

        when (menuItem.itemId) {
            R.id.playlist_menu_download -> downloadPlaylist(playlist.id, playlist.name, false)
            R.id.playlist_menu_sync -> syncPlaylist(playlist)
            R.id.playlist_menu_stop_sync -> stopSyncPlaylist(playlist)
            R.id.playlist_menu_play_now -> {
                fragment = SelectDirectoryFragment()
                args = Bundle()
                args.putString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID, playlist.id)
                args.putString(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME, playlist.name)
                args.putBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, true)
                fragment.setArguments(args)

                replaceFragment(fragment)
            }
            R.id.playlist_menu_play_shuffled -> {
                fragment = SelectDirectoryFragment()
                args = Bundle()
                args.putString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID, playlist.id)
                args.putString(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME, playlist.name)
                args.putBoolean(Constants.INTENT_EXTRA_NAME_SHUFFLE, true)
                args.putBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, true)
                fragment.setArguments(args)

                replaceFragment(fragment)
            }
            R.id.playlist_menu_delete -> deletePlaylist(playlist)
            R.id.playlist_info -> displayPlaylistInfo(playlist)
            R.id.playlist_update_info -> updatePlaylistInfo(playlist)
        }

        return false
    }

    public override fun getOptionsMenu(): Int {
        return R.menu.abstract_top_menu
    }

    public override fun getAdapter(playlists: List<Playlist>): SectionAdapter<Playlist> {
        val mine = ArrayList<Playlist>()
        val shared = ArrayList<Playlist>()

        val currentUsername = UserUtil.getCurrentUsername(context)
        for (playlist in playlists) {
            if (playlist.owner == null || playlist.owner == currentUsername) {
                mine.add(playlist)
            } else {
                shared.add(playlist)
            }
        }

        return if (shared.isEmpty()) {
            PlaylistAdapter(context, playlists, imageLoader, largeAlbums, this)
        } else {
            val res = context.resources
            val headers = Arrays.asList(res.getString(R.string.playlist_mine), res.getString(R.string.playlist_shared))

            val sections = ArrayList<List<Playlist>>()
            sections.add(mine)
            sections.add(shared)

            PlaylistAdapter(context, headers, sections, imageLoader, largeAlbums, this)
        }
    }

    @Throws(Exception::class)
    public override fun getObjects(musicService: MusicService, refresh: Boolean, listener: ProgressListener): List<Playlist> {
        val playlists = musicService.getPlaylists(refresh, context, listener)
        if (!Util.isOffline(context) && refresh) {
            CacheCleaner(context, downloadService).cleanPlaylists(playlists)
        }
        return playlists
    }

    public override fun getTitleResource(): Int {
        return R.string.playlist_label
    }

    override fun onItemClicked(updateView: UpdateView<Playlist>?, playlist: Playlist) {
        val fragment = SelectDirectoryFragment()
        val args = Bundle()
        args.putString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID, playlist.id)
        args.putString(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME, playlist.name)
        if (ServerInfo.checkServerVersion("1.8") && (playlist.owner != null && playlist.owner == UserUtil.getCurrentUsername(context) || playlist.id!!.contains(".m3u"))) {
            args.putBoolean(Constants.INTENT_EXTRA_NAME_PLAYLIST_OWNER, true)
        }
        fragment.arguments = args

        replaceFragment(fragment)
    }

    public override fun onFinishRefresh() {
        val args = arguments
        if (args != null) {
            val playlistId = args.getString(Constants.INTENT_EXTRA_NAME_ID, null)
            if (playlistId != null && objects != null) {
                for (playlist in objects) {
                    if (playlistId == playlist.id) {
                        onItemClicked(null, playlist)
                        break
                    }
                }
            }
        }
    }

    private fun deletePlaylist(playlist: Playlist) {
        Util.confirmDialog(context, R.string.common_delete, playlist.name) { _, _ ->
            object : LoadingTask<Void>(context, false) {
                @Throws(Throwable::class)
                override fun doInBackground(): Void? {
                    val musicService = MusicServiceFactory.getMusicService(context)
                    musicService.deletePlaylist(playlist.id, context, null)
                    SyncUtil.removeSyncedPlaylist(context, playlist.id)
                    return null
                }

                override fun done(result: Void) {
                    adapter.removeItem(playlist)
                    Util.toast(context, context.resources.getString(R.string.menu_deleted_playlist, playlist.name))
                }

                override fun error(error: Throwable) {
                    val msg: String = if (error is OfflineException || error is ServerTooOldException) {
                        getErrorMessage(error)
                    } else {
                        context.resources.getString(R.string.menu_deleted_playlist_error, playlist.name) + " " + getErrorMessage(error)
                    }

                    Util.toast(context, msg, false)
                }
            }.execute()
        }
    }

    private fun displayPlaylistInfo(playlist: Playlist) {
        val headers = ArrayList<Int>()
        val details = ArrayList<String>()

        headers.add(R.string.details_title)
        playlist.name?.let{ details.add(it) }

        playlist.owner?.let{
            headers.add(R.string.details_owner)
            details.add(it)
        }

        playlist.comment?.let {
            headers.add(R.string.details_comments)
            details.add(it)
        }

        headers.add(R.string.details_song_count)
        details.add(Integer.toString(playlist.songCount))

        if (playlist.duration != null) {
            headers.add(R.string.details_length)
            details.add(Util.formatDuration(playlist.duration))
        }

        headers.add(R.string.details_public)
        details.add(Util.formatBoolean(context, playlist.public))

        if (playlist.getCreated() != null) {
            headers.add(R.string.details_created)
            details.add(Util.formatDate(playlist.getCreated()))
        }

        Util.showDetailsDialog(context, R.string.details_title_playlist, headers, details)
    }

    private fun updatePlaylistInfo(playlist: Playlist) {
        val dialogView = context.layoutInflater.inflate(R.layout.update_playlist, null)
        val nameBox = dialogView.findViewById<EditText>(R.id.get_playlist_name)
        val commentBox = dialogView.findViewById<EditText>(R.id.get_playlist_comment)
        val publicBox = dialogView.findViewById<CheckBox>(R.id.get_playlist_public)

        nameBox.setText(playlist.name)
        commentBox.setText(playlist.comment)
        val pub = playlist.public
        publicBox.isChecked = pub

        AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.playlist_update_info)
                .setView(dialogView)
                .setPositiveButton(R.string.common_ok) { _, _ ->
                    object : LoadingTask<Void>(context, false) {
                        @Throws(Throwable::class)
                        override fun doInBackground(): Void? {
                            val name = nameBox.text.toString()
                            val comment = commentBox.text.toString()
                            val isPublic = publicBox.isChecked

                            val musicService = MusicServiceFactory.getMusicService(context)
                            musicService.updatePlaylist(playlist.id, name, comment, isPublic, context, null)

                            playlist.name = name
                            playlist.comment = comment
                            playlist.public = isPublic

                            return null
                        }

                        override fun done(result: Void) {
                            Util.toast(context, context.resources.getString(R.string.playlist_updated_info, playlist.name))
                        }

                        override fun error(error: Throwable) {
                            val msg: String = if (error is OfflineException || error is ServerTooOldException) {
                                getErrorMessage(error)
                            } else {
                                context.resources.getString(R.string.playlist_updated_info_error, playlist.name) + " " + getErrorMessage(error)
                            }

                            Util.toast(context, msg, false)
                        }
                    }.execute()
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
    }

    private fun syncPlaylist(playlist: Playlist) {
        SyncUtil.addSyncedPlaylist(context, playlist.id)

        val syncImmediately: Boolean
        syncImmediately = if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_SYNC_WIFI, true)) {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = manager.activeNetworkInfo

            networkInfo.type == ConnectivityManager.TYPE_WIFI
        } else {
            true
        }

        if (syncImmediately) {
            downloadPlaylist(playlist.id, playlist.name, true)
        }
    }

    private fun stopSyncPlaylist(playlist: Playlist) {
        SyncUtil.removeSyncedPlaylist(context, playlist.id)

        object : LoadingTask<Void>(context, false) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                // Unpin all of the songs in playlist
                val musicService = MusicServiceFactory.getMusicService(context)
                val root = musicService.getPlaylist(true, playlist.id, playlist.name, context, this)
                for (entry in root.getChildren()!!) {
                    val file = DownloadFile(context, entry, false)
                    file.unpin()
                }

                return null
            }

            override fun done(result: Void) {

            }
        }.execute()
    }
}
