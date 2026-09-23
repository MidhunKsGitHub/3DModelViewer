package com.midhun.a3dmodelviewer.filament

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.util.concurrent.atomic.AtomicInteger


object SharedFilament {

    @Volatile
    private var engine: Engine? = null
    private var materialProvider: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var sharedIndirectLight: IndirectLight? = null

    private val retainCount = AtomicInteger(0)
    private var nativesLoaded = false

    private fun ensureNativesLoaded() {
        if (nativesLoaded) return
        // Loads filament-jni / filament-utils-jni / gltfio-jni.
        Utils.init()
        nativesLoaded = true
    }

    fun acquire(): Handles {
        synchronized(this) {
            ensureNativesLoaded()
            if (engine == null) {
                val eng = Engine.create()
                val materials = UbershaderProvider(eng)
                val assets = AssetLoader(eng, materials, EntityManager.get())
                val resources = ResourceLoader(eng, true)
                // Diffuse-only ambient via SH — no cubemap IBL (cheaper, still lit PBR).
                val irradiance = floatArrayOf(
                    1.0f, 1.0f, 1.0f
                )
                val ibl = IndirectLight.Builder()
                    .irradiance(1, irradiance)
                    .intensity(40_000.0f)
                    .build(eng)

                engine = eng
                materialProvider = materials
                assetLoader = assets
                resourceLoader = resources
                sharedIndirectLight = ibl
            }
            retainCount.incrementAndGet()
            return Handles(
                engine = engine!!,
                assetLoader = assetLoader!!,
                resourceLoader = resourceLoader!!,
                indirectLight = sharedIndirectLight!!
            )
        }
    }

    fun release() {
        synchronized(this) {
            if (retainCount.decrementAndGet() > 0) return
            sharedIndirectLight?.let { engine?.destroyIndirectLight(it) }
            sharedIndirectLight = null
            resourceLoader?.destroy()
            resourceLoader = null
            assetLoader?.destroy()
            assetLoader = null
            materialProvider?.destroyMaterials()
            materialProvider?.destroy()
            materialProvider = null
            engine?.destroy()
            engine = null
        }
    }

    data class Handles(
        val engine: Engine,
        val assetLoader: AssetLoader,
        val resourceLoader: ResourceLoader,
        val indirectLight: IndirectLight
    )
}
