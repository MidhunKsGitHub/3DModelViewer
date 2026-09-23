package com.midhun.a3dmodelviewer.domain.repository

import android.net.Uri
import com.midhun.a3dmodelviewer.domain.model.ModelDescriptor
import com.midhun.a3dmodelviewer.domain.model.PartLabel

interface ModelCatalogRepository {
    fun bundledModels(): List<ModelDescriptor>
}

interface ModelDataRepository {
    fun importFromUri(uri: Uri): Result<ModelDescriptor>

    fun readBytes(descriptor: ModelDescriptor): ByteArray

    fun parseLabels(glbBytes: ByteArray): List<PartLabel>
}
