package com.example.pintxomatch.ui.common.components

import java.util.Locale

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
