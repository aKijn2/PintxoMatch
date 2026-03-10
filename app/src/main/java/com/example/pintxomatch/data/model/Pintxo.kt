package com.example.pintxomatch.data.model

// Esta clase representa la información de cada pintxo que veremos en la app
data class Pintxo(
    val id: String,
    val name: String,          // Ej: "Gilda tradicional"
    val barName: String,       // Ej: "Bar Txepetxa"
    val location: String,      // Ej: "Parte Vieja, Donostia"
    val price: Double,         // Ej: 2.50
    val imageUrl: String,      // Aquí irá la foto del pintxo
    val averageRating: Double = 0.0,
    val ratingCount: Int = 0,
    val userRating: Int = 0
)