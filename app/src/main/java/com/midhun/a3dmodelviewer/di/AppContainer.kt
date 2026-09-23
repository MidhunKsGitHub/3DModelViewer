package com.midhun.a3dmodelviewer.di

import android.content.Context
import com.midhun.a3dmodelviewer.data.catalog.ModelCatalogRepositoryImpl
import com.midhun.a3dmodelviewer.data.model.ModelDataRepositoryImpl
import com.midhun.a3dmodelviewer.domain.repository.ModelCatalogRepository
import com.midhun.a3dmodelviewer.domain.repository.ModelDataRepository
import com.midhun.a3dmodelviewer.domain.usecase.GetBundledModelsUseCase
import com.midhun.a3dmodelviewer.domain.usecase.ImportModelFromUriUseCase
import com.midhun.a3dmodelviewer.domain.usecase.LoadModelBytesUseCase
import com.midhun.a3dmodelviewer.domain.usecase.ParseModelLabelsUseCase


class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val catalogRepository: ModelCatalogRepository = ModelCatalogRepositoryImpl()
    val modelDataRepository: ModelDataRepository = ModelDataRepositoryImpl(appContext)

    val getBundledModels = GetBundledModelsUseCase(catalogRepository)
    val importModelFromUri = ImportModelFromUriUseCase(modelDataRepository)
    val loadModelBytes = LoadModelBytesUseCase(modelDataRepository)
    val parseModelLabels = ParseModelLabelsUseCase(modelDataRepository)
}
