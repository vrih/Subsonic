package github.vrih.xsub.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.*

class GenreComparatorTest {

    /**
     * Sort genres which doesn't have name
     */
    @Test
    fun testSortGenreWithoutNameComparator() {
        val g1 = Genre("Genre", 1, 1)
        val g2 = Genre("", 1, 1)

        val genres = ArrayList<Genre>()
        genres.add(g1)
        genres.add(g2)

        val sortedGenre = Genre.GenreComparator.sort(genres)
        assertThat(sortedGenre[0]).isEqualTo(g2)
    }

    /**
     * Sort genre with same name
     */
    @Test
    fun testSortGenreWithSameName() {
        val g1 = Genre("Genre", 1, 1)
        val g2 = Genre("genre", 1, 1)

        val genres = ArrayList<Genre>()
        genres.add(g1)
        genres.add(g2)

        val sortedGenre = Genre.GenreComparator.sort(genres)
        assertThat(sortedGenre[0]).isEqualTo(g1)
    }

    /**
     * test nominal genre sort
     */
    @Test
    fun testSortGenre() {
        val g1 = Genre("Rock", 1, 1)
        val g2 = Genre("Pop", 1, 1)
        val g3 = Genre("Rap", 1, 1)

        val genres = ArrayList<Genre>()
        genres.add(g1)
        genres.add(g2)
        genres.add(g3)

        val sortedGenre = Genre.GenreComparator.sort(genres)
        assertThat(sortedGenre[0]).isEqualTo(g2)
        assertThat(sortedGenre[1]).isEqualTo(g3)
        assertThat(sortedGenre[2]).isEqualTo(g1)
    }
}
