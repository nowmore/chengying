package com.w3n9.chengying.domain.model

import android.graphics.drawable.Drawable

data class TaskInfo(
    val taskId: Int,
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?
)
