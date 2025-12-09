package com.w3n9.chengying

import android.app.Application
import com.w3n9.chengying.domain.repository.CursorRepository
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ChengyingApp : Application() {
    
    @Inject
    lateinit var cursorRepository: CursorRepository

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }


    }
}
