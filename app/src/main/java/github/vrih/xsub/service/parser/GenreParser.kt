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

 Copyright 2010 (C) Sindre Mehus
 */
package github.vrih.xsub.service.parser

import android.content.Context
import android.os.Build
import android.text.Html
import android.util.Log
import github.vrih.xsub.domain.Genre
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.*

class GenreParser(context: Context, instance: Int) : AbstractParser(context, instance) {

    @Throws(Exception::class)
    fun parse(reader: Reader): List<Genre> {
        val result = ArrayList<Genre>()
        var sr: StringReader? = null

        try {
            val br = BufferedReader(reader)
            var xml: StringBuilder
            var line = br.readLine()
            xml = StringBuilder(line)

            line = br.readLine()
            while (line != null) {
                xml.append(line)
                line = br.readLine()
            }
            br.close()

            // Replace double escaped ampersand (&amp;apos;)
            xml = StringBuilder(xml.toString().replace("(?:&amp;)(amp;|lt;|gt;|#37;|apos;)".toRegex(), "&$1"))

            // Replace unescaped ampersand
            xml = StringBuilder(xml.toString().replace("&(?!amp;|lt;|gt;|#37;|apos;)".toRegex(), "&amp;"))

            // Replace unescaped percent symbol
            // No replacements for <> at this time
            xml = StringBuilder(xml.toString().replace("%".toRegex(), "&#37;"))

            xml = StringBuilder(xml.toString().replace("'".toRegex(), "&apos;"))

            sr = StringReader(xml.toString())
        } catch (ioe: IOException) {
            Log.e(TAG, "Error parsing Genre XML", ioe)
        }

        sr ?: run {
            Log.w(TAG, "Unable to parse Genre XML, returning empty list")
            return result
        }

        init(sr)

        var albumCount = 0
        var songCount = 0
        var eventType: Int
        do {
            eventType = nextParseEvent()
            var genreName : String
            if (eventType == XmlPullParser.START_TAG) {
                val name = elementName
                when (name) {
                    "genre" -> {
                        albumCount = getInteger("albumCount")
                        songCount = getInteger("songCount")
                    }
                    "error" -> handleError()
                }
            } else if (eventType == XmlPullParser.TEXT) {
                val value = text
                genreName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString()
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(value).toString()
                }

                if (genreName != "") result.add(Genre(genreName, albumCount, songCount))
                    albumCount = 0
                    songCount = 0
            }
        } while (eventType != XmlPullParser.END_DOCUMENT)

        validate()

        return Genre.GenreComparator.sort(result)
    }

    companion object {
        private val TAG = GenreParser::class.java.simpleName
    }
}
