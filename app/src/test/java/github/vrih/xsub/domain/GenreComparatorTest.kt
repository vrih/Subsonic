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
        val g1 = Genre()
        g1.name = "Genre"

        val g2 = Genre()

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
        val g1 = Genre()
        g1.name = "Genre"

        val g2 = Genre()
        g2.name = "genre"

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
        val g1 = Genre()
        g1.name = "Rock"

        val g2 = Genre()
        g2.name = "Pop"

        val g3 = Genre()
        g3.name = "Rap"

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
