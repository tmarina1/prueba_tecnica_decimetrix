package com.example.prueba_tecnica_decimetrix.model

data class FavoritePoint(
    val id: Int = 0,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val isAlertPoint: Boolean = false
)