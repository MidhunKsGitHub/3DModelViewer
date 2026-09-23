package com.midhun.a3dmodelviewer.filament

import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View as FilamentView
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.GestureDetector
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.max
import com.google.android.filament.utils.scale
import com.google.android.filament.utils.translation
import com.google.android.filament.utils.transpose
import com.midhun.a3dmodelviewer.domain.model.PartLabel
import java.nio.Buffer

/**
 * Per-window Filament renderer that shares [SharedFilament]'s Engine.
 * Hosted on a [TextureView] so stacked windows composite correctly.
 */
class GlbModelRenderer(
    private val hostView: TextureView
) {
    private val handles = SharedFilament.acquire()
    private val engine = handles.engine

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(hostView.context)

    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    val view: FilamentView = engine.createView()
    val camera: Camera = engine.createCamera(engine.entityManager.create())

    private var swapChain: SwapChain? = null
    private var asset: FilamentAsset? = null

    private val cameraManipulator: Manipulator = Manipulator.Builder()
        .targetPosition(DEFAULT_TARGET.x, DEFAULT_TARGET.y, DEFAULT_TARGET.z)
        .viewport(1, 1)
        .build(Manipulator.Mode.ORBIT)

    private val gestureDetector = GestureDetector(hostView, cameraManipulator)

    @Entity
    private val sunLight: Int = EntityManager.get().create()

    private val readyRenderables = IntArray(128)
    private val eyePos = DoubleArray(3)
    private val target = DoubleArray(3)
    private val upward = DoubleArray(3)

    private val worldMatrix = FloatArray(16)
    private val rootWorldMatrix = FloatArray(16)
    private val viewMatrix = DoubleArray(16)
    private val projMatrix = DoubleArray(16)
    private val camModelMatrix = DoubleArray(16)

    private var interactionEnabled = false
    private var destroyed = false
    private var fittedToUnitCube = false
    private var loadComplete = false

    var partLabels: List<PartLabel> = emptyList()
        private set


    private var labelEntities: IntArray = IntArray(0)

    init {
        GlbLoadGate.liveWindowCount.incrementAndGet()
        view.scene = scene
        view.camera = camera
        camera.setExposure(16f, 1f / 125f, 100f)

        // Low-end focused view settings: no MSAA / AO / bloom / shadows / post.
        view.multiSampleAntiAliasingOptions = FilamentView.MultiSampleAntiAliasingOptions().apply {
            enabled = false
        }
        view.ambientOcclusionOptions = FilamentView.AmbientOcclusionOptions().apply {
            enabled = false
        }
        view.bloomOptions = FilamentView.BloomOptions().apply {
            enabled = false
        }
        view.renderQuality = FilamentView.RenderQuality().apply {
            hdrColorBuffer = FilamentView.QualityLevel.MEDIUM
        }
        // Post-processing is one of the costliest paths with multiple TextureViews.
        view.isPostProcessingEnabled = false

        scene.indirectLight = handles.indirectLight
        // Neutral clear — no skybox cube map (saves GPU bandwidth).
        scene.skybox = null
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0.12f, 0.13f, 0.15f, 1f)
        }

        // Single directional light (two lights roughly doubles lighting work).
        val (r, g, b) = Colors.cct(6_500.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(110_000.0f)
            .direction(0.35f, -1.0f, -0.25f)
            .castShadows(false)
            .build(engine, sunLight)
        scene.addEntity(sunLight)

        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(hostView)
    }

    fun setInteractionEnabled(enabled: Boolean) {
        interactionEnabled = enabled
    }

    fun isLoading(): Boolean = !loadComplete

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled) return false
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun loadModelGlb(buffer: Buffer, labels: List<PartLabel>) {
        destroyModel()
        fittedToUnitCube = false
        loadComplete = false
        partLabels = labels
        labelEntities = IntArray(0)
        asset = handles.assetLoader.createAsset(buffer)
        asset?.let { loaded ->
            handles.resourceLoader.asyncBeginLoad(loaded)
            loaded.releaseSourceData()
            resolveLabelEntities(loaded)
        }
    }

    fun render(frameTimeNanos: Long) {
        if (destroyed || !uiHelper.isReadyToRender) return

        if (!loadComplete) {
            handles.resourceLoader.asyncUpdateLoad()
        }
        asset?.let { populateScene(it) }

        cameraManipulator.getLookAt(eyePos, target, upward)
        camera.lookAt(
            eyePos[0], eyePos[1], eyePos[2],
            target[0], target[1], target[2],
            upward[0], upward[1], upward[2]
        )

        val chain = swapChain ?: return
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }


    fun projectLabels(out: MutableList<ProjectedLabel>) {
        out.clear()
        val current = asset ?: return
        if (partLabels.isEmpty()) return
        resolveLabelEntities(current)

        val vp = view.viewport
        if (vp.width <= 0 || vp.height <= 0) return

        // Overlay is laid out in view pixels; never use the GPU buffer size here.
        val screenW = hostView.width.takeIf { it > 0 } ?: vp.width
        val screenH = hostView.height.takeIf { it > 0 } ?: vp.height

        camera.getModelMatrix(camModelMatrix)
        invert4x4(camModelMatrix, viewMatrix)
        camera.getCullingProjectionMatrix(projMatrix)

        val tm = engine.transformManager
        val rootInstance = tm.getInstance(current.root)
        if (rootInstance != 0) {
            tm.getWorldTransform(rootInstance, rootWorldMatrix)
        } else {
            java.util.Arrays.fill(rootWorldMatrix, 0f)
            rootWorldMatrix[0] = 1f
            rootWorldMatrix[5] = 1f
            rootWorldMatrix[10] = 1f
            rootWorldMatrix[15] = 1f
        }

        for (i in partLabels.indices) {
            val label = partLabels[i]
            var wx: Float
            var wy: Float
            var wz: Float

            val entity = labelEntities.getOrElse(i) { 0 }
            val ti = if (entity != 0) tm.getInstance(entity) else 0
            if (ti != 0) {
                tm.getWorldTransform(ti, worldMatrix)
                wx = worldMatrix[12]
                wy = worldMatrix[13]
                wz = worldMatrix[14]
            } else {
                val lx = label.localX
                val ly = label.localY
                val lz = label.localZ
                wx = rootWorldMatrix[0] * lx + rootWorldMatrix[4] * ly + rootWorldMatrix[8] * lz + rootWorldMatrix[12]
                wy = rootWorldMatrix[1] * lx + rootWorldMatrix[5] * ly + rootWorldMatrix[9] * lz + rootWorldMatrix[13]
                wz = rootWorldMatrix[2] * lx + rootWorldMatrix[6] * ly + rootWorldMatrix[10] * lz + rootWorldMatrix[14]
            }

            val vx = viewMatrix[0] * wx + viewMatrix[4] * wy + viewMatrix[8] * wz + viewMatrix[12]
            val vy = viewMatrix[1] * wx + viewMatrix[5] * wy + viewMatrix[9] * wz + viewMatrix[13]
            val vz = viewMatrix[2] * wx + viewMatrix[6] * wy + viewMatrix[10] * wz + viewMatrix[14]
            val vw = viewMatrix[3] * wx + viewMatrix[7] * wy + viewMatrix[11] * wz + viewMatrix[15]

            val cx = projMatrix[0] * vx + projMatrix[4] * vy + projMatrix[8] * vz + projMatrix[12] * vw
            val cy = projMatrix[1] * vx + projMatrix[5] * vy + projMatrix[9] * vz + projMatrix[13] * vw
            val cw = projMatrix[3] * vx + projMatrix[7] * vy + projMatrix[11] * vz + projMatrix[15] * vw
            if (cw <= 0.0001) continue

            val ndcX = (cx / cw).toFloat()
            val ndcY = (cy / cw).toFloat()
            out += ProjectedLabel(
                text = label.text,
                x = (ndcX * 0.5f + 0.5f) * screenW,
                y = (1f - (ndcY * 0.5f + 0.5f)) * screenH
            )
        }
    }

    private fun resolveLabelEntities(asset: FilamentAsset) {
        if (partLabels.isEmpty()) return
        if (labelEntities.size == partLabels.size && labelEntities.any { it != 0 }) return
        labelEntities = IntArray(partLabels.size) { index ->
            asset.getFirstEntityByName(partLabels[index].nodeName)
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        uiHelper.detach()
        destroyModel()
        engine.destroyEntity(sunLight)
        EntityManager.get().destroy(sunLight)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        EntityManager.get().destroy(camera.entity)
        GlbLoadGate.liveWindowCount.decrementAndGet()
        SharedFilament.release()
    }

    private fun destroyModel() {
        handles.resourceLoader.asyncCancelLoad()
        handles.resourceLoader.evictResourceData()
        asset?.let { loaded ->
            scene.removeEntities(loaded.entities)
            handles.assetLoader.destroyAsset(loaded)
        }
        asset = null
        labelEntities = IntArray(0)
        fittedToUnitCube = false
        loadComplete = false
    }

    private fun populateScene(asset: FilamentAsset) {
        val rcm = engine.renderableManager
        var count: Int
        while (true) {
            count = asset.popRenderables(readyRenderables)
            if (count == 0) break
            for (i in 0 until count) {
                val ri = rcm.getInstance(readyRenderables[i])
                // Disable contact shadows / keep cheap.
                rcm.setCastShadows(ri, false)
                rcm.setReceiveShadows(ri, false)
            }
            scene.addEntities(readyRenderables.copyOf(count))
        }
        scene.addEntities(asset.lightEntities)
        if (!fittedToUnitCube && handles.resourceLoader.asyncGetLoadProgress() >= 1.0f) {
            transformToUnitCube(asset)
            fittedToUnitCube = true
            loadComplete = true
            // Align camera viewport with the actual view so the model sits centered.
            val w = hostView.width
            val h = hostView.height
            if (w > 0 && h > 0) {
                view.viewport = Viewport(0, 0, w, h)
                cameraManipulator.setViewport(w, h)
                updateCameraProjection()
            }
        }
    }

    private fun transformToUnitCube(asset: FilamentAsset) {
        val tm = engine.transformManager
        val centerArr = asset.boundingBox.center
        val halfArr = asset.boundingBox.halfExtent
        var center = Float3(centerArr[0], centerArr[1], centerArr[2])
        val halfExtent = Float3(halfArr[0], halfArr[1], halfArr[2])
        val maxExtent = 2.0f * max(halfExtent)
        if (maxExtent <= 0f) return
        val scaleFactor = 2.0f / maxExtent
        center -= DEFAULT_TARGET / scaleFactor
        val transform = scale(Float3(scaleFactor)) * translation(-center)
        tm.setTransform(tm.getInstance(asset.root), transpose(transform).toFloatArray())
    }

    private fun updateCameraProjection() {
        val width = view.viewport.width
        val height = view.viewport.height
        if (width == 0 || height == 0) return
        val aspect = width.toDouble() / height.toDouble()
        camera.setLensProjection(28.0, aspect, 0.05, 1000.0)
    }

    private inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, hostView.display)
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            view.viewport = Viewport(0, 0, width, height)
            cameraManipulator.setViewport(width, height)
            updateCameraProjection()
        }
    }

    data class ProjectedLabel(val text: String, val x: Float, val y: Float)

    companion object {
        private val DEFAULT_TARGET = Float3(0f, 0f, -4f)


        private fun invert4x4(m: DoubleArray, out: DoubleArray) {
            val inv = DoubleArray(16)
            inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
                m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
            inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
                m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
            inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
                m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
            inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
                m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
            inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
                m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
            inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
                m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
            inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
                m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
            inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
                m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
            inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
                m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
            inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
                m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
            inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
                m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
            inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
                m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
            inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
                m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
            inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
                m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
            inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
                m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
            inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
                m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

            var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
            if (kotlin.math.abs(det) < 1e-12) return
            det = 1.0 / det
            for (i in 0 until 16) {
                out[i] = inv[i] * det
            }
        }
    }
}
