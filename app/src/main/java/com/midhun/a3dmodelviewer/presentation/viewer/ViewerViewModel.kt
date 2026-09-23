package com.midhun.a3dmodelviewer.presentation.viewer

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.midhun.a3dmodelviewer.di.AppContainer
import com.midhun.a3dmodelviewer.domain.model.ModelDescriptor
import com.midhun.a3dmodelviewer.domain.model.ModelWindow
import com.midhun.a3dmodelviewer.domain.model.PartLabel
import com.midhun.a3dmodelviewer.domain.usecase.GetBundledModelsUseCase
import com.midhun.a3dmodelviewer.domain.usecase.ImportModelFromUriUseCase
import com.midhun.a3dmodelviewer.domain.usecase.LoadModelBytesUseCase
import com.midhun.a3dmodelviewer.domain.usecase.ParseModelLabelsUseCase
import com.midhun.a3dmodelviewer.presentation.viewer.components.GlbSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerUiState(
    val bundledModels: List<ModelDescriptor> = emptyList(),
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)


class ViewerViewModel(
    private val getBundledModels: GetBundledModelsUseCase,
    private val importModelFromUri: ImportModelFromUriUseCase,
    private val loadModelBytes: LoadModelBytesUseCase,
    private val parseModelLabels: ParseModelLabelsUseCase
) : ViewModel() {

    val windows = mutableStateListOf<ModelWindow>()

    var uiState by mutableStateOf(
        ViewerUiState(bundledModels = getBundledModels())
    )
        private set

    private var nextZ by mutableFloatStateOf(1f)

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun addBundledModel(
        model: ModelDescriptor,
        canvasWidthPx: Float,
        canvasHeightPx: Float
    ) {
        addWindow(model, canvasWidthPx, canvasHeightPx)
    }

    fun importFromUri(
        uri: Uri,
        canvasWidthPx: Float,
        canvasHeightPx: Float
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isImporting = true, errorMessage = null)
            val result = withContext(Dispatchers.IO) { importModelFromUri(uri) }
            uiState = uiState.copy(isImporting = false)
            result
                .onSuccess { descriptor ->
                    addWindow(descriptor, canvasWidthPx, canvasHeightPx)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        errorMessage = error.message ?: "Failed to import model"
                    )
                }
        }
    }

    fun bringToFront(id: String) {
        nextZ += 1f
        replace(id) { it.copy(zIndex = nextZ) }
    }

    fun transform(
        id: String,
        panX: Float,
        panY: Float,
        zoom: Float,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        minWidthPx: Float
    ) {
        replace(id) { cur ->
            val minW = maxOf(canvasWidthPx * 0.28f, minWidthPx)
            val minH = canvasHeightPx * 0.22f
            val maxW = canvasWidthPx * 0.95f
            val maxH = canvasHeightPx * 0.85f
            val newW = GlbSurfaceView.clampSize(cur.widthPx * zoom, minW, maxW)
            val newH = GlbSurfaceView.clampSize(cur.heightPx * zoom, minH, maxH)
            val newX = (cur.offsetX + panX)
                .coerceIn(-newW * 0.4f, canvasWidthPx - newW * 0.2f)
            val newY = (cur.offsetY + panY)
                .coerceIn(0f, canvasHeightPx - newH * 0.2f)
            cur.copy(
                offsetX = newX,
                offsetY = newY,
                widthPx = newW,
                heightPx = newH,
                layoutCanvasWidth = canvasWidthPx,
                layoutCanvasHeight = canvasHeightPx
            )
        }
    }


    fun resize(
        id: String,
        deltaWidth: Float,
        deltaHeight: Float,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        minWidthPx: Float
    ) {
        replace(id) { cur ->
            val minW = maxOf(canvasWidthPx * 0.28f, minWidthPx)
            val minH = canvasHeightPx * 0.22f
            val maxW = canvasWidthPx * 0.95f
            val maxH = canvasHeightPx * 0.85f
            val newW = GlbSurfaceView.clampSize(cur.widthPx + deltaWidth, minW, maxW)
            val newH = GlbSurfaceView.clampSize(cur.heightPx + deltaHeight, minH, maxH)
            cur.copy(
                widthPx = newW,
                heightPx = newH,
                layoutCanvasWidth = canvasWidthPx,
                layoutCanvasHeight = canvasHeightPx
            )
        }
    }

    fun toggleInteraction(id: String) {
        replace(id) { it.copy(interactionMode = !it.interactionMode) }
    }

    fun toggleLabels(id: String) {
        replace(id) { it.copy(labelsVisible = !it.labelsVisible) }
    }

    fun close(id: String) {
        windows.removeAll { it.id == id }
    }

    fun adaptToCanvas(canvasWidthPx: Float, canvasHeightPx: Float, minWidthPx: Float = 0f) {
        if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) return
        val snapshot = windows.toList()
        if (snapshot.isEmpty()) return

        val needsAdapt = snapshot.any {
            it.layoutCanvasWidth <= 0f ||
                it.layoutCanvasHeight <= 0f ||
                kotlin.math.abs(it.layoutCanvasWidth - canvasWidthPx) > 1f ||
                kotlin.math.abs(it.layoutCanvasHeight - canvasHeightPx) > 1f
        }
        if (!needsAdapt) return

        val floorW = maxOf(canvasWidthPx * 0.28f, minWidthPx)
        windows.clear()
        snapshot.forEach { cur ->
            val prevW = cur.layoutCanvasWidth.takeIf { it > 0f } ?: canvasWidthPx
            val prevH = cur.layoutCanvasHeight.takeIf { it > 0f } ?: canvasHeightPx
            val widthFrac = (cur.widthPx / prevW).coerceIn(0.28f, 0.95f)
            val heightFrac = (cur.heightPx / prevH).coerceIn(0.22f, 0.85f)
            val xFrac = cur.offsetX / prevW
            val yFrac = cur.offsetY / prevH

            val newW = (canvasWidthPx * widthFrac).coerceIn(floorW, canvasWidthPx * 0.95f)
            val newH = (canvasHeightPx * heightFrac).coerceIn(canvasHeightPx * 0.22f, canvasHeightPx * 0.85f)
            val newX = (xFrac * canvasWidthPx).coerceIn(-newW * 0.4f, canvasWidthPx - newW * 0.2f)
            val newY = (yFrac * canvasHeightPx).coerceIn(0f, canvasHeightPx - newH * 0.2f)

            windows += cur.copy(
                offsetX = newX,
                offsetY = newY,
                widthPx = newW,
                heightPx = newH,
                layoutCanvasWidth = canvasWidthPx,
                layoutCanvasHeight = canvasHeightPx
            )
        }
    }


    fun loadGlbBytes(descriptor: ModelDescriptor): ByteArray = loadModelBytes(descriptor)

    fun parseLabels(bytes: ByteArray): List<PartLabel> = parseModelLabels(bytes)

    private fun addWindow(
        model: ModelDescriptor,
        canvasWidthPx: Float,
        canvasHeightPx: Float
    ) {
        // Wide enough that Interact / Labels / Close show with text on open.
        val w = canvasWidthPx * 0.92f
        val h = canvasHeightPx * 0.48f
        val stagger = (windows.size % 5) * 36f
        nextZ += 1f
        windows += ModelWindow(
            model = model,
            offsetX = 24f + stagger,
            offsetY = 72f + stagger,
            widthPx = w,
            heightPx = h,
            zIndex = nextZ,
            layoutCanvasWidth = canvasWidthPx,
            layoutCanvasHeight = canvasHeightPx
        )
    }

    private fun replace(id: String, transform: (ModelWindow) -> ModelWindow) {
        val index = windows.indexOfFirst { it.id == id }
        if (index >= 0) windows[index] = transform(windows[index])
    }

    class Factory(
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ViewerViewModel::class.java))
            return ViewerViewModel(
                getBundledModels = container.getBundledModels,
                importModelFromUri = container.importModelFromUri,
                loadModelBytes = container.loadModelBytes,
                parseModelLabels = container.parseModelLabels
            ) as T
        }
    }
}
