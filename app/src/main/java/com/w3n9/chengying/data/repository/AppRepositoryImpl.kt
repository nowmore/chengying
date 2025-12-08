package com.w3n9.chengying.data.repository

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.w3n9.chengying.di.IODispatcher
import com.w3n9.chengying.domain.model.AppInfo
import com.w3n9.chengying.domain.repository.AppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : AppRepository {

    override fun getInstalledApps(): Flow<List<AppInfo>> = flow {
        val pm = applicationContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val apps = pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                try {
                    val packageName = resolveInfo.activityInfo.packageName
                    if (packageName == applicationContext.packageName) return@mapNotNull null
                    
                    AppInfo(
                        label = resolveInfo.loadLabel(pm).toString(),
                        packageName = packageName,
                        icon = resolveInfo.loadIcon(pm)
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error loading app info for ${resolveInfo.activityInfo.packageName}")
                    null
                }
            }
            .sortedBy { it.label }
            
        emit(apps)
    }.flowOn(ioDispatcher)

    override fun launchApp(context: Context, packageName: String, displayId: Int) {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                // Reverting to the more stable flag combination based on logs.
                // CLEAR_TASK was causing process death for some apps.
                // MULTIPLE_TASK is the standard way to request a new instance on a different display.
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    options.launchDisplayId = displayId
                }

                Timber.d("Launching app $packageName on display $displayId with flags: 0x${Integer.toHexString(intent.flags)}")
                context.startActivity(intent, options.toBundle())
                
            } else {
                Timber.w("No launch intent found for package: $packageName")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch app: $packageName")
        }
    }
}
