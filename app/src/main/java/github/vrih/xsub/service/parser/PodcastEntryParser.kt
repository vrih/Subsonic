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
package github.vrih.xsub.service.parser

import android.content.Context
import android.util.Log
import github.vrih.xsub.domain.Bookmark
import github.vrih.xsub.domain.MusicDirectory
import github.vrih.xsub.domain.PodcastEpisode
import github.vrih.xsub.util.FileUtil
import org.xmlpull.v1.XmlPullParser
import java.io.Reader

/**
 *
 * @author Scott
 */
class PodcastEntryParser(context: Context, instance: Int) : AbstractParser(context, instance) {

    @Throws(Exception::class)
    fun parse(channel: String?, reader: Reader): MusicDirectory {
        init(reader)

        val episodes = MusicDirectory()
        var coverArt: String? = null
        var eventType: Int
        var valid = false
        do {
            eventType = nextParseEvent()
            if (eventType == XmlPullParser.START_TAG) {
                val name = elementName
                if ("channel" == name) {
                    val id = get("id")
                    if (id == channel) {
                        episodes.id = id
                        episodes.name = get("title")
                        coverArt = get("coverArt")
                        valid = true
                    } else {
                        valid = false
                    }
                } else if ("newestPodcasts" == name) {
                    valid = true
                } else if ("episode" == name && valid) {
                    val episode = PodcastEpisode(get("id"), get("status"))
                    episode.id = get("streamId")
                    episode.title = get("title")
                    episodes.id?.let {
                        episode.artist = episodes.name
                        episode.parent = it
                    } ?: run {
                        episode.parent = get("channelId")
                    }
                    episode.album = get("description")
                    episode.date = get("publishDate")
                    episode.date = episode.date ?: get("created")
                    if (episode.date?.contains("T") == true) {
                        episode.date = episode.date?.replace("T", " ")
                    }
                    episode.coverArt = coverArt ?: get("coverArt")
                    Log.w("Podcast", "parser coverart" + episode.coverArt)
                    episode.size = getLong("size")
                    episode.contentType = get("contentType")
                    episode.suffix = get("suffix")
                    episode.duration = getInteger("duration")
                    episode.bitRate = getInteger("bitRate")
                    episode.isVideo = getBoolean("isVideo")
                    episode.path = get("path")
                    if (episode.path == null) {
                        episode.path = FileUtil.getPodcastPath(episode)
                    } else if (episode.path!!.indexOf("Podcasts/") == 0) {
                        episode.path = episode.path!!.substring("Podcasts/".length)
                    }

                    val bookmark = getInteger("bookmarkPosition")
                    bookmark?.let { episode.bookmark = Bookmark(it) }
                    episode.type = MusicDirectory.Entry.TYPE_PODCAST

                    if (episode.id == null) {
                        episode.id = bogusId.toString()
                        bogusId--
                    }
                    episodes.addChild(episode)
                } else if ("error" == name) {
                    handleError()
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT)

        validate()
        return episodes
    }

    companion object {
        private var bogusId = -1
    }
}
