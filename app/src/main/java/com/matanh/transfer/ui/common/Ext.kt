package com.matanh.transfer.ui.common

import com.matanh.transfer.util.PreferenceUtil.getBoolean
import com.matanh.transfer.util.PreferenceUtil.getInt
import com.matanh.transfer.util.PreferenceUtil.getString

inline val String.booleanState
    get() = this.getBoolean()

inline val String.stringState
    get() = this.getString()

inline val String.intState
    get() = this.getInt()