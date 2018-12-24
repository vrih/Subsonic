package github.vrih.xsub.service.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth
import github.vrih.xsub.domain.PodcastEpisode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
class PodcastEntryParserTest {

    @Test
    fun parseNewestEpisodes() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val response = """
            <subsonic-response status="ok" version="1.13.0">
                <newestPodcasts>
                    <episode
                        id="7390"
                        parent="7389"
                        isDir="false"
                        title="Jonas Gahr Støre"
                        album="NRK – Hallo P3"
                        artist="Podcast"
                        year="2015"
                        coverArt="7389"
                        size="41808585"
                        contentType="audio/mpeg"
                        suffix="mp3"
                        duration="2619"
                        bitRate="128"
                        isVideo="false"
                        created="2015-09-07T20:07:31.000Z"
                        artistId="453"
                        type="podcast"
                        streamId="7410"
                        channelId="17"
                        description="Description"
                        status="completed"
                        publishDate="2015-09-07T15:29:00.000Z"/>
                </newestPodcasts>
            </subsonic-response>""".trimIndent()

        val parser = PodcastEntryParser(context, 0)
        val result = parser.parse("1", StringReader(response)).getChildren()!![0]
        Truth.assertThat(result).isEqualTo(PodcastEpisode("7390", "completed"))
        Truth.assertThat(result.coverArt).isEqualTo("7389")
    }

    @Test
    fun parseChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val response = """
            <subsonic-response status="ok" version="1.13.0">
                <podcasts>
                    <channel id="1" url="http://downloads.bbc.co.uk/podcasts/fivelive/drkarl/rss.xml" title="Dr Karl and the Naked Scientist" description="Dr Chris Smith aka The Naked Scientist with the latest news from the world of science and Dr Karl answers listeners' science questions." coverArt="pod-1" originalImageUrl="http://downloads.bbc.co.uk/podcasts/fivelive/drkarl/drkarl.jpg" status="completed">
                    <episode id="34" streamId="523" channelId="1" title="Scorpions have re-evolved eyes" description="This week Dr Chris fills us in on the UK's largest free science festival, plus all this week's big scientific discoveries." publishDate="2011-02-03T14:46:43" status="completed" parent="11" isDir="false" year="2011" genre="Podcast" coverArt="24" size="78421341" contentType="audio/mpeg" suffix="mp3" duration="3146" bitRate="128" path="Podcast/drkarl/20110203.mp3"/><episode id="35" streamId="524" channelId="1" title="Scar tissue and snake venom treatment" description="This week Dr Karl tells the gruesome tale of a surgeon who operated on himself." publishDate="2011-09-03T16:47:52" status="completed" parent="11" isDir="false" year="2011" genre="Podcast" coverArt="27" size="45624671" contentType="audio/mpeg" suffix="mp3" duration="3099" bitRate="128" path="Podcast/drkarl/20110903.mp3"/></channel><channel id="2" url="http://podkast.nrk.no/program/herreavdelingen.rss" title="NRK P1 - Herreavdelingen" description="Et program der herrene Yan Friis og Finn Bjelke møtes og musikk nytes." coverArt="pod-2" originalImageUrl="http://gfx.nrk.no/oP_mZkqyrOkZiAOilZPvFA1nlzIxOYVV9yq7P_J-ngjw.jpg" status="completed">
    </channel><channel id="3" url="http://foo.bar.com/xyz.rss" status="error" errorMessage="Not found."/></podcasts>
            </subsonic-response>""".trimIndent()

        val parser = PodcastEntryParser(context, 0)
        val result = parser.parse("1", StringReader(response)).getChildren()!![0]
        Truth.assertThat(result).isEqualTo(PodcastEpisode("34", "completed"))
    }

}