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
package github.vrih.xsub.domain

import java.util.regex.Pattern

/**
 * The criteria for a music search.
 *
 * @author Sindre Mehus
 */
class SearchCritera(val query: String, val artistCount: Int, val albumCount: Int, val songCount: Int) {
    private var pattern: Pattern? = null

    /**
     * Returns and caches a pattern instance that can be used to check if a
     * string matches the query.
     */
    fun getPattern(): Pattern? {

        // If the pattern wasn't already cached, create a new regular expression
        // from the search string :
        //  * Surround the search string with ".*" (match anything)
        //  * Replace spaces and wildcard '*' characters with ".*"
        //  * All other characters are properly quoted
        if (this.pattern == null) {
            val regex = StringBuilder(".*")
            var currentPart = StringBuilder()
            for (i in 0 until query.length) {
                val c = query[i]
                if (c == '*' || c == ' ') {
                    regex.append(Pattern.quote(currentPart.toString()))
                    regex.append(".*")
                    currentPart = StringBuilder()
                } else {
                    currentPart.append(c)
                }
            }
            if (currentPart.isNotEmpty()) {
                regex.append(Pattern.quote(currentPart.toString()))
            }

            regex.append(".*")
            this.pattern = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE)
        }

        return this.pattern
    }
}
