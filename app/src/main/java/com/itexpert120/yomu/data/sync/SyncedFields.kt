package com.itexpert120.yomu.data.sync

/**
 * Which fields of one settings group travel between devices. Every field is declared either
 * [synced] (model name to wire name) or [deviceLocal]; a test fails when a field is in neither, so
 * adding a setting forces the choice. [legacy] wire fields are retired but still sent with their
 * payload default, because older app versions require them; they are ignored on receive.
 */
internal class SyncedFields(
    val synced: Map<String, String>,
    val deviceLocal: Set<String>,
    val legacy: Set<String> = emptySet(),
) {
    /** For groups whose wire names are the model's field names. */
    constructor(synced: Set<String>, deviceLocal: Set<String>) : this(synced.associateWith { it }, deviceLocal)

    init {
        require(synced.keys.none { it in deviceLocal }) { "A field is either synced or device-local" }
    }

    operator fun contains(field: String) = field in synced

    /** Model fields declared neither synced nor device-local. */
    fun undeclared(fields: Collection<String>) = fields.filterNot { it in synced || it in deviceLocal }

    /** Every field name the payload carries on the wire. */
    val wireFields: Set<String> get() = synced.values.toSet() + legacy
}
