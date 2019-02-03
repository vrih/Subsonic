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
}

class AppearanceSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Appearance")
        addPreferencesFromResource(R.xml.settings_appearance)
    }
}

class DrawerSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Drawer")
        addPreferencesFromResource(R.xml.settings_drawer)
    }
}

class CacheSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Cache")
        addPreferencesFromResource(R.xml.settings_cache)
    }
}

class CastSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Cast")
        addPreferencesFromResource(R.xml.settings_cast)
    }
}

class PlaybackSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Playback")
        addPreferencesFromResource(R.xml.settings_playback)
    }
}

class SyncSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val settingsActivity: SettingsActivity = activity as SettingsActivity
        settingsActivity.updateActionBarTitle("Sync")
        addPreferencesFromResource(R.xml.settings_sync)
    }
}