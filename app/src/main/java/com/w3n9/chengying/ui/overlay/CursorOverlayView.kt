package com.w3n9.chengying.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import timber.log.Timber

class CursorOverlayView(context: Context) : View(context) {
    
    private var cursorX = 0f
    private var cursorY = 0f
    private var isVisible = true
    
    private val cursorPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val cursorStrokePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val cursorPath = Path()
    
    init {
        setBackgroundColor(Color.TRANSPARENT)
        Timber.d("[CursorOverlayView] Initialized")
    }
    
    fun updateCursor(x: Float, y: Float, visible: Boolean) {
        cursorX = x
        cursorY = y
        isVisible = visible
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isVisible) return
        
        canvas.save()
        canvas.translate(cursorX, cursorY)
        
        // Draw cursor shape (arrow)
        cursorPath.reset()
        cursorPath.moveTo(0f, 0f)
        cursorPath.lineTo(0f, 60f)
        cursorPath.lineTo(24f, 42f)
        cursorPath.lineTo(60f, 42f)
        cursorPath.close()
        
        // Draw fill
        canvas.drawPath(cursorPath, cursorPaint)
        
        // Draw stroke
        canvas.drawPath(cursorPath, cursorStrokePaint)
        
        canvas.restore()
    }
}
