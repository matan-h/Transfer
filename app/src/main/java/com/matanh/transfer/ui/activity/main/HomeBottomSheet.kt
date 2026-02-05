package com.matanh.transfer.ui.activity.main

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.matanh.transfer.BuildConfig
import com.matanh.transfer.R
import com.matanh.transfer.databinding.LayoutBottomSheetBinding
import com.matanh.transfer.ui.activity.aboutus.AboutActivity
import com.matanh.transfer.ui.activity.settings.SettingsActivity
import com.matanh.transfer.ui.common.resolveColorAttr
import com.matanh.transfer.ui.common.setBottomMarginDp
import com.matanh.transfer.util.ErrorReport
import com.matanh.transfer.util.HapticUtils

class HomeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.userDescription.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)

        setupActions()
        setupCard()
        binding.views.post { binding.views.setBottomMarginDp(10) }
    }

    private fun setupActions() {
        setupAction(binding.actionSettings) {
            startActivity(Intent(requireActivity(), SettingsActivity::class.java))
        }

        setupAction(binding.actionAbout) {
            startActivity(Intent(requireActivity(), AboutActivity::class.java))
        }

        setupAction(binding.actionHelp) {
            ErrorReport().openReport(requireContext())
        }
    }

    private fun setupAction(view: View, action: () -> Unit) {
        view.setOnClickListener {
            it.isEnabled = false
            HapticUtils.weakVibrate(it)
            action()
            dismiss()
        }
    }

    private fun setupCard() {
        val color = requireContext().resolveColorAttr(
            if (requireActivity().isNightMode()) {
                com.google.android.material.R.attr.colorSurfaceContainerHighest
            } else {
                com.google.android.material.R.attr.colorSurfaceContainerLow
            }
        )
        binding.card.setCardBackgroundColor(color)
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun Context.isNightMode(): Boolean {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    override fun getTheme(): Int {
        return R.style.ThemeOverlay_Ui3_BottomSheetDialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}