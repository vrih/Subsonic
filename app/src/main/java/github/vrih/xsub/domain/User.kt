/*
  This file is part of Subsonic.
	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
	Copyright 2014 (C) Scott Jackson
*/

package github.vrih.xsub.domain

import java.io.Serializable
import java.util.*

class User : Serializable {

    lateinit var username: String
    lateinit var password: String
    var email: String? = null

    constructor()

    constructor(username: String, email: String?) {
        this.username = username
        this.email = email
    }

    private val settings = ArrayList<Setting>()
    private var musicFolders: MutableList<Setting>? = null
    val musicFolderSettings: List<Setting>?
        get() = musicFolders

    fun getSettings(): List<Setting> {
        return settings
    }

    fun setSettings(settings: List<Setting>) {
        this.settings.clear()
        this.settings.addAll(settings)
    }

    fun addSetting(name: String, value: Boolean?) {
        settings.add(Setting(name, value))
    }

    fun addMusicFolder(musicFolder: MusicFolder) {
        if (musicFolders == null) {
            musicFolders = ArrayList()
        }

        musicFolders!!.add(MusicFolderSetting(musicFolder.id, musicFolder.name, false))
    }

    fun addMusicFolder(musicFolderSetting: MusicFolderSetting, defaultValue: Boolean) {
        if (musicFolders == null) {
            musicFolders = ArrayList()
        }

        musicFolders!!.add(MusicFolderSetting(musicFolderSetting.name, musicFolderSetting.label, defaultValue))
    }

    open class Setting internal constructor(val name: String, var value: Boolean?) : Serializable

    class MusicFolderSetting internal constructor(name: String, val label: String, value: Boolean?) : Setting(name, value)

    companion object {
        const val SCROBBLING = "scrobblingEnabled"
        const val ADMIN = "adminRole"
        const val SETTINGS = "settingsRole"
        const val DOWNLOAD = "downloadRole"
        const val UPLOAD = "uploadRole"
        const val COVERART = "coverArtRole"
        const val COMMENT = "commentRole"
        const val PODCAST = "podcastRole"
        const val STREAM = "streamRole"
        const val JUKEBOX = "jukeboxRole"
        const val SHARE = "shareRole"
        const val VIDEO_CONVERSION = "videoConversionRole"
        const val LASTFM = "lastFMRole"
        val ROLES: MutableList<String> = ArrayList()

        init {
            ROLES.add(ADMIN)
            ROLES.add(SETTINGS)
            ROLES.add(STREAM)
            ROLES.add(DOWNLOAD)
            ROLES.add(UPLOAD)
            ROLES.add(COVERART)
            ROLES.add(COMMENT)
            ROLES.add(PODCAST)
            ROLES.add(JUKEBOX)
            ROLES.add(SHARE)
            ROLES.add(VIDEO_CONVERSION)
        }
    }
}
