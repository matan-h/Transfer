package com.matanh.transfer.server

import kotlinx.serialization.Serializable

@Serializable
data class RefreshSettings(
    val enabled: Boolean = true,
    val intervalSeconds: Int = 30
)
