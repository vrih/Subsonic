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

import github.vrih.xsub.domain.MusicDirectory.Entry
import java.io.Serializable
import java.util.*

class Share : Serializable {
    var id: String? = null
    var url: String? = null
    var description: String? = null
    var username: String? = null
    private var created: Date? = null
    var lastVisited: Date? = null
    private var expires: Date? = null
    var visitCount: Long? = null
    private val entries: MutableList<Entry>

    val name: String?
        get() = if (description != null && "" != description) {
            description
        } else {
            url!!.replaceFirst(".*/([^/?]+).*".toRegex(), "$1")
        }

    val musicDirectory: MusicDirectory
        get() {
            val dir = MusicDirectory()
            dir.addChildren(entries)
            dir.id = id
            dir.name = name
            return dir
        }

    init {
        entries = ArrayList()
    }

    fun getCreated(): Date? {
        return created
    }

    fun setCreated(created: Date) {
        this.created = created
    }

    fun getExpires(): Date? {
        return expires
    }

    fun setExpires(expires: Date) {
        this.expires = expires
    }

    fun addEntry(entry: Entry) {
        entries.add(entry)
    }
}
