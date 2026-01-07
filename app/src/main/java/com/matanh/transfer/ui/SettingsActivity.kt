package com.matanh.transfer.ui

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.matanh.transfer.util.Constants
import com.matanh.transfer.util.FileUtils
import com.matanh.transfer.R


class SettingsActivity : AppCompatActivity() {

    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                FileUtils.persistUriPermission(this, uri)
                val prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE)
                prefs.edit { putString(Constants.EXTRA_FOLDER_URI, uri.toString()) }
                Toast.makeText(this, "Shared folder selected", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportActionBar?.title = getString(R.string.title_activity_settings)
    }

    fun launchFolderSelection() {
        selectFolderLauncher.launch(null)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Tell PreferenceManager to use the right SharedPreferences file (see #18)
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREFS_NAME
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Password Preference
            val passwordPreference =
                findPreference<EditTextPreference>(getString(R.string.pref_key_server_password))
            updatePasswordSummary(passwordPreference)
            passwordPreference?.setOnPreferenceChangeListener { preference, newValue ->
                val newPassword = newValue as String?
                if (newPassword.isNullOrEmpty()) {
                    preference.summary = getString(R.string.pref_summary_password_protect_off)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.password_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    preference.summary = getString(R.string.pref_summary_password_protect_on)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.password_set),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }

             // SwitchPreferenceCompat
            val autoStartSwitch =
                findPreference<Preference>(getString(R.string.pref_key_auto_start_server_disabled))

            autoStartSwitch?.setOnPreferenceChangeListener { preference, newValue ->
                val isEnabled = newValue as Boolean
                true
            }

            // Folder Selection Preference
            val folderPreference = findPreference<Preference>("pref_key_shared_folder")
            folderPreference?.setOnPreferenceClickListener {
                (activity as? SettingsActivity)?.launchFolderSelection()
                true
            }
            updateFolderSummary()
        }

        private fun updatePasswordSummary(passwordPreference: EditTextPreference?) {
            val prefs = preferenceManager.sharedPreferences


            val password = prefs
                ?.getString(getString(R.string.pref_key_server_password), null)
            if (password.isNullOrEmpty()) {
                passwordPreference?.summary = getString(R.string.pref_summary_password_protect_off)
            } else {
                passwordPreference?.summary = getString(R.string.pref_summary_password_protect_on)
            }
        }

        private fun updateFolderSummary() {
            val prefs = preferenceManager.sharedPreferences
            val uriString = prefs?.getString(Constants.EXTRA_FOLDER_URI, null)
            val folderPreference = findPreference<Preference>("pref_key_shared_folder")
            if (uriString != null) {
                val uri = uriString.toUri()
                val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
                folderPreference?.summary = docFile?.name ?: uri.path
            } else {
                folderPreference?.summary = getString(R.string.no_folder_selected)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}