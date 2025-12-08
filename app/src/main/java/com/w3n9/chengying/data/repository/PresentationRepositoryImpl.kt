package com.w3n9.chengying.data.repository

import android.app.Presentation
import android.content.Context
import android.view.Display
import com.w3n9.chengying.domain.repository.PresentationRepository
import com.w3n9.chengying.ui.presentation.SecondScreenPresentation
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Inject

@ActivityScoped
class PresentationRepositoryImpl @Inject constructor(
    @ActivityContext private val context: Context
) : PresentationRepository {

    private var presentation: Presentation? = null

    override fun showPresentation(display: Display) {
        // Idempotency Check: If presentation is already showing on the correct display, do nothing.
        if (presentation?.isShowing == true && presentation?.display?.displayId == display.displayId) {
            Timber.d("[PresentationRepositoryImpl] Presentation already showing on display ${display.displayId}. Ignoring request.")
            return
        }

        // If we are here, we need a new presentation. Dismiss any old one.
        dismissPresentation()

        try {
            Timber.d("[PresentationRepositoryImpl] Creating and showing new presentation on display: ${display.displayId}")
            presentation = SecondScreenPresentation(context, display).apply {
                setOnDismissListener {
                    Timber.d("[PresentationRepositoryImpl] Presentation on display ${display.displayId} was dismissed by the system.")
                    if (presentation == this) {
                         presentation = null
                    }
                }
                show()
            }
        } catch (e: Exception) {
            Timber.e(e, "[PresentationRepositoryImpl] Failed to show presentation")
        }
    }

    override fun dismissPresentation() {
        presentation?.let {
            Timber.d("[PresentationRepositoryImpl] Dismissing presentation programmatically.")
            if (it.isShowing) {
                it.dismiss()
            }
        }
        presentation = null
    }
}
