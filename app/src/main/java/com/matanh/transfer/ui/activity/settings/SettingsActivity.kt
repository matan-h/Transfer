package com.matanh.transfer.ui.activity.settings

import android.content.Context
import android.net.Uri
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.matanh.transfer.R
import com.matanh.transfer.databinding.ActivitySettingsBinding
import com.matanh.transfer.ui.common.BaseActivity
import com.matanh.transfer.ui.views.SettingsCategoryView
import com.matanh.transfer.ui.views.SettingsItemView
import com.matanh.transfer.ui.views.SettingsSwitchItemView
import com.matanh.transfer.util.Constants
import com.matanh.transfer.util.FileUtils.persistFolderUri

class SettingsActivity : BaseActivity<ActivitySettingsBinding>(ActivitySettingsBinding::inflate) {

    private lateinit var sharedFolderPref: SettingsItemView

    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                persistFolderUri(uri)
                putPrefString(Constants.EXTRA_FOLDER_URI, uri.toString())
                sharedFolderPref.setDescription(getFolderSummary())
                toast(getString(R.string.folder_setup_complete))
            }
        }

    fun launchFolderSelection() {
        selectFolderLauncher.launch(null)
    }

    override fun init() {
        setupContent(binding.content)
    }

    override fun initLogic() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            it.hapticClick()
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupContent(content: ViewGroup) {

        val categories = listOf(
            SettingsCategoryView(this).apply {
                setTitle("General")
                setItems(
                    buildList {
                        add(
                            createPreference(
                                R.drawable.palette, "Look & feel", "Dynamic colors, Dark theme"
                            ) {
                                it.hapticClick()
                                openActivity<LookAndFeelActivity>()
                            })
                    }
                )
            },

            SettingsCategoryView(this).apply {
                setTitle("Security")

                val ipPermissionKey = getString(R.string.pref_key_ip_permission_enabled)
                val serverPasswordKey = getString(R.string.pref_key_server_password)

                val isIpPermissionEnabled = getPrefBoolean(ipPermissionKey, true)

                setItems(
                    buildList {

                        add(
                            createSwitchPreference(
                                icon = R.drawable.app_badging,
                                title = "Display Permission Dialog",
                                desc = "Ask for permission for new IP's. Valid for 1 hour",
                                isChecked = isIpPermissionEnabled
                            ) { enabled ->
                                binding.content.hapticClick()
                                putPrefBoolean(ipPermissionKey, enabled)
                            }
                        )

                        add(
                            SettingsItemView(this@SettingsActivity).apply {
                                setContent(
                                    R.drawable.password,
                                    title,
                                    getPasswordSummary(serverPasswordKey)
                                )
                                setOnClickListener {
                                    it.hapticClick()
                                    this@SettingsActivity.showTextInputDialog(
                                        title = "Enter a password", hintText = "Password"
                                    ) { password ->
                                        putPrefString(serverPasswordKey, password)
                                        setDescription(getPasswordSummary(serverPasswordKey))
                                    }
                                }
                            }
                        )

                        add(
                            SettingsItemView(this@SettingsActivity).apply {
                                setContent(
                                    R.drawable.folder, "Shared Folder", getFolderSummary()
                                )

                                sharedFolderPref = this

                                setOnClickListener {
                                    it.hapticClick()
                                    launchFolderSelection()
                                }
                            }
                        )
                    }
                )
            }
        )

        categories.forEach(content::addView)
    }

    private fun Context.getFolderSummary(): String {
        val uriString = getPrefString(Constants.EXTRA_FOLDER_URI, "")
        if (uriString.isEmpty()) return "No folder selected"

        val uri = uriString.toUri()
        val docFile = DocumentFile.fromTreeUri(this, uri)
        return docFile?.name ?: uri.path.orEmpty()
    }

    fun Context.showTextInputDialog(
        title: String,
        hintText: String,
        onResult: (String) -> Unit
    ) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val password = getPrefString(getString(R.string.pref_key_server_password), "")

        val inputLayout = TextInputLayout(this).apply {

            // Material style
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii( dp(8).toFloat(),
                dp(8).toFloat(),
                dp(8).toFloat(),
                dp(8).toFloat())

            // Spacing
            setPadding(dp(16), dp(8), dp(16), dp(8))

            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(20), dp(12), dp(20), dp(4))
            }

            hint = hintText
        }

        val editText = TextInputEditText(this).apply {

            setPadding(dp(12), dp(12), dp(12), dp(12))

            textSize = 16f
            isSingleLine = true

            setText(password)

            // Enable "Done" action
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT

            setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            setHintTextColor(getThemeColor(android.R.attr.textColorHint))

        }

        inputLayout.addView(editText)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(inputLayout)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {

            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            // OK button logic
            val submit = {
                val text = editText.text?.toString()?.trim().orEmpty()
                onResult(text)
                dialog.dismiss()
            }

            okButton.setOnClickListener {
                submit()
            }

            // IME Done → OK
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submit()
                    true
                } else {
                    false
                }
            }

            // Autofocus + show keyboard
            editText.requestFocus()
            editText.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        dialog.show()
    }


    private fun Context.getPasswordSummary(key: String): String {
        return if (getPrefString(key, "").isNotEmpty()) {
            getString(R.string.pref_summary_password_protect_on)
        } else {
            getString(R.string.pref_summary_password_protect_off)
        }
    }


    private fun createPreference(
        @DrawableRes icon: Int,
        title: CharSequence,
        desc: CharSequence,
        listener: View.OnClickListener
    ): SettingsItemView = SettingsItemView(this).apply {
        setContent(icon, title, desc)
        setOnClickListener(listener)
    }

    private fun createSwitchPreference(
        @DrawableRes icon: Int,
        title: CharSequence,
        desc: CharSequence,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ): SettingsSwitchItemView = SettingsSwitchItemView(this).apply {
        setContent(icon, title, desc, isChecked)
        setOnCheckedChangeListener(onCheckedChange)
    }

}