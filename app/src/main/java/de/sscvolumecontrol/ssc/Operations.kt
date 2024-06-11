package de.sscvolumecontrol.ssc

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

suspend fun getOscSchemaRecursive(device: Connection, path: List<String>? = null): List<String> {
    suspend fun getOscSchema(path: List<String>? = null): JsonArray {
        val payload = if (path == null) {
            JsonNull
        } else {
            JsonArray(listOf(wrapElement(path.joinToString("."), JsonNull)))
        }
        val response = device.send(wrapElement("osc.schema", payload))
        return unwrapElement("osc.schema", response).jsonArray
    }

    val knownSchema = mutableListOf<String>()
    if (path == null) {
        val rootSchemas = getOscSchema()
        rootSchemas.forEach { schema ->
            schema.jsonObject.keys.forEach { key ->
                if (schema.jsonObject[key] == JsonNull) {
                    knownSchema.add(key)
                } else {
                    val subtree = getOscSchemaRecursive(device, listOf(key))
                    knownSchema.addAll(subtree)
                }
            }
        }
    } else {
        val schemas = getOscSchema(path)
        schemas.forEach { schema ->
            val subtree = unwrapElement(path.joinToString("."), schema.jsonObject).jsonObject
            subtree.forEach { (key, value) ->
                if (value == JsonNull) {
                    knownSchema.add((path + key).joinToString("."))
                } else {
                    knownSchema.addAll(getOscSchemaRecursive(device, path + key))
                }
            }
        }
    }
    return knownSchema
}

suspend fun ping(device: Connection, value: JsonPrimitive): JsonPrimitive {
    val response = device.send(wrapElement("osc.ping", value))
    return unwrapElement("osc.ping", response) as JsonPrimitive
}

suspend fun limits(device: Connection, path: String): SscLimits {
    val payload = JsonArray(listOf(wrapElement(path, JsonNull)))
    val response = device.send(wrapElement("osc.limits", payload))
    val limitsWrapped = unwrapElement("osc.limits", response).jsonArray.get(0).jsonObject
    Log.i("SSC", "limits: $limitsWrapped")
    val limits = unwrapElement(path, limitsWrapped).jsonArray.get(0).jsonObject

    return SscLimits(
        stringToSscLimitsType(limits["type"]!!.jsonPrimitive.content),
        limits["min"]?.jsonPrimitive?.floatOrNull,
        limits["max"]?.jsonPrimitive?.floatOrNull,
        limits["inc"]?.jsonPrimitive?.floatOrNull,
        limits["units"]?.jsonPrimitive?.content,
        limits["desc"]?.jsonPrimitive?.content,
        limits["option"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
        limits["option_desc"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    )
}

suspend fun getLimitsForSchema(device: Connection, sscSchema: List<String>): Map<String, SscLimits> {
    return(sscSchema.filter { !it.startsWith("osc.") } .map {
        it to limits(device, it)
    }.toMap())
}