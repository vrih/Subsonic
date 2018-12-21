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
import java.text.Collator
import java.util.*

/**
 * @author Sindre Mehus
 */
class Artist(val id: String, val name: String) : Serializable{
    var isStarred: Boolean = false
    var rating: Int = 0
    var closeness: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val entry = other as Artist?
        return id == entry!!.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return name
    }

    internal class ArtistComparator(private val ignoredArticles: Array<String>) : Comparator<Artist> {
        private val collator: Collator = Collator.getInstance(Locale.US)

        init {
            this.collator.strength = Collator.PRIMARY
        }

        override fun compare(lhsArtist: Artist, rhsArtist: Artist): Int {
            var lhs = lhsArtist.name.toLowerCase()
            var rhs = rhsArtist.name.toLowerCase()

            for (article in ignoredArticles) {
                var index = lhs.indexOf(article.toLowerCase() + " ")
                if (index == 0) {
                    lhs = lhs.substring(article.length + 1)
                }
                index = rhs.indexOf(article.toLowerCase() + " ")
                if (index == 0) {
                    rhs = rhs.substring(article.length + 1)
                }
            }

            return collator.compare(lhs, rhs)
        }
    }

    companion object {
        private val TAG = Artist::class.java.simpleName
        const val MISSING_ID = "-2"

        fun sort(artists: List<Artist>, ignoredArticles: Array<String>) {
            try {
                Collections.sort(artists, ArtistComparator(ignoredArticles))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sort artists", e)
            }

        }
    }
}
