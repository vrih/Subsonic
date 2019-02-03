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
package github.vrih.xsub.domain

import android.content.Context
import github.vrih.xsub.util.FileUtil
import github.vrih.xsub.util.Util
import java.io.Serializable

/**
 * Information about the Subsonic server.
 *
 * @author Sindre Mehus
 */
class ServerInfo : Serializable {

    var isLicenseValid: Boolean = false
    var restVersion: Version? = null
    var restType: Int = 0
    init {
        restType = TYPE_SUBSONIC
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other == null || javaClass != other.javaClass) {
            return false
        }

        val info = other as ServerInfo?

        return if (this.restType != info!!.restType) {
            false
        } else if (this.restVersion == null || info.restVersion == null) {
            // Should never be null unless just starting up
            false
        } else {
            this.restVersion == info.restVersion
        }
    }

    fun saveServerInfo(context: Context, instance: Int) {
        val current = SERVER
        if (this != current) {
            SERVER = this
            FileUtil.serialize(context, this, getCacheName(context, instance))
        }
    }

    override fun hashCode(): Int {
        var result = restVersion?.hashCode() ?: 0
        result = 31 * result + restType
        return result
    }

    companion object {
        private const val TYPE_SUBSONIC = 1
        const val TYPE_MADSONIC = 2
        const val TYPE_AMPACHE = 3
        @JvmStatic
        private var SERVER: ServerInfo? = null

        private fun getServerInfo(): ServerInfo? {
            return SERVER
        }

        fun getServerVersion(): Version? {
            val server = getServerInfo() ?: return null

            return server.restVersion
        }

        @JvmStatic
        fun checkServerVersion(requiredVersion: String): Boolean {
            val server = getServerInfo() ?: return false

            val version = server.restVersion ?: return false
            val required = Version(requiredVersion)
            return version >= required
        }

        private fun getServerType(context: Context): Int {
            if (Util.isOffline(context)) {
                return 0
            }

            val server = getServerInfo() ?: return 0

            return server.restType
        }

        private fun isStockSubsonic(context: Context): Boolean = getServerType(context) == TYPE_SUBSONIC

        @JvmStatic
        fun isMadsonic(context: Context): Boolean = getServerType(context) == TYPE_MADSONIC

        @JvmStatic
        fun isMadsonic6(context: Context): Boolean = getServerType(context) == TYPE_MADSONIC && checkServerVersion("2.0")

        private fun getCacheName(context: Context, instance: Int): String {
            return "server-" + Util.getRestUrl(context, null, instance, false).hashCode() + ".ser"
        }

        fun hasArtistInfo(context: Context): Boolean {
            return if (!isMadsonic(context) && ServerInfo.checkServerVersion("1.11")) {
                true
            } else if (isMadsonic(context)) {
                checkServerVersion("2.0")
            } else {
                false
            }
        }

        fun canBookmark(): Boolean = checkServerVersion("1.9")
        fun canInternetRadio(): Boolean = checkServerVersion("1.9")
        fun canSavePlayQueue(context: Context): Boolean {
            return ServerInfo.checkServerVersion("1.12")
                    && (!ServerInfo.isMadsonic(context)
                    || checkServerVersion("2.0"))
        }

        fun canAlbumListPerFolder(context: Context): Boolean {
            return ServerInfo.checkServerVersion("1.11")
                    && (!ServerInfo.isMadsonic(context)
                    || checkServerVersion("2.0"))
                    && !Util.isTagBrowsing(context)
        }

        fun hasTopSongs(context: Context): Boolean {
            return ServerInfo.isMadsonic(context) || ServerInfo.checkServerVersion("1.13")
        }

        fun canUseToken(context: Context): Boolean {
            return if (isStockSubsonic(context) && checkServerVersion("1.14")) {
                !Util.getBlockTokenUse(context)
            } else {
                false
            }
        }

        fun hasSimilarArtists(context: Context): Boolean {
            return !ServerInfo.isMadsonic(context) || ServerInfo.checkServerVersion("2.0")
        }

        fun hasNewestPodcastEpisodes(): Boolean = ServerInfo.checkServerVersion("1.13")

        fun canRescanServer(context: Context): Boolean {
            return ServerInfo.isMadsonic(context) || ServerInfo.isStockSubsonic(context) && ServerInfo.checkServerVersion("1.15")
        }
    }
}
