package com.matanh.transfer.ui.activity.aboutus

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import com.matanh.transfer.BuildConfig
import com.matanh.transfer.databinding.ActivityAboutUsBinding
import com.matanh.transfer.ui.adapter.TeamAdapter
import com.matanh.transfer.ui.common.BaseActivity
import com.matanh.transfer.ui.items.TeamMember
import com.matanh.transfer.util.Url

class AboutActivity : BaseActivity<ActivityAboutUsBinding>(ActivityAboutUsBinding::inflate) {

    private val teamAdapter = TeamAdapter { member ->
        openUrl(member.social)
    }

    override fun init() {
        setupToolbar()
        setupVersion()
        setupRecyclerView()
    }

    override fun initLogic() {
        setupClicks()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            it.hapticClick()
            onBackPressedDispatcher.onBackPressed()
        }

        setUpDivider()
    }

    @SuppressLint("SetTextI18n")
    private fun setupVersion() {
        binding.textViewVersion.text = "v${BuildConfig.VERSION_NAME}"
    }

    private fun setupRecyclerView() {
        binding.teamMembersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AboutActivity)
            adapter = teamAdapter
        }

        teamAdapter.submitList(
            listOf(
                TeamMember(
                    name = "matan h",
                    imageUrl = "https://avatars.githubusercontent.com/u/56131718?v=4",
                    social = "https://github.com/matan-h"
                )
            )
        )
    }

    private fun setupClicks() {
        binding.layoutChangelog.setOnClickListener {
            it.hapticClick()
            openUrl(Url.REPO_CHANGE_LOG_URL)
        }
        binding.tvGithub.setOnClickListener {
            it.hapticClick()
            openUrl(Url.REPO_URL)
        }
        binding.tvCoffee.setOnClickListener {
            it.hapticClick()
            openUrl(Url.DEV_COFFEE_URL)
        }
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    private fun setUpDivider() {
        binding.divider.hide()
        binding.container.isVerticalScrollBarEnabled = false
        binding.container.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            binding.divider.visibility = if (scrollY > 0) VISIBLE else GONE
        }
    }
}