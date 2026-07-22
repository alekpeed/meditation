package com.meditation.core

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import org.junit.Test

class AmbienceMixesTest {
    @Test fun `a list of saved mixes round-trips through json`() {
        val json = Json { encodeDefaults = true }
        val mixes = listOf(
            SavedAmbienceMix("m1", "Rainy Night", listOf(AmbienceLayer("ambience-rain-steady-01", 0.7), AmbienceLayer("ambience-drone-low-01", 0.4))),
            SavedAmbienceMix("m2", "Empty Mix", emptyList()),
        )
        val serializer = ListSerializer(SavedAmbienceMix.serializer())
        val decoded = json.decodeFromString(serializer, json.encodeToString(serializer, mixes))
        assertEquals(mixes, decoded)
    }
}
