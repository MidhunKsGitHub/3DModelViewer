package com.midhun.a3dmodelviewer.domain.usecase

import android.net.Uri
import com.midhun.a3dmodelviewer.domain.model.ModelDescriptor
import com.midhun.a3dmodelviewer.domain.model.PartLabel
import com.midhun.a3dmodelviewer.domain.repository.ModelCatalogRepository
import com.midhun.a3dmodelviewer.domain.repository.ModelDataRepository

class GetBundledModelsUseCase(
    private val catalogRepository: ModelCatalogRepository
) {
    operator fun invoke(): List<ModelDescriptor> = catalogRepository.bundledModels()
}

class ImportModelFromUriUseCase(
    private val modelDataRepository: ModelDataRepository
) {
    operator fun invoke(uri: Uri): Result<ModelDescriptor> =
        modelDataRepository.importFromUri(uri)
}

class LoadModelBytesUseCase(
    private val modelDataRepository: ModelDataRepository
) {
    operator fun invoke(descriptor: ModelDescriptor): ByteArray =
        modelDataRepository.readBytes(descriptor)
}

class ParseModelLabelsUseCase(
    private val modelDataRepository: ModelDataRepository
) {
    operator fun invoke(glbBytes: ByteArray): List<PartLabel> =
        modelDataRepository.parseLabels(glbBytes)
}
