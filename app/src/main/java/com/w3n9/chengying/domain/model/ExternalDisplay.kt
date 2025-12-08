package com.w3n9.chengying.domain.model

data class ExternalDisplay(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val state: DisplayState = DisplayState.UNKNOWN
)

enum class DisplayState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN
}
