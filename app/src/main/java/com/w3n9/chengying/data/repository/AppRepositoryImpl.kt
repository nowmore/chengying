package com.w3n9.chengying.data.repository

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import com.w3n9.chengying.core.common.Result
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
import kotlinx.coroutines.withContext
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
                runCatching {
                    val packageName = resolveInfo.activityInfo.packageName
                    if (packageName == applicationContext.packageName) return@mapNotNull null

                    AppInfo(
                        label = resolveInfo.loadLabel(pm).toString(),
                        packageName = packageName,
                        icon = resolveInfo.loadIcon(pm)
                    )
                }.onFailure { e ->
                    Timber.e(e, "[AppRepositoryImpl::getInstalledApps] Error loading app: ${resolveInfo.activityInfo.packageName}")
                }.getOrNull()
            }
            .sortedBy { it.label }

        emit(apps)
    }.flowOn(ioDispatcher)

    override suspend fun launchApp(
        context: Context,
        packageName: String,
        displayId: Int
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            Timber.d("[AppRepositoryImpl::launchApp] packageName=$packageName, displayId=$displayId")

            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val targetDisplay = displayManager.getDisplay(displayId)
                ?: throw IllegalStateException("Display $displayId not found")

            val displayContext = context.createDisplayContext(targetDisplay)
            val metrics = displayContext.resources.displayMetrics

            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
                ?: throw IllegalStateException("No launch intent for package: $packageName")

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId

            Timber.d("[AppRepositoryImpl::launchApp] Launching with bounds: ${metrics.widthPixels}x${metrics.heightPixels}")
            context.startActivity(intent, options.toBundle())

            _activePackageName.value = packageName
            cursorRepository.setAppLaunched(true)
        }.fold(
            onSuccess = {
                Timber.i("[AppRepositoryImpl::launchApp] Successfully launched $packageName")
                Result.Success(Unit)
            },
            onFailure = { e ->
                Timber.e(e, "[AppRepositoryImpl::launchApp] Failed to launch $packageName")
                Result.Error(e)
            }
        )
    }

    override suspend fun closeActiveApp(): Result<Unit> = withContext(ioDispatcher) {
        val packageName = _activePackageName.value

        if (packageName == null) {
            _activePackageName.value = null
            cursorRepository.setAppLaunched(false)
            return@withContext Result.Success(Unit)
        }

        runCatching {
            Timber.i("[AppRepositoryImpl::closeActiveApp] Force stopping: $packageName")

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Shizuku permission not granted")
            }

            executeShellCommand("am force-stop $packageName")
            _activePackageName.value = null
            cursorRepository.setAppLaunched(false)
        }.fold(
            onSuccess = {
                Timber.i("[AppRepositoryImpl::closeActiveApp] Successfully closed $packageName")
                Result.Success(Unit)
            },
            onFailure = { e ->
                Timber.e(e, "[AppRepositoryImpl::closeActiveApp] Failed to close $packageName")
                _activePackageName.value = null
                cursorRepository.setAppLaunched(false)
                Result.Error(e)
            }
        )
    }

    override fun minimizeApp() {
        Timber.i("[AppRepositoryImpl::minimizeApp] Showing Desktop")
        cursorRepository.setAppLaunched(false)
    }

    override fun forceStopPackage(packageName: String) {
        Timber.i("[AppRepositoryImpl::forceStopPackage] Force stopping: $packageName")
        runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                executeShellCommand("am force-stop $packageName")
                Timber.d("[AppRepositoryImpl::forceStopPackage] Successfully stopped $packageName")
            } else {
                Timber.w("[AppRepositoryImpl::forceStopPackage] Shizuku permission not granted")
            }
        }.onFailure { e ->
            Timber.e(e, "[AppRepositoryImpl::forceStopPackage] Failed to stop $packageName")
        }
    }

    override fun sendBackEvent(displayId: Int) {
        Timber.i("[AppRepositoryImpl::sendBackEvent] Sending BACK event to display $displayId")
        runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                // KEYCODE_BACK = 4
                executeShellCommand("input -d $displayId keyevent 4")
                Timber.d("[AppRepositoryImpl::sendBackEvent] BACK event sent to display $displayId")
            } else {
                Timber.w("[AppRepositoryImpl::sendBackEvent] Shizuku permission not granted")
            }
        }.onFailure { e ->
            Timber.e(e, "[AppRepositoryImpl::sendBackEvent] Failed to send BACK event")
        }
    }

    override fun injectSwipe(displayId: Int, startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int) {
        Timber.d("[AppRepositoryImpl::injectSwipe] Swipe on display $displayId: ($startX,$startY) -> ($endX,$endY), duration=$durationMs")
        runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                // input swipe <x1> <y1> <x2> <y2> [duration(ms)]
                val cmd = "input -d $displayId swipe ${startX.toInt()} ${startY.toInt()} ${endX.toInt()} ${endY.toInt()} $durationMs"
                executeShellCommand(cmd)
                Timber.d("[AppRepositoryImpl::injectSwipe] Swipe injected successfully")
            } else {
                Timber.w("[AppRepositoryImpl::injectSwipe] Shizuku permission not granted")
            }
        }.onFailure { e ->
            Timber.e(e, "[AppRepositoryImpl::injectSwipe] Failed to inject swipe")
        }
    }

    private fun executeShellCommand(command: String) {
        val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        newProcessMethod.isAccessible = true
        newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
    }
}
