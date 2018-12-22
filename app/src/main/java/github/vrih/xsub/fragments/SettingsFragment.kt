package github.vrih.xsub.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import github.vrih.xsub.R
import github.vrih.xsub.util.Constants
import github.vrih.xsub.util.Util


class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)
    }

    companion object {
        const val FRAGMENT_TAG = "settings_fragment"
    }
}

class ServerSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_servers)
    }

    companion object {
        const val FRAGMENT_TAG = "server_settings_fragment"
    }
}

class AppearanceSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_appearance)
    }

    companion object {
        const val FRAGMENT_TAG = "appearance_settings_fragment"
    }
}

class DrawerSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_drawer)
    }

    companion object {
        const val FRAGMENT_TAG = "drawer_settings_fragment"
    }
}

class CacheSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_cache)
    }

    companion object {
        const val FRAGMENT_TAG = "cache_settings_fragment"
    }
}

class CastSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_cast)
    }

    companion object {
        const val FRAGMENT_TAG = "cast_settings_fragment"
    }
}

class PlaybackSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_playback)
    }

    companion object {
        const val FRAGMENT_TAG = "playback_settings_fragmett"
    }
}

class SyncSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_sync)
    }

    companion object {
        const val FRAGMENT_TAG = "sync_settings_fragment"
    }
}



