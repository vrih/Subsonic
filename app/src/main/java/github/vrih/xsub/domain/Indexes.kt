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

import android.content.Context
import github.vrih.xsub.util.Constants
import github.vrih.xsub.util.Util
import java.io.Serializable
import java.util.*

class Indexes : Serializable {

    var shortcuts: List<Artist>? = null
        private set
    private var artists: MutableList<Artist>? = null
    var entries: List<MusicDirectory.Entry>? = null
        private set

    constructor(shortcuts: List<Artist>, artists: MutableList<Artist>) {
        this.shortcuts = shortcuts
        this.artists = artists
        this.entries = ArrayList()
    }

    constructor(shortcuts: List<Artist>, artists: MutableList<Artist>, entries: List<MusicDirectory.Entry>) {
        this.shortcuts = shortcuts
        this.artists = artists
        this.entries = entries
    }

    fun getArtists(): List<Artist>? {
        return artists
    }

    fun setArtists(artists: List<Artist>) {
        this.shortcuts = ArrayList()
        this.artists!!.clear()
        this.artists!!.addAll(artists)
    }

    fun sortChildren(context: Context) {
        val prefs = Util.getPreferences(context)
        val ignoredArticlesString = prefs.getString(Constants.CACHE_KEY_IGNORE, "The El La Los Las Le Les")
        val ignoredArticles = ignoredArticlesString!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        Artist.sort(shortcuts!!, ignoredArticles)
        Artist.sort(artists!!, ignoredArticles)
    }
}
