package com.midhun.a3dmodelviewer.presentation.viewer.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.midhun.a3dmodelviewer.filament.GlbModelRenderer


class LabelOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val projected = ArrayList<GlbModelRenderer.ProjectedLabel>(16)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        alpha = 200
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 20, 20, 24)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }
    private val tmpRect = RectF()

    init {
        setWillNotDraw(false)
        // Transparent so SurfaceView content shows through between labels.
        setBackgroundColor(Color.TRANSPARENT)
        visibility = INVISIBLE
    }

    var labelsVisible: Boolean = false
        set(value) {
            field = value
            visibility = if (value) VISIBLE else INVISIBLE
            if (!value) {
                projected.clear()
            }
            invalidate()
        }

    fun updateProjections(labels: List<GlbModelRenderer.ProjectedLabel>) {
        if (!labelsVisible) return
        projected.clear()
        projected.addAll(labels)
        invalidate()
    }

    /** Never consume touches — the SurfaceView underneath needs them in interaction mode. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    override fun onDraw(canvas: Canvas) {
        if (!labelsVisible || projected.isEmpty()) return

        val midX = width * 0.5f
        for (label in projected) {
            val anchorX = label.x
            val anchorY = label.y
            // Push label horizontally away from centre to reduce overlap with the mesh.
            val toRight = anchorX < midX
            val labelX = if (toRight) anchorX + 48f else anchorX - 48f
            val labelY = anchorY - 18f

            val textWidth = textPaint.measureText(label.text)
            val pad = 10f
            val left = if (toRight) labelX else labelX - textWidth
            tmpRect.set(
                left - pad,
                labelY - textPaint.textSize,
                left + textWidth + pad,
                labelY + pad
            )
            canvas.drawRoundRect(tmpRect, 8f, 8f, boxPaint)
            canvas.drawLine(anchorX, anchorY, if (toRight) tmpRect.left else tmpRect.right, labelY - 4f, linePaint)
            canvas.drawText(label.text, left, labelY, textPaint)
        }
    }
}
