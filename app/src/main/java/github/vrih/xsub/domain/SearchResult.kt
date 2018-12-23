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

/**
 * The result of a search.  Contains matching artists, albums and songs.
 *
 * @author Sindre Mehus
 */
class SearchResult(val artists: List<Artist>, val albums: List<MusicDirectory.Entry>, val songs: List<MusicDirectory.Entry>) : Serializable {

    fun hasArtists(): Boolean {
        return !artists.isEmpty()
    }

    fun hasAlbums(): Boolean {
        return !albums.isEmpty()
    }

    fun hasSongs(): Boolean {
        return !songs.isEmpty()
    }
}
