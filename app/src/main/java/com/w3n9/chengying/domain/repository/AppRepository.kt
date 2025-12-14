package com.w3n9.chengying.domain.repository

import android.content.Context
import com.w3n9.chengying.core.common.Result
import com.w3n9.chengying.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AppRepository {
    val activePackageName: StateFlow<String?>
    
    fun getInstalledApps(): Flow<List<AppInfo>>
    
    suspend fun launchApp(context: Context, packageName: String, displayId: Int): Result<Unit>
    
    suspend fun closeActiveApp(): Result<Unit>
    
    fun forceStopPackage(packageName: String)
    
    fun minimizeApp()
}
