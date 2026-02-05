package com.matanh.transfer.ui.activity.main

import android.content.Intent
import android.graphics.Outline
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.matanh.transfer.R
import com.matanh.transfer.databinding.ActivityMainNewBinding
import com.matanh.transfer.ui.MainViewModel
import com.matanh.transfer.ui.activity.settings.SettingsActivity
import com.matanh.transfer.ui.adapter.MainPagerAdapter
import com.matanh.transfer.ui.common.BaseActivity
import com.matanh.transfer.ui.components.InsetsHelper
import com.matanh.transfer.util.FileUtils
import com.matanh.transfer.util.FileUtils.getFileName
import com.matanh.transfer.util.ShareHandler
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : BaseActivity<ActivityMainNewBinding>(ActivityMainNewBinding::inflate) {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }
    private lateinit var shareHandler: ShareHandler
    private var filesToolbarHeight: Int = 0
    private var toolbarBottomMargin: Int = 0
    private var isPagerScrolling = false

    fun showFAB() {
        binding.addTutorialFab.show()
        binding.topToolbar.show()
    }

    fun hideFAB() {
        binding.addTutorialFab.hide()
        binding.topToolbar.hide()
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrollStateChanged(state: Int) {
            isPagerScrolling = state in listOf(
                ViewPager2.SCROLL_STATE_DRAGGING, ViewPager2.SCROLL_STATE_SETTLING
            )
        }

        override fun onPageScrolled(position: Int, offset: Float, positionOffsetPixels: Int) {
            if (isPagerScrolling) updateProjectsToolbar(position, offset)
        }

        override fun onPageSelected(position: Int) {
            updateFabState(position)
        }
    }

    private val uploadFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            handleFileUpload(uri)
        }

    override fun init() {
        shareHandler = ShareHandler(this, viewModel)

        updateHeaderPadding()
        ensureProjectsToolbarHeight()
        setupViewPagerAndTabs()
        setupLayoutHelpers()
        setupViews()
    }

    override fun initLogic() {
        viewModel.selectedFolderUri.observe(this) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    shareHandler.handleIntent(intent, uri)
                }
            } else {
                navigateToSettingsWithMessage(R.string.select_shared_folder_prompt)
            }
        }

        viewModel.files.observe(this) {
            binding.projectsCount.text = getString(R.string.files_count, viewModel.getFileCount())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch {
            viewModel.selectedFolderUri.value?.let {
                shareHandler.handleIntent(intent, it)
            }
        }
    }

    private fun handleFileUpload(sourceUri: Uri) {
        val folderUri = viewModel.selectedFolderUri.value ?: run {
            toast(getString(R.string.shared_folder_not_selected))
            return
        }

        lifecycleScope.launch {
            val fileName = getFileName(sourceUri)
            val copiedFile = FileUtils.copyUriToAppDir(
                this@MainActivity, sourceUri, folderUri, fileName
            )

            if (copiedFile?.exists() == true) {
                toast(getString(R.string.file_uploaded, copiedFile.name))
                viewModel.loadFiles(folderUri)
            } else {
                toast(getString(R.string.file_upload_failed))
            }
        }
    }

    private fun setupViews() = with(binding) {
        iconUserAvatar.setOnClickListener {
            it.hapticClick()
            showUserBottomSheet()
        }

        addTutorialFab.setOnClickListener {
            it.hapticClick()
            uploadFileLauncher.launch("*/*")
        }
    }

    private fun showUserBottomSheet() {
        val tag = HomeBottomSheet::class.java.simpleName
        if (!supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentByTag(tag) == null) {
            HomeBottomSheet().show(supportFragmentManager, tag)
        }
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPager.apply {
            adapter = MainPagerAdapter(this@MainActivity)
            registerOnPageChangeCallback(pageCallback)
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "CONFIG"
                1 -> "FILES"
                else -> ""
            }
        }.attach()
    }

    private fun updateProjectsToolbar(position: Int, offset: Float) {
        if (filesToolbarHeight <= 0 || position != 0) return

        val progress = offset.coerceIn(0f, 1f)

        binding.projectsToolbar.apply {
            visibility = View.VISIBLE
            alpha = progress

            (layoutParams as ViewGroup.MarginLayoutParams).apply {
                height = (filesToolbarHeight * progress).toInt()
                bottomMargin = (toolbarBottomMargin * progress).toInt()
            }.also { layoutParams = it }
        }
    }

    private fun updateFabState(position: Int) {
        when (position) {
            0 -> {
                binding.addTutorialFab.hide()
                setToolbarState(alpha = 0f, height = 0, margin = 0)
            }

            1 -> {
                binding.addTutorialFab.show()
                setToolbarState(
                    alpha = 1f,
                    height = filesToolbarHeight,
                    margin = toolbarBottomMargin,
                    visible = true
                )
            }
        }
    }

    private fun setToolbarState(
        alpha: Float, height: Int, margin: Int, visible: Boolean = false
    ) {
        binding.projectsToolbar.apply {
            if (visible) visibility = View.VISIBLE
            this.alpha = alpha

            (layoutParams as ViewGroup.MarginLayoutParams).apply {
                this.height = height
                bottomMargin = margin
            }.also { layoutParams = it }
        }
    }

    private fun setupLayoutHelpers() = with(binding) {
        val unselectedColor = getThemeColor(
            if (isNightMode()) com.google.android.material.R.attr.colorSurfaceVariant
            else com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        tabLayout.setTabTextColors(
            unselectedColor, getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        )

        appBarLayout.addOnOffsetChangedListener { appBar, offset ->
            headerView.alpha = 1f - abs(offset.toFloat() / appBar.totalScrollRange)
        }

        InsetsHelper.applyMargin(addTutorialFab, bottom = true)
        setupFrontLayoutOutline()
    }

    private fun setupFrontLayoutOutline() {
        val radius = (24 * resources.displayMetrics.density).toInt()

        binding.frontLayout.apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0, 0, view.width, view.height + radius, radius.toFloat()
                    )
                }
            }
            clipToOutline = true
        }
    }

    private fun ensureProjectsToolbarHeight() {
        binding.projectsToolbar.doOnLayout {
            filesToolbarHeight = it.measuredHeight
            toolbarBottomMargin = (it.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        }
    }

    private fun updateHeaderPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.headerView.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }

    private fun navigateToSettingsWithMessage(resId: Int) {
        toast(getString(resId), true)
        openActivity<SettingsActivity>()
    }
}