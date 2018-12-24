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

	Copyright 2014 (C) Scott Jackson
*/

package github.vrih.xsub.domain

import android.os.Parcel
import android.os.Parcelable

import org.fourthline.cling.model.meta.Device

/**
 * Created by Scott on 11/1/2014.
 */
class DLNADevice : Parcelable {
    lateinit var renderer: Device<*, *, *>
    val id: String?
    val name: String?
    val description: String?
    var volume: Int = 0
    val volumeMax: Int

    private constructor(`in`: Parcel) {
        id = `in`.readString()
        name = `in`.readString()
        description = `in`.readString()
        volume = `in`.readInt()
        volumeMax = `in`.readInt()
    }

    constructor(renderer: Device<*, *, *>, id: String, name: String, description: String, volume: Int, volumeMax: Int) {
        this.renderer = renderer
        this.id = id
        this.name = name
        this.description = description
        this.volume = volume
        this.volumeMax = volumeMax
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(name)
        dest.writeString(description)
        dest.writeInt(volume)
        dest.writeInt(volumeMax)
    }

    companion object CREATOR : Parcelable.Creator<DLNADevice> {
        override fun createFromParcel(parcel: Parcel): DLNADevice {
            return DLNADevice(parcel)
        }

        override fun newArray(size: Int): Array<DLNADevice?> {
            return arrayOfNulls(size)
        }
    }
}
