package com.midhun.a3dmodelviewer.presentation.viewer.components

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.TextureView
import com.midhun.a3dmodelviewer.domain.model.PartLabel
import com.midhun.a3dmodelviewer.filament.GlbLoadGate
import com.midhun.a3dmodelviewer.filament.GlbModelRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max


class GlbSurfaceView(context: Context) : TextureView(context), Choreographer.FrameCallback {

    private var renderer: GlbModelRenderer? = null
    private var choreographer: Choreographer? = null
    private var lastFrameNanos = 0L
    private var loopPosted = false
    private var destroyed = false

    private var interacting = false
    private var labelsOn = false
    private var frontmost = true
    private var windowCount = 1
    private var settleFrames = SETTLE_FRAMES_AFTER_LOAD

    private val projectedScratch = ArrayList<GlbModelRenderer.ProjectedLabel>(16)
    var labelOverlay: LabelOverlayView? = null

    init {
        isOpaque = true
    }

    fun start(
        loadBytes: () -> ByteArray,
        parseLabels: (ByteArray) -> List<PartLabel>
    ) {
        if (renderer != null) return
        destroyed = false
        val r = GlbModelRenderer(this)
        renderer = r
        choreographer = Choreographer.getInstance()
        settleFrames = SETTLE_FRAMES_AFTER_LOAD
        wakeLoop()

        Thread({
            try {
                GlbLoadGate.withPermit {
                    val bytes = loadBytes()
                    val labels = parseLabels(bytes)
                    val buffer = ByteBuffer.allocateDirect(bytes.size)
                        .order(ByteOrder.nativeOrder())
                        .put(bytes)
                        .flip()
                    post {
                        if (destroyed || renderer !== r) return@post
                        r.loadModelGlb(buffer, labels)
                        settleFrames = SETTLE_FRAMES_AFTER_LOAD
                        wakeLoop()
                    }
                }
            } catch (_: Exception) {
                // Leave empty scene; user can close the window.
            }
        }, "glb-load").start()
    }

    fun setInteractionEnabled(enabled: Boolean) {
        if (interacting == enabled) {
            renderer?.setInteractionEnabled(enabled)
            return
        }
        interacting = enabled
        renderer?.setInteractionEnabled(enabled)
        if (enabled) {
            settleFrames = Int.MAX_VALUE / 4
            wakeLoop()
        } else {
            settleFrames = SETTLE_FRAMES_AFTER_IDLE
            wakeLoop()
        }
    }

    fun setLabelsVisible(visible: Boolean) {
        if (labelsOn == visible) {
            labelOverlay?.labelsVisible = visible
            return
        }
        labelsOn = visible
        labelOverlay?.labelsVisible = visible
        // Project a few frames so connectors line up after toggle.
        settleFrames = max(settleFrames, SETTLE_FRAMES_AFTER_LABELS)
        wakeLoop()
    }


    fun setPerformanceHints(totalWindows: Int, isFrontmost: Boolean) {
        val count = totalWindows.coerceAtLeast(1)
        val countChanged = count != windowCount
        val frontChanged = isFrontmost != frontmost
        windowCount = count
        frontmost = isFrontmost
        if (countChanged || frontChanged) {
            if (isFrontmost || interacting) {
                settleFrames = max(settleFrames, SETTLE_FRAMES_AFTER_IDLE)
                wakeLoop()
            }
        }
    }

    fun destroyRenderer() {
        destroyed = true
        loopPosted = false
        choreographer?.removeFrameCallback(this)
        choreographer = null
        renderer?.destroy()
        renderer = null
        surfaceTextureListener = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        settleFrames = max(settleFrames, SETTLE_FRAMES_AFTER_IDLE)
        wakeLoop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        loopPosted = false
        if (destroyed) return

        val r = renderer
        if (r == null) return

        val loading = r.isLoading()
        val needsContinuous = interacting || loading
        val interval = GlbLoadGate.frameIntervalNs(windowCount, frontmost, interacting)

        if (frameTimeNanos - lastFrameNanos >= interval) {
            lastFrameNanos = frameTimeNanos
            r.render(frameTimeNanos)

            val overlay = labelOverlay
            if (overlay != null && labelsOn) {
                r.projectLabels(projectedScratch)
                overlay.updateProjections(projectedScratch)
            }

            if (!needsContinuous && settleFrames > 0) {
                settleFrames--
            }
        }

        val keepGoing = !destroyed && (needsContinuous || settleFrames > 0)

        if (keepGoing) {
            wakeLoop()
        }
        // else: freeze — TextureView keeps the last rendered frame (big win with N models).
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (interacting) {
            wakeLoop()
        }
        val handled = renderer?.onTouchEvent(event) == true
        return handled || super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        destroyRenderer()
        super.onDetachedFromWindow()
    }

    private fun wakeLoop() {
        if (destroyed || loopPosted) return
        val c = choreographer ?: return
        loopPosted = true
        c.postFrameCallback(this)
    }

    companion object {
        private const val SETTLE_FRAMES_AFTER_LOAD = 12
        private const val SETTLE_FRAMES_AFTER_IDLE = 4
        private const val SETTLE_FRAMES_AFTER_LABELS = 8

        fun clampSize(value: Float, min: Float, max: Float): Float =
            max(min, kotlin.math.min(max, value))
    }
}
