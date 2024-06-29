package io.github.zadagu.sscvolumecontrol.ssc

import kotlinx.serialization.Serializable

enum class SscLimitsType {
    Number,
    Str,
    Boolean,
    Container
}

fun stringToSscLimitsType(type: String): SscLimitsType {
    return when (type) {
        "Number" -> SscLimitsType.Number
        "String" -> SscLimitsType.Str
        "Boolean" -> SscLimitsType.Boolean
        "Container" -> SscLimitsType.Container
        else -> throw IllegalArgumentException("Unknown type $type")
    }
}

@Serializable
data class SscLimits(
    /**
     * The type of the value.
     */
    val type: SscLimitsType,
    /**
     * The minimum valid value.
     */
    val min: Float? = null,
    /**
     * The maximum valid value.
     */
    val max: Float? = null,
    /**
     * The recommended user interface increment value.
     */
    val inc: Float? = null,
    /**
     * String describing value units (preferably SI).
     */
    val units: String? = null,
    /**
     * Descriptive text, meant for display to the user.
     */
    val desc: String? = null,
    /**
     * Array of all allowed options for the value.
     */
    val option: List<String> = emptyList(),
    /**
     * Array with description text relating to the option values.
     */
    val option_desc: List<String> = emptyList()
) {
}