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

import java.io.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Sindre Mehus
 */
class Playlist : Serializable {

    var id: String? = null
    var name: String? = null
    var owner: String? = null
    var comment: String? = null
    var songCount: Int = 0
    var public: Boolean = true
    private var created: Date? = null
    var duration: Int? = null

    constructor()

    constructor(id: String, name: String) {
        this.id = id
        this.name = name
    }

    constructor(id: String, name: String, owner: String?, comment: String?, songCount: Int, pub: Boolean?, created: String, duration: Int?) {
        this.id = id
        this.name = name
        this.owner = owner ?: ""
        this.comment = comment ?: ""
        this.songCount = songCount
        this.public = pub ?: true
        setCreated(created)
        this.duration = duration
    }

    fun getCreated(): Date? {
        return created
    }

    private fun setCreated(created: String?) {
        if (created != null) {
            try {
                this.created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(created)
            } catch (e: ParseException) {
                this.created = null
            }

        } else {
            this.created = null
        }
    }

    override fun toString(): String {
        return name ?: ""
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other == null -> false
            other is String -> other == this.id
            other.javaClass != javaClass -> false
            else -> {
                val playlist = other as Playlist?
                playlist!!.id == this.id
            }
        }

    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    class PlaylistComparator : Comparator<Playlist> {
        override fun compare(playlist1: Playlist, playlist2: Playlist): Int {
            return playlist1.name!!.compareTo(playlist2.name!!, ignoreCase = true)
        }

        companion object {

            fun sort(playlists: List<Playlist>): List<Playlist> {
                Collections.sort(playlists, PlaylistComparator())
                return playlists
            }
        }
    }
}
