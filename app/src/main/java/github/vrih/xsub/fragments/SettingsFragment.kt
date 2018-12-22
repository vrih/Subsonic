package github.vrih.xsub.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import github.vrih.xsub.R
import github.vrih.xsub.activity.SettingsActivity


class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        activity?.title = "Settings"
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Settings")
        addPreferencesFromResource(R.xml.settings)
    }

    companion object {
        const val FRAGMENT_TAG = "settings_fragment"
    }
}

class ServerSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Server")

        addPreferencesFromResource(R.xml.settings_servers)
    }

    companion object {
        const val FRAGMENT_TAG = "server_settings_fragment"
    }
}

class AppearanceSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Appearance")
        addPreferencesFromResource(R.xml.settings_appearance)
    }

    companion object {
        const val FRAGMENT_TAG = "appearance_settings_fragment"
    }
}

class DrawerSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Drawer")
        addPreferencesFromResource(R.xml.settings_drawer)
    }

    companion object {
        const val FRAGMENT_TAG = "drawer_settings_fragment"
    }
}

class CacheSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Cache")
        addPreferencesFromResource(R.xml.settings_cache)
    }

    companion object {
        const val FRAGMENT_TAG = "cache_settings_fragment"
    }
}

class CastSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Cast")
        addPreferencesFromResource(R.xml.settings_cast)
    }

    companion object {
        const val FRAGMENT_TAG = "cast_settings_fragment"
    }
}

class PlaybackSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Playback")
        addPreferencesFromResource(R.xml.settings_playback)
    }

    companion object {
        const val FRAGMENT_TAG = "playback_settings_fragmett"
    }
}

class SyncSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Sync")
        addPreferencesFromResource(R.xml.settings_sync)
    }

    companion object {
        const val FRAGMENT_TAG = "sync_settings_fragment"
    }
}



