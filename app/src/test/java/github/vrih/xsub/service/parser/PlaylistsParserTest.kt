//package github.vrih.xsub.service.parser
//
//import android.content.Context
//import androidx.test.core.app.ApplicationProvider
//import com.google.common.truth.Truth
//import github.vrih.xsub.domain.Playlist
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.robolectric.RobolectricTestRunner
//import java.io.StringReader
//
//@RunWith(RobolectricTestRunner::class)
//class PlaylistsParserTest {
//
//    @Test
//    fun parse() {
//        val context = ApplicationProvider.getApplicationContext<Context>()
//
//        val response = """
//            <subsonic-response status="ok" version="1.11.0">
//                <playlists>
//                    <playlist id="15" name="Some random songs" comment="Just something I tossed together" owner="admin" public="false" songCount="6" duration="1391" created="2012-04-17T19:53:44" coverArt="pl-15">
//                        <allowedUser>sindre</allowedUser>
//                        <allowedUser>john</allowedUser>
//                    </playlist>
//                    <playlist id="16" name="More random songs" comment="No comment" owner="admin" public="true" songCount="5" duration="1018" created="2012-04-17T19:55:49" coverArt="pl-16"/>
//                </playlists>
//            </subsonic-response>""".trimIndent()
//
//        val parser = PlaylistsParser(context, 0)
//        Truth.assertThat(parser.parse(StringReader(response))).isEqualTo(
//                arrayListOf(
//                        Playlist("16", "More random songs", "admin", "Just something I tossed together", 6, false, "2012-04-17T19:53:44", 1391),
//                        Playlist("15", "Some random songs", "admin", "No comment", 5, false, "2012-04-17T19:55:49", 1018)
//        )
//        )
//    }
//}