package com.midhun.a3dmodelviewer.domain.model

import java.util.UUID


data class ModelWindow(
    val id: String = UUID.randomUUID().toString(),
    val model: ModelDescriptor,
    val offsetX: Float,
    val offsetY: Float,
    val widthPx: Float,
    val heightPx: Float,
    val interactionMode: Boolean = false,
    val labelsVisible: Boolean = false,
    val zIndex: Float = 0f,
    val layoutCanvasWidth: Float = 0f,
    val layoutCanvasHeight: Float = 0f
)
