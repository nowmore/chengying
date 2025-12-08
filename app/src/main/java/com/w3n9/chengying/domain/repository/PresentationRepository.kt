package com.w3n9.chengying.domain.repository

import android.view.Display

interface PresentationRepository {
    fun showPresentation(display: Display)
    fun dismissPresentation()
}
