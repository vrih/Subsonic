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

import android.util.Log
import java.io.Serializable
import java.util.*

/**
 * Represents a top level directory in which music or other media is stored.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
class MusicFolder : Serializable {
    var id: String = ""
    var name: String = ""
    var enabled: Boolean = false

    constructor()

    constructor(id: String, name: String) {
        this.id = id
        this.name = name
    }

    internal class MusicFolderComparator : Comparator<MusicFolder> {
        override fun compare(lhsMusicFolder: MusicFolder, rhsMusicFolder: MusicFolder): Int {
            return if (lhsMusicFolder === rhsMusicFolder || lhsMusicFolder.name == rhsMusicFolder.name) {
                0
            } else {
                lhsMusicFolder.name.compareTo(rhsMusicFolder.name, ignoreCase = true)
            }
        }
    }

    companion object {
        private val TAG = MusicFolder::class.java.simpleName

        fun sort(musicFolders: List<MusicFolder>) {
            try {
                Collections.sort(musicFolders, MusicFolderComparator())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sort music folders", e)
            }

        }
    }
}
