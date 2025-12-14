package com.w3n9.chengying.domain.repository

import com.w3n9.chengying.domain.model.TaskInfo
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getRecentTasks(displayId: Int): Flow<List<TaskInfo>>
    fun switchToTask(taskId: Int)
    fun getPackagesOnDisplay(displayId: Int): List<String>
}
