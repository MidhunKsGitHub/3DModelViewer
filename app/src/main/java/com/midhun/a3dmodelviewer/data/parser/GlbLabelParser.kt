package com.midhun.a3dmodelviewer.data.parser

import com.midhun.a3dmodelviewer.domain.model.PartLabel
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GlbLabelParser {

    fun parse(glbBytes: ByteArray): List<PartLabel> {
        if (glbBytes.size < 20) return emptyList()
        val header = ByteBuffer.wrap(glbBytes, 0, 12).order(ByteOrder.LITTLE_ENDIAN)
        val magic = header.int
        if (magic != 0x46546C67) return emptyList() // glTF

        val chunkHeader = ByteBuffer.wrap(glbBytes, 12, 8).order(ByteOrder.LITTLE_ENDIAN)
        val chunkLength = chunkHeader.int
        val chunkType = chunkHeader.int
        if (chunkType != 0x4E4F534A) return emptyList() // JSON

        val jsonStart = 20
        if (jsonStart + chunkLength > glbBytes.size) return emptyList()

        val json = String(glbBytes, jsonStart, chunkLength, Charsets.UTF_8)
        val root = JSONObject(json)
        val nodes = root.optJSONArray("nodes") ?: return emptyList()

        val parent = IntArray(nodes.length()) { -1 }
        for (i in 0 until nodes.length()) {
            val children = nodes.optJSONObject(i)?.optJSONArray("children") ?: continue
            for (c in 0 until children.length()) {
                val child = children.optInt(c, -1)
                if (child in 0 until nodes.length()) parent[child] = i
            }
        }

        val labels = ArrayList<PartLabel>(8)
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            val extras = node.optJSONObject("extras") ?: continue
            if (!extras.has("prop")) continue
            val text = extras.optString("prop").trim()
            if (text.isEmpty()) continue
            val name = node.optString("name", "node_$i")
            val (x, y, z) = worldTranslation(nodes, parent, i)
            labels += PartLabel(
                nodeName = name,
                text = text,
                localX = x,
                localY = y,
                localZ = z
            )
        }
        return labels
    }

    private fun worldTranslation(
        nodes: JSONArray,
        parent: IntArray,
        index: Int
    ): FloatArray {
        var x = 0f
        var y = 0f
        var z = 0f
        var cur = index
        val chain = ArrayList<Int>(4)
        while (cur >= 0) {
            chain.add(cur)
            cur = parent[cur]
        }
        for (i in chain.lastIndex downTo 0) {
            val node = nodes.optJSONObject(chain[i]) ?: continue
            val t = node.optJSONArray("translation")
            if (t != null && t.length() >= 3) {
                x += t.optDouble(0, 0.0).toFloat()
                y += t.optDouble(1, 0.0).toFloat()
                z += t.optDouble(2, 0.0).toFloat()
            } else {
                val m = node.optJSONArray("matrix")
                if (m != null && m.length() >= 16) {
                    x += m.optDouble(12, 0.0).toFloat()
                    y += m.optDouble(13, 0.0).toFloat()
                    z += m.optDouble(14, 0.0).toFloat()
                }
            }
        }
        return floatArrayOf(x, y, z)
    }
}
