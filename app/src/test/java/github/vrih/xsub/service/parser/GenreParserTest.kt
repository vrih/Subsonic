//package github.vrih.xsub.service.parser
//
//import android.content.Context
//import androidx.test.core.app.ApplicationProvider
//import com.google.common.truth.Truth.assertThat
//import github.vrih.xsub.domain.Genre
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.robolectric.RobolectricTestRunner
//import java.io.StringReader
//
//
//@RunWith(RobolectricTestRunner::class)
//class GenreParserTest {
//    @Test
//    fun parse() {
//        val context = ApplicationProvider.getApplicationContext<Context>()
//
//        val response = """
//            <subsonic-response status="ok" version="1.10.2">
//                <genres>
//                    <genre songCount="28" albumCount="6">Electronic</genre>
//                    <genre songCount="6" albumCount="2">Hard Rock</genre>
//                </genres>
//            </subsonic-response>""".trimIndent()
//
//        val parser = GenreParser(context, 0)
//        assertThat(parser.parse(StringReader(response))).isEqualTo(
//                arrayListOf(
//                        Genre("Electronic", 6, 28),
//                        Genre("Hard Rock", 2, 6)
//                )
//        )
//
//    }
//}