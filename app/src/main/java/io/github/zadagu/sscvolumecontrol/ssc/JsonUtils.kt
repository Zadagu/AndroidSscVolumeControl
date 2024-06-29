package io.github.zadagu.sscvolumecontrol.ssc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Wraps a JSON element in a JSON object at the specified path.
 * @param path The path is a dot-separated list of keys. like "osc.schema"
 */
fun wrapElement(path: String, value: JsonElement): JsonObject {
    if (path.isEmpty()) {
        throw IllegalArgumentException("Path must not be empty")
    }
    val pathPartsReversed = path.split(".").reversed()

    var currentValue = value
    for (pathPart in pathPartsReversed) {
        currentValue = buildJsonObject {
            put(pathPart, currentValue)
        }
    }
    return currentValue as JsonObject
}

/**
 * Unwraps a JSON element from a JSON object at the specified path.
 * @param path The path is a dot-separated list of keys. like "osc.schema"
 */
fun unwrapElement(path: String, value: JsonObject): JsonElement {
    if (path.isEmpty()) {
        throw IllegalArgumentException("Path must not be empty")
    }
    val pathParts = path.split(".")

    var currentValue: JsonElement = value
    for (pathPart in pathParts) {
        currentValue = (currentValue as JsonObject)[pathPart] ?: throw IllegalArgumentException("Path $path not found in object ${value.toString()}")
    }
    return currentValue
}
