package com.w3n9.chengying.domain.repository

import android.content.Context
import com.w3n9.chengying.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getInstalledApps(): Flow<List<AppInfo>>
    fun launchApp(context: Context, packageName: String, displayId: Int)
}
