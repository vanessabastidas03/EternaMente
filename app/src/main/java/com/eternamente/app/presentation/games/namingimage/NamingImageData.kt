package com.eternamente.app.presentation.games.namingimage

import com.eternamente.app.presentation.games.engine.GameConfig
import com.eternamente.app.presentation.games.engine.GameResult
import com.eternamente.app.presentation.games.engine.MetricsSnapshot

data class ImageItem(
    val emoji: String,
    val correctName: String,
    val category: String,
    val semanticDistractors: List<String>,
    val unrelatedDistractor: String
)

val IMAGE_DATABASE = listOf(
    ImageItem("🐶","Perro","animal",listOf("Gato","Conejo"),"Mesa"),
    ImageItem("🐱","Gato","animal",listOf("Perro","Ratón"),"Llave"),
    ImageItem("🐟","Pez","animal",listOf("Pulpo","Cangrejo"),"Silla"),
    ImageItem("🦋","Mariposa","insecto",listOf("Abeja","Araña"),"Libro"),
    ImageItem("🍎","Manzana","fruta",listOf("Pera","Naranja"),"Perro"),
    ImageItem("🍌","Plátano","fruta",listOf("Naranja","Uva"),"Reloj"),
    ImageItem("🥕","Zanahoria","verdura",listOf("Lechuga","Tomate"),"Perro"),
    ImageItem("🚗","Coche","vehículo",listOf("Moto","Camión"),"Pez"),
    ImageItem("✈️","Avión","vehículo",listOf("Barco","Tren"),"Mesa"),
    ImageItem("🚲","Bicicleta","vehículo",listOf("Moto","Patinete"),"Luna"),
    ImageItem("🌳","Árbol","planta",listOf("Flor","Hoja"),"Coche"),
    ImageItem("🌸","Flor","planta",listOf("Árbol","Hoja"),"Llave"),
    ImageItem("📱","Teléfono","tecnología",listOf("Tablet","TV"),"Árbol"),
    ImageItem("⌚","Reloj","accesorio",listOf("Anillo","Collar"),"Moto"),
    ImageItem("📚","Libro","objeto",listOf("Cuaderno","Lápiz"),"Gato"),
    ImageItem("🎸","Guitarra","instrumento",listOf("Piano","Flauta"),"Silla"),
    ImageItem("⚽","Pelota","deporte",listOf("Raqueta","Red"),"Llave"),
    ImageItem("🏠","Casa","construcción",listOf("Edificio","Puente"),"Plátano"),
    ImageItem("🍕","Pizza","comida",listOf("Hamburguesa","Pasta"),"Barco"),
    ImageItem("🦁","León","animal",listOf("Tigre","Leopardo"),"Piano")
)

data class NamingImageConfig(
    override val gameId: String = GAME_ID,
    override val sessionId: String,
    override val userId: String,
    override val difficultyLevel: Int = 1,
    val imagesPerSession: Int = 15,
    val timeLimitPerImageMs: Long = 8_000L
) : GameConfig {
    companion object {
        const val GAME_ID = "naming_image"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = NamingImageConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            imagesPerSession = if (level <= 2) 10 else 15,
            timeLimitPerImageMs = when(level) { 1->10_000L; 2->8_000L; 3->7_000L; 4->6_000L; else->5_000L }
        )
    }
}

data class NamingImageResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val correctAnswers: Int, val totalImages: Int,
    val semanticErrors: Int, val unrelatedErrors: Int, val omissions: Int
) : GameResult

data class NamingImageUiState(
    val emoji: String = "", val options: List<String> = emptyList(),
    val selectedIndex: Int? = null, val isCorrect: Boolean? = null,
    val correctName: String = "", val imageIndex: Int = 0,
    val totalImages: Int = 15, val timeRemainingMs: Long = 8_000L
)
