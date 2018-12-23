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

import java.io.Serializable

/**
 * Represents the version number of the Subsonic Android app.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.3 $ $Date: 2006/01/20 21:25:16 $
 */
class Version : Comparable<Version>, Serializable {
    private var major: Int = 0
    var minor: Int = 0
    private var beta: Int = 0
    private var bugfix: Int = 0

    val version: String
        get() {
            when (major) {
                1 -> when (minor) {
                    0 -> return "3.8"
                    1 -> return "3.9"
                    2 -> return "4.0"
                    3 -> return "4.1"
                    4 -> return "4.2"
                    5 -> return "4.3.1"
                    6 -> return "4.5"
                    7 -> return "4.6"
                    8 -> return "4.7"
                    9 -> return "4.8"
                    10 -> return "4.9"
                    11 -> return "5.1"
                    12 -> return "5.2"
                    13 -> return "5.3"
                    14 -> return "6.0"
                }
            }
            return ""
        }

    constructor()

    /**
     * Creates a new version instance by parsing the given string.
     * @param version A string of the format "1.27", "1.27.2" or "1.27.beta3".
     */
    constructor(version: String) {
        val s = version.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        major = Integer.valueOf(s[0])
        minor = Integer.valueOf(s[1])

        if (s.size > 2) {
            if (s[2].contains("beta")) {
                beta = Integer.valueOf(s[2].replace("beta", ""))
            } else {
                bugfix = Integer.valueOf(s[2])
            }
        }
    }

    /**
     * Return whether this object is equal to another.
     * @param other Object to compare to.
     * @return Whether this object is equals to another.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val version = other as Version?

        if (beta != version!!.beta) return false
        if (bugfix != version.bugfix) return false
        return if (major != version.major) false else minor == version.minor
    }

    /**
     * Returns a hash code for this object.
     * @return A hash code for this object.
     */
    override fun hashCode(): Int {
        var result: Int = major
        result = 29 * result + minor
        result = 29 * result + beta
        result = 29 * result + bugfix
        return result
    }

    /**
     * Returns a string representation of the form "1.27", "1.27.2" or "1.27.beta3".
     * @return A string representation of the form "1.27", "1.27.2" or "1.27.beta3".
     */
    override fun toString(): String {
        val buf = StringBuilder()
        buf.append(major).append('.').append(minor)
        if (beta != 0) {
            buf.append(".beta").append(beta)
        } else if (bugfix != 0) {
            buf.append('.').append(bugfix)
        }

        return buf.toString()
    }

    /**
     * Compares this object with the specified object for order.
     * @param other The object to compare to.
     * @return A negative integer, zero, or a positive integer as this object is less than, equal to, or
     * greater than the specified object.
     */
    override fun compareTo(other: Version): Int {
        if (major < other.major) {
            return -1
        } else if (major > other.major) {
            return 1
        }

        if (minor < other.minor) {
            return -1
        } else if (minor > other.minor) {
            return 1
        }

        if (bugfix < other.bugfix) {
            return -1
        } else if (bugfix > other.bugfix) {
            return 1
        }

        val thisBeta = if (beta == 0) Integer.MAX_VALUE else beta
        val otherBeta = if (other.beta == 0) Integer.MAX_VALUE else other.beta

        if (thisBeta < otherBeta) {
            return -1
        } else if (thisBeta > otherBeta) {
            return 1
        }

        return 0
    }
}
