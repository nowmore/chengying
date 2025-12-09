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
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import java.lang.reflect.Proxy
import javax.inject.Inject
import javax.inject.Singleton

// Minimal IActivityManager interface for our dynamic proxy
interface IActivityManager : android.os.IInterface {
    fun getRecentTasks(maxNum: Int, flags: Int, userId: Int): List<ActivityManager.RecentTaskInfo>
    fun moveTaskToFront(taskId: Int, flags: Int, options: android.os.Bundle?)
}

@Singleton
class TaskRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TaskRepository {

    private val activityManager: IActivityManager? by lazy {
        try {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"))
            val serviceManagerClass = Class.forName("android.app.IActivityManager\$Stub")
            val asInterfaceMethod = serviceManagerClass.getMethod("asInterface", IBinder::class.java)
            val realActivityManager = asInterfaceMethod.invoke(null, binder)

            Proxy.newProxyInstance(
                IActivityManager::class.java.classLoader,
                arrayOf(IActivityManager::class.java)
            ) { _, method, args ->
                try {
                    val finalArgs = args ?: emptyArray()
                    realActivityManager?.javaClass?.getMethod(method.name, *method.parameterTypes)
                        ?.invoke(realActivityManager, *finalArgs)
                } catch (e: Exception) {
                    Timber.e(e, "Dynamic proxy invocation failed for ${method.name}")
                    if (method.returnType == Void.TYPE || method.returnType.isPrimitive) {
                        null
                    } else {
                        throw e
                    }
                }
            } as IActivityManager
        } catch (e: Exception) {
            Timber.e(e, "Failed to create IActivityManager proxy")
            null
        }
    }

    @SuppressLint("NewApi")
    override fun getRecentTasks(displayId: Int): Flow<List<TaskInfo>> = flow {
        val am = activityManager
        // Guard against API level and permission issues
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED || am == null) {
            emit(emptyList())
            return@flow
        }

        try {
            val allTasks = am.getRecentTasks(100, 0, 0)

            val displayTasks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                allTasks.filter { task ->
                    try {
                        val field = task.javaClass.getField("displayId")
                        val taskDisplayId = field.getInt(task)
                        taskDisplayId == displayId
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get task displayId")
                        false
                    }
                }
            } else {
                Timber.w ("task has no displayId, get emptyList")
                emptyList()
            }

            val tasks = displayTasks
                .filter { it.baseIntent.component?.packageName != context.packageName }
                .mapNotNull { task ->
                    val packageName = task.baseIntent.component?.packageName ?: return@mapNotNull null
                    val pm = context.packageManager
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        TaskInfo(
                            taskId = task.taskId,
                            packageName = packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            appIcon = pm.getApplicationIcon(appInfo)
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        null // App might have been uninstalled
                    }
                }
            emit(tasks)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get recent tasks")
            emit(emptyList())
        }
    }

    override fun switchToTask(taskId: Int) {
        val am = activityManager
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED || am == null) {
            return
        }

        try {
            am.moveTaskToFront(taskId, 0, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to switch task")
        }
    }
}
