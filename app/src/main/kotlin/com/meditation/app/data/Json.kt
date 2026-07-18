package com.meditation.app.data

import kotlinx.serialization.json.Json

/** Single lenient Json instance shared by Room converters, asset loading, and export. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}
