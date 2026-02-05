package com.matanh.transfer.ui.activity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.matanh.transfer.R
import com.matanh.transfer.databinding.QrBottomSheetBinding
import com.matanh.transfer.ui.common.setBottomMarginDp
import com.matanh.transfer.util.HapticUtils
import com.matanh.transfer.util.QRCodeGenerator

class QrcodeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: QrBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val qrUrl: String
        get() = requireArguments().getString(ARG_URL).orEmpty()

    companion object {
        private const val ARG_URL = "arg_url"
        fun newInstance(url: String) = QrcodeBottomSheet().apply {
            arguments = bundleOf(ARG_URL to url)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = QrBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupActions()
        setupQr()

        binding.views.post {
            binding.views.setBottomMarginDp(10)
        }
    }

    private fun setupActions() {
        binding.back.setOnClickListener {
            HapticUtils.weakVibrate(it)
            dismiss()
        }
    }

    private fun setupQr() {
        if (qrUrl.isBlank()) return
        binding.apply {
            tvQRUrl.text = qrUrl
            ivQRCode.setImageDrawable(
                QRCodeGenerator.generateQRCode(requireContext(), qrUrl)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.let { sheet ->
                BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
            }
    }

    override fun getTheme() = R.style.ThemeOverlay_Ui3_BottomSheetDialog

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}