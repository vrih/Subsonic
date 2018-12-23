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

 Copyright 2013 (C) Scott Jackson
 */
package github.vrih.xsub.domain

import java.io.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class Bookmark : Serializable {
    var position: Int = 0
    var comment: String? = null
    private var created: Date? = null
    private var changed: Date? = null

    constructor()

    constructor(position: Int) {
        this.position = position
    }

    fun getCreated(): Date? {
        return created
    }

    fun setCreated(created: String?) {
        if (created != null) {
            try {
                this.created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(created)
            } catch (e: ParseException) {
                this.created = Date()
            }

        } else {
            this.created = Date()
        }
    }

    fun setCreated(created: Date) {
        this.created = created
    }

    fun getChanged(): Date? {
        return changed
    }

    fun setChanged(changed: Date) {
        this.changed = changed
    }
}
