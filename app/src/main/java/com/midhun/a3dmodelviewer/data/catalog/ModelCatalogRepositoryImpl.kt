package com.midhun.a3dmodelviewer.data.catalog

import com.midhun.a3dmodelviewer.domain.model.ModelDescriptor
import com.midhun.a3dmodelviewer.domain.model.ModelLocation
import com.midhun.a3dmodelviewer.domain.repository.ModelCatalogRepository

class ModelCatalogRepositoryImpl : ModelCatalogRepository {

    override fun bundledModels(): List<ModelDescriptor> = BundledModels.all
}


object BundledModels {
    val all: List<ModelDescriptor> = listOf(
        ModelDescriptor("asset:Bulb", "Bulb", ModelLocation.Asset("models/Bulb.glb")),
        ModelDescriptor("asset:Fiagena", "Fiagena", ModelLocation.Asset("models/Fiagena.glb")),
        ModelDescriptor("asset:Lungs", "Lungs", ModelLocation.Asset("models/Lungs.glb")),
        ModelDescriptor("asset:Microscope", "Microscope", ModelLocation.Asset("models/Microscope.glb")),
        ModelDescriptor("asset:SolarSystem", "Solar System", ModelLocation.Asset("models/solarsystem.glb"))
    )
}
