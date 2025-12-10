package com.w3n9.chengying.data.repository

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.w3n9.chengying.domain.model.TaskInfo
import com.w3n9.chengying.domain.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TaskRepository {

    private var targetDisplayId: Int = 0

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
            Timber.d("[TaskRepositoryImpl::init] HiddenApiBypass enabled")
        }
    }
    
    fun setTargetDisplayId(displayId: Int) {
        targetDisplayId = displayId
        Timber.d("[TaskRepositoryImpl::setTargetDisplayId] Set target display to: $displayId")
    }

    private val activityTaskManager: Any? by lazy {
        runCatching {
            Timber.d("[TaskRepositoryImpl::init] Initializing ActivityTaskManager, SDK=${Build.VERSION.SDK_INT}")
            
            if (!Shizuku.pingBinder()) {
                Timber.e("[TaskRepositoryImpl::init] Shizuku binder is not alive")
                return@runCatching null
            }
            
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Timber.e("[TaskRepositoryImpl::init] Shizuku permission not granted")
                return@runCatching null
            }
            
            val serviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "activity_task"
            } else {
                "activity"
            }
            
            Timber.d("[TaskRepositoryImpl::init] Getting system service: $serviceName")
            val binder = SystemServiceHelper.getSystemService(serviceName)
            if (binder == null) {
                Timber.e("[TaskRepositoryImpl::init] Failed to get system service: $serviceName")
                return@runCatching null
            }
            
            val wrappedBinder = ShizukuBinderWrapper(binder)
            Timber.d("[TaskRepositoryImpl::init] Binder wrapped successfully")
            
            val interfaceClassName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "android.app.IActivityTaskManager"
            } else {
                "android.app.IActivityManager"
            }
            
            val stubClassName = "$interfaceClassName\$Stub"
            val stubClass = Class.forName(stubClassName)
            
            val asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
            val manager = asInterfaceMethod.invoke(null, wrappedBinder)
            
            Timber.i("[TaskRepositoryImpl::init] ActivityTaskManager initialized successfully: ${manager?.javaClass?.name}")
            manager
        }.onFailure { e ->
            Timber.e(e, "[TaskRepositoryImpl::init] Failed to create ActivityTaskManager proxy")
        }.getOrNull()
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    override fun getRecentTasks(displayId: Int): Flow<List<TaskInfo>> = flow {
        val manager = activityTaskManager
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED || manager == null) {
            Timber.w("[TaskRepositoryImpl::getRecentTasks] Shizuku permission denied or manager null")
            emit(emptyList())
            return@flow
        }

        runCatching {
            val parcelableListClass = Class.forName("android.content.pm.ParceledListSlice")
            
            val getRecentTasksMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                manager.javaClass.getMethod(
                    "getRecentTasks",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            } else {
                manager.javaClass.getMethod(
                    "getRecentTasks",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            }
            

            val parceledListSlice = getRecentTasksMethod.invoke(manager, 100, 0, 0)
            
            if (parceledListSlice == null) {
                Timber.w("[TaskRepositoryImpl::getRecentTasks] getRecentTasks returned null ParceledListSlice")
                emit(emptyList())
                return@flow
            }
            
            val getListMethod = parcelableListClass.getMethod("getList")
            @Suppress("UNCHECKED_CAST")
            val recentTaskInfoList = getListMethod.invoke(parceledListSlice) as? List<*>
            
            if (recentTaskInfoList == null) {
                Timber.w("[TaskRepositoryImpl::getRecentTasks] ParceledListSlice.getList() returned null")
                emit(emptyList())
                return@flow
            }

            val displayTasks = recentTaskInfoList.mapNotNull { taskInfoObj ->
                runCatching {
                    val taskId = taskInfoObj?.javaClass?.getField("taskId")?.getInt(taskInfoObj) ?: -1
                    val taskDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        taskInfoObj?.javaClass?.getField("displayId")?.getInt(taskInfoObj) ?: -1
                    } else {
                        -1
                    }
                    val baseIntent = taskInfoObj?.javaClass?.getField("baseIntent")?.get(taskInfoObj) as? android.content.Intent
                    val packageName = baseIntent?.component?.packageName

                    if (taskDisplayId == displayId && packageName != null && packageName != context.packageName) {
                        Triple(taskId, packageName, baseIntent)
                    } else {
                        null
                    }
                }.onFailure { e ->
                    Timber.e(e, "[TaskRepositoryImpl::getRecentTasks] Failed to parse task info")
                }.getOrNull()
            }

            val tasks = displayTasks.mapNotNull { (taskId, packageName, _) ->
                runCatching {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    TaskInfo(
                        taskId = taskId,
                        packageName = packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        appIcon = pm.getApplicationIcon(appInfo)
                    )
                }.onFailure { e ->
                    Timber.w(e, "[TaskRepositoryImpl::getRecentTasks] App not found: $packageName")
                }.getOrNull()
            }

            Timber.i("[TaskRepositoryImpl::getRecentTasks] Emitting ${tasks.size} tasks for displayId=$displayId")
            emit(tasks)
        }.onFailure { e ->
            Timber.e(e, "[TaskRepositoryImpl::getRecentTasks] Failed to get recent tasks")
            emit(emptyList())
        }
    }

    @SuppressLint("PrivateApi")
    override fun switchToTask(taskId: Int) {
        val manager = activityTaskManager
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED || manager == null) {
            Timber.w("[TaskRepositoryImpl::switchToTask] Shizuku permission denied or manager null")
            return
        }

        runCatching {
            Timber.d("[TaskRepositoryImpl::switchToTask] Attempting to switch to task $taskId on display $targetDisplayId")
            
            val activityOptionsClass = Class.forName("android.app.ActivityOptions")
            val makeBasicMethod = activityOptionsClass.getMethod("makeBasic")
            val activityOptions = makeBasicMethod.invoke(null)
            
            val setLaunchDisplayIdMethod = activityOptionsClass.getMethod(
                "setLaunchDisplayId",
                Int::class.javaPrimitiveType
            )
            setLaunchDisplayIdMethod.invoke(activityOptions, targetDisplayId)
            
            val setTaskOverlayMethod = runCatching {
                activityOptionsClass.getMethod("setTaskOverlay", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            }.getOrNull()
            setTaskOverlayMethod?.invoke(activityOptions, true, true)
            
            val toBundleMethod = activityOptionsClass.getMethod("toBundle")
            val options = toBundleMethod.invoke(activityOptions) as android.os.Bundle
            
            val startActivityFromRecentsMethod = manager.javaClass.getMethod(
                "startActivityFromRecents",
                Int::class.javaPrimitiveType,
                android.os.Bundle::class.java
            )
            
            val result = startActivityFromRecentsMethod.invoke(manager, taskId, options)
            Timber.i("[TaskRepositoryImpl::switchToTask] startActivityFromRecents returned: $result for task $taskId")
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching {
                    val chengyingAm = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val chengyingTasks = chengyingAm.appTasks
                    chengyingTasks.firstOrNull()?.moveToFront()
                    Timber.d("[TaskRepositoryImpl::switchToTask] Moved Chengying back to front on main display")
                }.onFailure { e ->
                    Timber.w(e, "[TaskRepositoryImpl::switchToTask] Failed to move Chengying to front")
                }
            }, 100)
            
        }.onFailure { e ->
            Timber.e(e, "[TaskRepositoryImpl::switchToTask] Failed to switch task: $taskId")
        }
    }
}
