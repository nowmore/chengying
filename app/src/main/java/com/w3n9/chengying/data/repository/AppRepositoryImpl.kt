package com.w3n9.chengying.data.repository

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import com.w3n9.chengying.di.IODispatcher
import com.w3n9.chengying.domain.model.AppInfo
import com.w3n9.chengying.domain.repository.AppRepository
import com.w3n9.chengying.domain.repository.CursorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import rikka.shizuku.Shizuku
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cursorRepository: CursorRepository
) : AppRepository {

    private val _activePackageName = MutableStateFlow<String?>(null)
    override val activePackageName: StateFlow<String?> = _activePackageName.asStateFlow()

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
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val targetDisplay = displayManager.getDisplay(displayId)
            if (targetDisplay == null) {
                Timber.e("Cannot launch app, displayId $displayId not found.")
                return
            }

            // Force Landscape on the target display
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                executeShellCommand("wm user-rotation -d $displayId 1")
            }

            val displayContext = context.createDisplayContext(targetDisplay)
            val metrics = displayContext.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName) ?: run {
                Timber.w("No launch intent found for package: $packageName")
                return
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            //intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId

            // Set launch bounds to trick apps into landscape
            options.setLaunchBounds(Rect(0, 0, width, height))

            Timber.d("Launching app $packageName on display $displayId with bounds: $width x $height")
            context.startActivity(intent, options.toBundle())
            
            _activePackageName.value = packageName
            cursorRepository.setAppLaunched(true)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch app: $packageName")
        }
    }

    override fun closeActiveApp() {
        val packageName = _activePackageName.value
        if (packageName != null) {
            Timber.i("Force stopping package: $packageName")
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    val command = "am force-stop $packageName"
                    executeShellCommand(command)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to close app via Shizuku")
            }
        }
        
        _activePackageName.value = null
        cursorRepository.setAppLaunched(false)
    }

    override fun minimizeApp() {
        Timber.i("Minimizing app (Showing Desktop)")
        cursorRepository.setAppLaunched(false)
    }

    private fun executeShellCommand(command: String) {
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess", 
                Array<String>::class.java, 
                Array<String>::class.java, 
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            // process.waitFor() // Don't wait on main thread
        } catch (e: Exception) {
            Timber.e(e, "Shell command failed")
        }
    }
}
