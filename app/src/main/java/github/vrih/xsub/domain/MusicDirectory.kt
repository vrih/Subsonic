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
package github.vrih.xsub.domain

import android.annotation.TargetApi
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import github.vrih.xsub.service.DownloadService
import github.vrih.xsub.util.Constants
import github.vrih.xsub.util.UpdateHelper
import github.vrih.xsub.util.Util
import java.io.*
import java.text.Collator
import java.util.*


class MusicDirectory : Serializable {

    var name: String? = null
    var id: String? = null
    var parent: String? = null
    private var children: MutableList<Entry> = ArrayList()
    val songs: List<Entry>
        @Synchronized get() {
            val result = ArrayList<Entry>()
            for (child in children) {
                if (!child.isDirectory && !child.isVideo) {
                    result.add(child)
                }
            }
            return result
        }

    val childrenSize: Int
        @Synchronized get() = children.size

    constructor()

    constructor(children: MutableList<Entry>) {
        this.children = children
    }

    fun addChild(child: Entry?) {
        if (child != null) {
            children.add(child)
        }
    }

    fun addChildren(children: List<Entry>) {
        this.children.addAll(children)
    }

    fun replaceChildren(children: MutableList<Entry>) {
        this.children = children
    }

    @Synchronized
    fun getChildren(): List<Entry>? {
        return getChildren(true, true)
    }

    @Synchronized
    fun getChildren(includeDirs: Boolean, includeFiles: Boolean): List<Entry>? {
        if (includeDirs && includeFiles) {
            return children
        }

        val result = ArrayList<Entry>(children.size)
        for (child in children) {
            if (child.isDirectory && includeDirs || !child.isDirectory && includeFiles) {
                result.add(child)
            }
        }
        return result
    }

    fun shuffleChildren() {
        Collections.shuffle(this.children)
    }

