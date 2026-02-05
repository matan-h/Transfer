package com.matanh.transfer.ui.common

import androidx.appcompat.app.AppCompatActivity

object ActivityReloader {

    private val activities = mutableSetOf<AppCompatActivity>()

    fun register(activity: AppCompatActivity) {
        activities.add(activity)
    }

    fun unregister(activity: AppCompatActivity) {
        activities.remove(activity)
    }

    fun recreateAll() {
        activities.forEach { activity ->
            activity.recreate()
        }
    }
}