package com.example.pintxomatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pintxomatch.Pintxo

@Composable
fun PintxoCard(pintxo: Pintxo) {
    // Card es el contenedor principal que le da esa forma de "tarjeta" con sombra
    Card(
        modifier = Modifier
            .fillMaxWidth() // Ocupa todo el ancho disponible
            .height(450.dp) // Altura de la tarjeta
            .padding(16.dp), // Margen exterior
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp) // Sombra
    ) {
        // Column organiza las cosas de arriba a abajo
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. ESPACIO PARA LA FOTO (Descargada de internet con control de errores)
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(pintxo.imageUrl)
                    .crossfade(true) // Hace que la imagen aparezca suavemente
                    .build(),
                contentDescription = "Foto de ${pintxo.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.LightGray), // Fondo gris mientras carga
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                // Si la imagen falla, mostramos un aviso
                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_dialog_alert)
            )

            // 2. TEXTOS CON LA INFORMACIÓN (Nombre, bar, precio)
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = pintxo.name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${pintxo.barName} - ${pintxo.location}",
                    color = Color.DarkGray,
                    fontSize = 16.sp
                )
                Text(
                    text = String.format("%.2f \u20ac", pintxo.price),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary, // Color principal de tu app
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}