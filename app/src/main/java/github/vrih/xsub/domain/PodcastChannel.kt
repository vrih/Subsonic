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

/**
 *
 * @author Scott
 */
class PodcastChannel : Serializable {
    var id: String? = null
    var name: String? = null
    var url: String? = null
    var description: String? = null
    var status: String? = null
    var errorMessage: String? = null
    var coverArt: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val entry = other as PodcastChannel?
        return id == entry!!.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    class PodcastComparator : Comparator<PodcastChannel> {

        override fun compare(podcast1: PodcastChannel, podcast2: PodcastChannel): Int {
            var lhs = podcast1.name
            var rhs = podcast2.name
            if (lhs == null && rhs == null) {
                return 0
            } else if (lhs == null) {
                return 1
            } else if (rhs == null) {
                return -1
            }

            lhs = lhs.toLowerCase()
            rhs = rhs.toLowerCase()

            for (article in ignoredArticles!!) {
                var index = lhs!!.indexOf(article.toLowerCase() + " ")
                if (index == 0) {
                    lhs = lhs.substring(article.length + 1)
                }
                index = rhs!!.indexOf(article.toLowerCase() + " ")
                if (index == 0) {
                    rhs = rhs.substring(article.length + 1)
                }
            }

            return lhs!!.compareTo(rhs!!, ignoreCase = true)
        }

        companion object {
            private var ignoredArticles: Array<String>? = null

            fun sort(podcasts: List<PodcastChannel>, context: Context): List<PodcastChannel> {
                val prefs = Util.getPreferences(context)
                val ignoredArticlesString = prefs.getString(Constants.CACHE_KEY_IGNORE, "The El La Los Las Le Les")
                ignoredArticles = ignoredArticlesString?.split(" ".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()

                Collections.sort(podcasts, PodcastComparator())
                return podcasts
            }
        }

    }
}
