package github.vrih.xsub.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class BookmarkTest {

    /**
     * tests the set created date
     * @throws ParseException
     */
    @Test
    @Throws(ParseException::class)
    fun testSetCreated() {
        val bookmark = Bookmark()
        bookmark.setCreated(null as String?)
        assertThat(bookmark.getCreated()).isNotNull()

        bookmark.setCreated("")
        assertThat(bookmark.getCreated()).isNotNull()

        bookmark.setCreated("2014-04-04")
        assertThat(bookmark.getCreated()).isNotNull()

        bookmark.setCreated("2014/0404")
        assertThat(bookmark.getCreated()).isNotNull()

        bookmark.setCreated("18/03/1988")
        assertThat(bookmark.getCreated()).isNotNull()

        bookmark.setCreated("18/03/88")
        assertThat(bookmark.getCreated()).isNotNull()

        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse("2013-10-20T00:00:00")
        bookmark.setCreated("2013-10-20T00:00:00")
        assertThat(bookmark.getCreated()).isEqualTo(date)
    }
}
