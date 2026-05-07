package com.example.pintxomatch.ui.common.components

import java.util.Locale

private val levelAchievementThresholds = linkedMapOf(
    "critico" to 1,
    "estrella" to 2,
    "ruta" to 3,
    "leyenda" to 4
)

fun String.toBadgeCategoryKey(): String {
    val normalized = trim().substringAfterLast('_').lowercase(Locale.getDefault())
    return when (normalized) {
        "critic", "critico" -> "critico"
        "creator", "estrella", "star" -> "estrella"
        "route", "ruta" -> "ruta"
        "legend", "leyenda" -> "leyenda"
        else -> normalized.ifBlank { "critico" }
    }
}

fun String.toBadgeDisplayLabel(): String {
    return when (toBadgeCategoryKey()) {
        "critico" -> "Critico"
        "estrella" -> "Estrella"
        "ruta" -> "Ruta"
        "leyenda" -> "Leyenda"
        else -> trim()
            .substringAfterLast('_')
            .replace('-', ' ')
            .lowercase(Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}

fun List<String>.toUniqueBadgeDisplayLabels(): List<String> {
    return asSequence()
        .map { it.toBadgeDisplayLabel() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}

fun levelAchievementBadgeIds(level: Int): List<String> {
    val safeLevel = level.coerceAtLeast(0)
    return levelAchievementThresholds.asSequence()
        .filter { (_, requiredLevel) -> safeLevel >= requiredLevel }
        .map { (category, requiredLevel) -> "badge_level_${requiredLevel}_$category" }
        .toList()
}

fun badgesWithLevelAchievements(
    badges: List<String>,
    level: Int
): List<String> {
    return (badges + levelAchievementBadgeIds(level)).distinct()
}

fun isBadgeCategoryUnlocked(
    categoryKey: String,
    badges: List<String>,
    level: Int
): Boolean {
    val normalizedCategory = categoryKey.toBadgeCategoryKey()
    return badgesWithLevelAchievements(badges, level)
        .map { it.toBadgeCategoryKey() }
        .contains(normalizedCategory)
}