    fun sortChildren(context: Context) {
        // Only apply sorting on server version 4.7 and greater, where disc is supported
        if (ServerInfo.checkServerVersion("1.8")) {
            sortChildren(Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_CUSTOM_SORT_ENABLED, true))
        }
    }

    fun sortChildren(byYear: Boolean) {
        EntryComparator.sort(children, byYear)
    }

    @Synchronized
    fun updateMetadata(refreshedDirectory: MusicDirectory): Boolean {
        var metadataUpdated = false
        for (entry in children) {
            val index = refreshedDirectory.children.indexOf(entry)
            if (index != -1) {
                val refreshed = refreshedDirectory.children[index]

                entry.title = refreshed.title
                entry.album = refreshed.album
                entry.artist = refreshed.artist
                entry.track = refreshed.track
                entry.year = refreshed.year
                entry.genre = refreshed.genre
                entry.transcodedContentType = refreshed.transcodedContentType
                entry.transcodedSuffix = refreshed.transcodedSuffix
                entry.discNumber = refreshed.discNumber
                entry.isStarred = refreshed.isStarred
                entry.setRating(refreshed.getRating())
                entry.type = refreshed.type
                if (!Util.equals(entry.coverArt, refreshed.coverArt)) {
                    metadataUpdated = true
                    entry.coverArt = refreshed.coverArt
                }

                object : UpdateHelper.EntryInstanceUpdater(entry) {
                    override fun update(found: Entry) {
                        found.title = refreshed.title
                        found.album = refreshed.album
                        found.artist = refreshed.artist
                        found.track = refreshed.track
                        found.year = refreshed.year
                        found.genre = refreshed.genre
                        found.transcodedContentType = refreshed.transcodedContentType
                        found.transcodedSuffix = refreshed.transcodedSuffix
                        found.discNumber = refreshed.discNumber
                        found.isStarred = refreshed.isStarred
                        found.setRating(refreshed.getRating())
                        found.type = refreshed.type
                        if (!Util.equals(found.coverArt, refreshed.coverArt)) {
                            found.coverArt = refreshed.coverArt
                            metadataUpdate = DownloadService.METADATA_UPDATED_COVER_ART
                        }
                    }
                }.execute()
            }
        }

        return metadataUpdated
    }

    @Synchronized
    fun updateEntriesList(context: Context, instance: Int, refreshedDirectory: MusicDirectory): Boolean {
        var changed = false
        val it = children.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            // No longer exists in here
            if (refreshedDirectory.children.indexOf(entry) == -1) {
                it.remove()
                changed = true
            }
        }

        // Make sure we contain all children from refreshed set
        var resort = false
        for (refreshed in refreshedDirectory.children) {
            if (!this.children.contains(refreshed)) {
                this.children.add(refreshed)
                resort = true
                changed = true
            }
        }

        if (resort) {
            this.sortChildren(context)
        }

        return changed
    }

    open class Entry : Serializable {

        var id: String? = null
        var parent: String? = null
        var grandParent: String? = null
        var albumId: String? = null
        var artistId: String? = null
        var isDirectory: Boolean = false
        var title: String? = null
        var album: String? = null
        var artist: String? = null
        var track: Int? = null
        var customOrder: Int? = null
        var year: Int? = null
        var genre: String? = null
        var contentType: String? = null
        var suffix: String? = null
        var transcodedContentType: String? = null
        var transcodedSuffix: String? = null
        var coverArt: String? = null
        var size: Long? = null
        var duration: Int? = null
        var bitRate: Int? = null
        var path: String? = null
        var isVideo: Boolean = false
        var discNumber: Int? = null
        private var starred: Boolean = false
        private var rating: Int? = null
        var bookmark: Bookmark? = null
        var type = 0
        var closeness: Int = 0
        @Transient
        private var linkedArtist: Artist? = null

        val isAlbum: Boolean
            get() = parent != null || artist != null

        val albumDisplay: String
            get() = if (album != null && title!!.startsWith("Disc ")) {
                album ?: ""
            } else {
                title ?: ""
            }

        var isStarred: Boolean
            get() = starred
            set(starred) {
                this.starred = starred
                linkedArtist?.isStarred = starred
            }
        val isSong: Boolean
            get() = type == TYPE_SONG
        val isPodcast: Boolean
            get() = this is PodcastEpisode || type == TYPE_PODCAST
        val isAudioBook: Boolean
            get() = type == TYPE_AUDIO_BOOK

        constructor() {

        }

        constructor(id: String) {
            this.id = id
        }

        constructor(artist: Artist) {
            this.id = artist.id
            this.title = artist.name
            this.isDirectory = true
            this.starred = artist.isStarred
            this.rating = artist.rating
            this.linkedArtist = artist
        }

        @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
        fun loadMetadata(file: File) {
            try {
                val metadata = MediaMetadataRetriever()
                metadata.setDataSource(file.absolutePath)
                var discNumberBuf: String = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER) ?: "1/1"
                val slashIndex = discNumberBuf.indexOf("/")
                if (slashIndex > 0) {
                    discNumberBuf = discNumberBuf.substring(0, slashIndex)
                }
                try {
                    discNumber = Integer.parseInt(discNumberBuf)
                } catch (e: Exception) {
                    Log.w(TAG, "Non numbers in disc field!")
                }

                val bitrate = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                bitRate = Integer.parseInt(bitrate ?: "0") / 1000
                val length = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = Integer.parseInt(length) / 1000
                artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                metadata.release()
            } catch (e: Exception) {
                Log.i(TAG, "Device doesn't properly support MediaMetadataRetreiver", e)
            }

        }

        fun rebaseTitleOffPath() {
            try {
                var filename: String? = path ?: return

                var index = filename!!.lastIndexOf('/')
                if (index != -1) {
                    filename = filename.substring(index + 1)
                    if (track != null) {
                        filename = filename.replace(String.format("%02d ", track), "")
                    }

                    index = filename.lastIndexOf('.')
                    if (index != -1) {
                        filename = filename.substring(0, index)
                    }

                    title = filename
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update title based off of path", e)
            }

        }

        fun getRating(): Int {
            return rating ?: 0
        }

        fun setRating(rating: Int?) {
            if (rating == null || rating == 0) {
                this.rating = null
            } else {
                this.rating = rating
            }

            linkedArtist?.rating = rating ?: 0
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }

            val entry = other as Entry?
            return id == entry!!.id
        }

        override fun hashCode(): Int {
            return id!!.hashCode()
        }

        override fun toString(): String {
            return title ?: ""
        }

        @Throws(IOException::class)
        fun toByteArray(): ByteArray {
            var out: ObjectOutput
            ByteArrayOutputStream().use { bos ->
                out = ObjectOutputStream(bos)
                out.writeObject(this)
                out.flush()
                return bos.toByteArray()
            }
            // ignore close exception
        }

        companion object {
            internal const val TYPE_SONG = 0
            const val TYPE_PODCAST = 1
            const val TYPE_AUDIO_BOOK = 2

            @Throws(IOException::class, ClassNotFoundException::class)
            fun fromByteArray(byteArray: ByteArray): Entry {
                val bis = ByteArrayInputStream(byteArray)
                ObjectInputStream(bis).use { `in` -> return `in`.readObject() as Entry }
                // ignore close exception
            }
        }
    }

    internal class EntryComparator(private val byYear: Boolean) : Comparator<Entry> {
        private val collator: Collator = Collator.getInstance(Locale.US)

        init {
            this.collator.strength = Collator.PRIMARY
        }

        override fun compare(lhs: Entry, rhs: Entry): Int {
            if (lhs.isDirectory && !rhs.isDirectory) {
                return -1
            } else if (!lhs.isDirectory && rhs.isDirectory) {
                return 1
            } else if (lhs.isDirectory && rhs.isDirectory) {
                if (byYear) {
                    val lhsYear = lhs.year
                    val rhsYear = rhs.year
                    if (lhsYear != null && rhsYear != null) {
                        return lhsYear.compareTo(rhsYear)
                    } else if (lhsYear != null) {
                        return -1
                    } else if (rhsYear != null) {
                        return 1
                    }
                }

                return collator.compare(lhs.albumDisplay, rhs.albumDisplay)
            }

            val lhsDisc = lhs.discNumber
            val rhsDisc = rhs.discNumber

            if (lhsDisc != null && rhsDisc != null) {
                if (lhsDisc < rhsDisc) {
                    return -1
                } else if (lhsDisc > rhsDisc) {
                    return 1
                }
            }

            val lhsTrack = lhs.track
            val rhsTrack = rhs.track
            if (lhsTrack != null && rhsTrack != null && lhsTrack != rhsTrack) {
                return lhsTrack.compareTo(rhsTrack)
            } else if (lhsTrack != null) {
                return -1
            } else if (rhsTrack != null) {
                return 1
            }

            return collator.compare(lhs.title, rhs.title)
        }

        companion object {

            fun sort(entries: List<Entry>?, byYear: Boolean) {
                try {
                    Collections.sort(entries, EntryComparator(byYear))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sort MusicDirectory")
                }

            }
        }
    }

    companion object {
        private val TAG = MusicDirectory::class.java.simpleName
    }
}
