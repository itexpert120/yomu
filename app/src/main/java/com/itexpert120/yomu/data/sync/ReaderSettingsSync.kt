package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Converts reader settings (the reading defaults and per-book overrides) to and from their synced
 * form: only [fields] travel, and a custom font travels as its family name (each device installs
 * and stores its own files).
 */
internal object ReaderSettingsSync {
    private const val CUSTOM_FONT = "customFont"

    val fields = SyncedFields(
        synced = setOf(
            "layout",
            "theme",
            "customBackground",
            "customText",
            "font",
            CUSTOM_FONT,
            "textAlign",
            "tapNavigation",
            "adaptPictures",
            "showFooter",
            "footerShowBattery",
            "footerShowClock",
            "footerShowProgress",
            "footerShowPagesLeft",
            "footerShowPageNumber",
            "footerShowTimeLeft",
            "footerShowBookTimeLeft",
            "showScrollbar",
            "immersiveChrome",
        ),
        // Screen and display behaviour, plus text size and spacing, which depend on the screen (a
        // tablet wants different values than a phone).
        deviceLocal = setOf(
            "useSystemBrightness",
            "brightness",
            "dimLevel",
            "keepScreenOn",
            "fontScale",
            "lineHeight",
            "pageMargins",
            "paragraphSpacing",
            "twoPageSpread",
            "pageCurl",
            "layoutFollowsScreen",
        ),
    )

    fun toPayload(settings: ReaderSettings): String {
        val full = SyncJson.encodeToJsonElement(settings).jsonObject
        val synced = buildJsonObject {
            full.forEach { (key, value) ->
                when (key) {
                    !in fields -> Unit
                    CUSTOM_FONT -> put(key, settings.customFont?.let { JsonPrimitive(it.family) } ?: JsonNull)
                    else -> put(key, value)
                }
            }
        }
        return SyncJson.encodeToString(JsonObject.serializer(), synced)
    }

    /** The family named by a synced payload, if any. */
    fun customFontFamily(payload: String): String? = runCatching {
        (SyncJson.parseToJsonElement(payload).jsonObject[CUSTOM_FONT] as? JsonPrimitive)?.contentOrNull
    }.getOrNull()

    /**
     * [payload] laid over [local]: synced fields come from the payload, device fields stay local.
     * The custom font resolves through [installedFont]; an uninstalled family falls back to the
     * bundled font so the reader never points at a missing file.
     */
    fun merge(local: ReaderSettings, payload: String, installedFont: (String) -> CustomFontRef?): ReaderSettings = merge(local, SyncJson.parseToJsonElement(payload).jsonObject, installedFont)

    fun merge(local: ReaderSettings, remote: JsonObject, installedFont: (String) -> CustomFontRef?): ReaderSettings {
        val base = SyncJson.encodeToJsonElement(local).jsonObject
        val merged = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            remote.forEach { (key, value) ->
                if (key in fields && key != CUSTOM_FONT) put(key, value)
            }
        }
        val font = (remote[CUSTOM_FONT] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let(installedFont)
        return SyncJson.decodeFromJsonElement<ReaderSettings>(merged).copy(customFont = font)
    }
}
