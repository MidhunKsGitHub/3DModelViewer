package com.midhun.a3dmodelviewer.domain.model


sealed class ModelLocation {
    data class Asset(val assetPath: String) : ModelLocation()
    data class File(val absolutePath: String) : ModelLocation()
}


data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val location: ModelLocation
)
