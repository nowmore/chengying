package com.w3n9.chengying.core.config

object ScreenSaverConfig {
    /**
     * Screen saver timeout in milliseconds
     * After this duration of inactivity, the screen saver will activate:
     * - Main screen: Hide touchpad buttons
     * - Secondary screen: Hide cursor
     */
    const val TIMEOUT_MS = 5000L // 60 seconds
}
