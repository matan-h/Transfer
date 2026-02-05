package com.matanh.transfer.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.matanh.transfer.ui.activity.main.fragment.ConfigFragment
import com.matanh.transfer.ui.activity.main.fragment.FilesFragment

class MainPagerAdapter(
    activity: FragmentActivity
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {

        return when (position) {
            0 -> ConfigFragment()
            1 -> FilesFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}