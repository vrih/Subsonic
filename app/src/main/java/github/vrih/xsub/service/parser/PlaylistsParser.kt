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
import github.vrih.xsub.domain.Playlist
import org.xmlpull.v1.XmlPullParser
import java.io.Reader
import java.util.*

/**
 * @author Sindre Mehus
 */
class PlaylistsParser(context: Context, instance: Int) : AbstractParser(context, instance) {

    @Throws(Exception::class)
    fun parse(reader: Reader): List<Playlist> {
        init(reader)

        val result = ArrayList<Playlist>()
        var eventType: Int
        do {
            eventType = nextParseEvent()
            if (eventType == XmlPullParser.START_TAG) {
                val tag = elementName
                if ("playlist" == tag) {
                    result.add(Playlist(
                            get("id"),
                            get("name"),
                            get("owner"),
                            get("comment"),
                            getInteger("songCount"),
                            getBoolean("public"),
                            get("created"),
                            getInteger("duration")
                    ))
                } else if ("error" == tag) {
                    handleError()
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT)

        validate()

        return Playlist.PlaylistComparator.sort(result)
    }

}
