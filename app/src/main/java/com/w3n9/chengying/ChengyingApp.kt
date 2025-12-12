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
        
        // Register activity lifecycle callbacks to handle cleanup
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                Timber.d("[ChengyingApp] Activity created: ${activity.javaClass.simpleName}")
            }
            
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            
            override fun onActivityDestroyed(activity: android.app.Activity) {
                Timber.i("[ChengyingApp] Activity destroyed: ${activity.javaClass.simpleName}")
                // This will be called even when process is being killed
                if (activity is MainActivity) {
                    Timber.i("[ChengyingApp] MainActivity destroyed, attempting to clean up presentation")
                }
            }
        })
    }
}
