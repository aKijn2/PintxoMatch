package com.example.pintxomatch.ui.common.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeVisualsTest {

    @Test
    fun toBadgeDisplayLabel_mapsKnownBadgesToSpanishLabels() {
        assertEquals("Critico", "badge_2026W14_critic".toBadgeDisplayLabel())
        assertEquals("Estrella", "badge_2026W14_creator".toBadgeDisplayLabel())
        assertEquals("Ruta", "badge_2026W14_ruta".toBadgeDisplayLabel())
        assertEquals("Leyenda", "badge_2026W14_legend".toBadgeDisplayLabel())
    }

    @Test
    fun toUniqueBadgeDisplayLabels_collapsesRepeatedCategories() {
        val labels = listOf(
            "badge_2026W14_critic",
            "badge_2026W15_critic",
            "badge_2026W15_creator"
        ).toUniqueBadgeDisplayLabels()

        assertEquals(listOf("Critico", "Estrella"), labels)
    }
}
