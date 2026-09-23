package com.midhun.a3dmodelviewer.data.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.midhun.a3dmodelviewer.data.parser.GlbLabelParser
import com.midhun.a3dmodelviewer.domain.model.ModelDescriptor
import com.midhun.a3dmodelviewer.domain.model.ModelLocation
import com.midhun.a3dmodelviewer.domain.model.PartLabel
import com.midhun.a3dmodelviewer.domain.repository.ModelDataRepository
import java.io.File
import java.util.UUID

class ModelDataRepositoryImpl(
    private val appContext: Context
) : ModelDataRepository {

    private val importDir: File by lazy {
        File(appContext.filesDir, "imported_models").also { it.mkdirs() }
    }

    override fun importFromUri(uri: Uri): Result<ModelDescriptor> = runCatching {
        val displayName = queryDisplayName(uri) ?: "Custom model.glb"
        val safeName = displayName
            .substringAfterLast('/')
            .ifBlank { "model.glb" }
        val ext = if (safeName.contains('.')) {
            safeName.substringAfterLast('.')
        } else {
            "glb"
        }
        require(ext.equals("glb", ignoreCase = true) || ext.equals("gltf", ignoreCase = true)) {
            "Only .glb / .gltf files are supported"
        }

        val dest = File(importDir, "${UUID.randomUUID()}.$ext")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected file")

        require(dest.length() > 0L) { "Selected file is empty" }

        ModelDescriptor(
            id = "file:${dest.absolutePath}",
            displayName = safeName.removeSuffix(".$ext").ifBlank { "Custom model" },
            location = ModelLocation.File(dest.absolutePath)
        )
    }

    override fun readBytes(descriptor: ModelDescriptor): ByteArray {
        return when (val location = descriptor.location) {
            is ModelLocation.Asset -> {
                appContext.assets.open(location.assetPath).use { it.readBytes() }
            }
            is ModelLocation.File -> {
                File(location.absolutePath).readBytes()
            }
        }
    }

    override fun parseLabels(glbBytes: ByteArray): List<PartLabel> =
        GlbLabelParser.parse(glbBytes)

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = appContext.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }
}
