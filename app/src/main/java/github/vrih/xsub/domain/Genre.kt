package github.vrih.xsub.domain

import java.io.Serializable
import java.util.*

class Genre : Serializable {
    var name: String? = null
    var albumCount: Int? = null
    var songCount: Int? = null

    override fun toString(): String {
        return name ?: "Unknown genre"
    }

    class GenreComparator : Comparator<Genre> {
        override fun compare(genre1: Genre, genre2: Genre): Int {
            val genre1Name = if (genre1.name != null) genre1.name else ""
            val genre2Name = if (genre2.name != null) genre2.name else ""

            return genre1Name!!.compareTo(genre2Name!!, ignoreCase = true)
        }

        companion object {

            fun sort(genres: List<Genre>): List<Genre> {
                Collections.sort(genres, GenreComparator())
                return genres
            }
        }

    }
}
