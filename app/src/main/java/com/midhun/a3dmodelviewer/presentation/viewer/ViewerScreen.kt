package com.midhun.a3dmodelviewer.presentation.viewer

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midhun.a3dmodelviewer.ModelViewerApp
import com.midhun.a3dmodelviewer.domain.model.ModelWindow
import com.midhun.a3dmodelviewer.presentation.viewer.components.GlbSurfaceView
import com.midhun.a3dmodelviewer.presentation.viewer.components.LabelOverlayView
import kotlin.math.roundToInt

private val ToolbarHeight = 48.dp
private val ResizeHandleSize = 36.dp
private val CompactWidthThresholdDp = 300.dp

private val MinWindowWidthDp = 155.dp

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ViewerScreen() {
    val app = LocalContext.current.applicationContext as ModelViewerApp
    val viewModel: ViewerViewModel = viewModel(
        factory = ViewerViewModel.Factory(app.container)
    )
    val windows = viewModel.windows
    val uiState = viewModel.uiState
    var showPicker by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1C1E))
    ) {
        val density = LocalDensity.current
        val canvasWidthPx = with(density) { maxWidth.toPx() }
        val canvasHeightPx = with(density) { maxHeight.toPx() }
        val minWindowWidthPx = with(density) { MinWindowWidthDp.toPx() }

        val pickGlb = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.importFromUri(uri, canvasWidthPx, canvasHeightPx)
            }
        }

        LaunchedEffect(canvasWidthPx, canvasHeightPx, minWindowWidthPx) {
            viewModel.adaptToCanvas(canvasWidthPx, canvasHeightPx, minWindowWidthPx)
        }

        windows.forEach { window ->
            key(window.id) {
                val frontmost = windows.maxOfOrNull { it.zIndex } == window.zIndex
                ModelWindowCard(
                    state = window,
                    viewModel = viewModel,
                    windowCount = windows.size,
                    isFrontmost = frontmost,
                    onBringToFront = { viewModel.bringToFront(window.id) },
                    onTransform = { panX, panY, zoom ->
                        viewModel.transform(
                            id = window.id,
                            panX = panX,
                            panY = panY,
                            zoom = zoom,
                            canvasWidthPx = canvasWidthPx,
                            canvasHeightPx = canvasHeightPx,
                            minWidthPx = minWindowWidthPx
                        )
                    },
                    onResize = { deltaW, deltaH ->
                        viewModel.resize(
                            id = window.id,
                            deltaWidth = deltaW,
                            deltaHeight = deltaH,
                            canvasWidthPx = canvasWidthPx,
                            canvasHeightPx = canvasHeightPx,
                            minWidthPx = minWindowWidthPx
                        )
                    },
                    onToggleInteraction = { viewModel.toggleInteraction(window.id) },
                    onToggleLabels = { viewModel.toggleLabels(window.id) },
                    onClose = { viewModel.close(window.id) }
                )
            }
        }

        FloatingActionButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add model"
            )
        }

        Text(
            text = if (windows.isEmpty()) {
                "Tap + to load a GLB"
            } else {
                "${windows.size} model${if (windows.size == 1) "" else "s"} on screen"
            },
            color = Color(0xFFB0B3B8),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        if (uiState.isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text("Import failed") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) { Text("OK") }
                }
            )
        }

        if (showPicker) {
            AlertDialog(
                onDismissRequest = { showPicker = false },
                title = { Text("Add model") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.bundledModels.forEach { model ->
                            Text(
                                text = model.displayName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addBundledModel(
                                            model,
                                            canvasWidthPx,
                                            canvasHeightPx
                                        )
                                        showPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Text(
                            text = "Browse files…",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPicker = false
                                    pickGlb.launch(
                                        arrayOf(
                                            "model/gltf-binary",
                                            "model/gltf+json",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPicker = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ModelWindowCard(
    state: ModelWindow,
    viewModel: ViewerViewModel,
    windowCount: Int,
    isFrontmost: Boolean,
    onBringToFront: () -> Unit,
    onTransform: (panX: Float, panY: Float, zoom: Float) -> Unit,
    onResize: (deltaWidth: Float, deltaHeight: Float) -> Unit,
    onToggleInteraction: () -> Unit,
    onToggleLabels: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    var surfaceRef by remember(state.id) { mutableStateOf<GlbSurfaceView?>(null) }
    val useIcons = with(density) { state.widthPx < CompactWidthThresholdDp.toPx() }

    DisposableEffect(state.id) {
        onDispose {
            surfaceRef?.destroyRenderer()
            surfaceRef = null
        }
    }

    Box(
        modifier = Modifier
            .zIndex(state.zIndex)
            .offset { IntOffset(state.offsetX.roundToInt(), state.offsetY.roundToInt()) }
            .width(with(density) { state.widthPx.toDp() })
            .height(with(density) { state.heightPx.toDp() })
            .border(1.dp, Color(0xFF3C4048), RoundedCornerShape(10.dp))
            .background(Color(0xFF252830), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(ToolbarHeight))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E2228))
                    .then(
                        if (!state.interactionMode) {
                            Modifier.pointerInput(state.id) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    onBringToFront()
                                    onTransform(pan.x, pan.y, zoom)
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                AndroidView(
                    factory = { context ->
                        val overlay = LabelOverlayView(context).apply {
                            labelsVisible = state.labelsVisible
                            elevation = 8f
                        }
                        val descriptor = state.model
                        val surface = GlbSurfaceView(context).apply {
                            labelOverlay = overlay
                            start(
                                loadBytes = { viewModel.loadGlbBytes(descriptor) },
                                parseLabels = { bytes -> viewModel.parseLabels(bytes) }
                            )
                            setInteractionEnabled(state.interactionMode)
                            setLabelsVisible(state.labelsVisible)
                            setPerformanceHints(windowCount, isFrontmost)
                        }
                        surfaceRef = surface
                        android.widget.FrameLayout(context).apply {
                            setBackgroundColor(android.graphics.Color.parseColor("#1E2228"))
                            addView(
                                surface,
                                android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            addView(
                                overlay,
                                android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            tag = overlay
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { frame ->
                        val overlay = frame.tag as? LabelOverlayView
                        overlay?.labelsVisible = state.labelsVisible
                        surfaceRef?.setInteractionEnabled(state.interactionMode)
                        surfaceRef?.setLabelsVisible(state.labelsVisible)
                        surfaceRef?.setPerformanceHints(windowCount, isFrontmost)
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
                .fillMaxWidth()
                .height(ToolbarHeight)
                .background(Color(0xFF2E333C), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .then(
                    if (!state.interactionMode) {
                        Modifier.pointerInput(state.id) {
                            detectDragGestures(
                                onDragStart = { onBringToFront() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onTransform(dragAmount.x, dragAmount.y, 1f)
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!useIcons) {
                Text(
                    text = state.model.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            ToolbarAction(
                icon = Icons.Filled.TouchApp,
                label = "Interact",
                contentDescription = if (state.interactionMode) "Disable interaction" else "Enable interaction",
                active = state.interactionMode,
                iconsOnly = useIcons,
                onClick = {
                    onBringToFront()
                    onToggleInteraction()
                }
            )
            ToolbarAction(
                icon = Icons.Filled.Label,
                label = "Labels",
                contentDescription = if (state.labelsVisible) "Hide labels" else "Show labels",
                active = state.labelsVisible,
                iconsOnly = useIcons,
                onClick = {
                    onBringToFront()
                    onToggleLabels()
                }
            )
            ToolbarAction(
                icon = Icons.Filled.Close,
                label = "Close",
                contentDescription = "Close model",
                active = false,
                // Always icon at compact sizes so Close stays visible at min width.
                iconsOnly = useIcons,
                onClick = onClose
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .zIndex(11f)
                .size(ResizeHandleSize)
                .padding(4.dp)
                .background(Color(0xCC3A414D), RoundedCornerShape(8.dp))
                .pointerInput(state.id) {
                    detectDragGestures(
                        onDragStart = { onBringToFront() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onResize(dragAmount.x, dragAmount.y)
                        }
                    )
                }
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = "Resize window",
                tint = Color(0xFFD0D4DC),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = 90f }
            )
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    active: Boolean,
    iconsOnly: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) Color(0xFF7DDEA5) else Color(0xFF3A414D)
    val fg = if (active) Color(0xFF06210F) else Color.White
    val interaction = remember { MutableInteractionSource() }

    if (iconsOnly) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .background(bg, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .background(bg, RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}
