package github.vrih.xsub.domain

import java.io.Serializable
import java.util.*

data class Genre(val name: String, val albumCount: Int, val songCount: Int) : Serializable {
    class GenreComparator : Comparator<Genre> {
        override fun compare(genre1: Genre, genre2: Genre): Int {
            return genre1.name.compareTo(genre2.name, ignoreCase = true)
        }

        companion object {

            fun sort(genres: List<Genre>): List<Genre> {
                Collections.sort(genres, GenreComparator())
                return genres
            }
        }
    }
}
