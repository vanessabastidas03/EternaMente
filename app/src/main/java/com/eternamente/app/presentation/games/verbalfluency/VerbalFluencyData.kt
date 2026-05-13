package com.eternamente.app.presentation.games.verbalfluency

import com.eternamente.app.presentation.games.engine.GameConfig
import com.eternamente.app.presentation.games.engine.GameResult
import com.eternamente.app.presentation.games.engine.MetricsSnapshot

enum class FluencyCategory(val displayName: String, val words: Set<String>) {
    ANIMALS("Animales", setOf(
        "perro","gato","pájaro","pez","conejo","rata","ratón","caballo","vaca","cerdo","oveja",
        "pollo","gallina","pato","ganso","pavo","paloma","loro","canario","tortuga","serpiente",
        "lagarto","rana","sapo","mariposa","abeja","mosca","araña","hormiga","escarabajo","caracol",
        "pulpo","calamar","tiburón","delfín","ballena","foca","pingüino","oso","lobo","zorro",
        "ciervo","conejo","liebre","ardilla","murciélago","mono","gorila","elefante","jirafa","cebra",
        "hipopótamo","rinoceronte","cocodrilo","caimán","camello","llama","alpaca","búfalo","bisonte","toro"
    )),
    FRUITS("Frutas", setOf(
        "manzana","pera","naranja","limón","lima","mandarina","pomelo","uva","fresa","fresón",
        "melocotón","durazno","albaricoque","ciruela","cereza","kiwi","mango","papaya","piña","plátano",
        "banana","melón","sandía","higo","dátil","ciruela","mora","arándano","frambuesa","grosella",
        "maracuyá","guayaba","lichi","carambola","tamarindo","coco","aguacate","oliva","membrillo","granada"
    )),
    CLOTHES("Ropa", setOf(
        "camisa","camiseta","pantalón","falda","vestido","chaqueta","abrigo","jersey","suéter","blusa",
        "chaleco","corbata","bufanda","guantes","calcetines","medias","zapatos","botas","zapatillas","sandalias",
        "sombrero","gorra","pañuelo","traje","pijama","bañador","biquini","impermeable","anorak","poncho",
        "vaqueros","leggins","shorts","bermudas","mono","peto","rebeca","cardigan","polo","ropa interior"
    )),
    FURNITURE("Muebles", setOf(
        "silla","mesa","sofá","cama","armario","estantería","escritorio","sillón","butaca","taburete",
        "banco","mesita","cómoda","tocador","aparador","vitrina","librería","perchero","espejo","lámpara",
        "alfombra","cortina","persiana","puerta","ventana","chimenea","nevera","lavadora","televisor","radio"
    ))
}

data class VerbalFluencyConfig(
    override val gameId: String       = GAME_ID,
    override val sessionId: String,
    override val userId: String,
    override val difficultyLevel: Int = 1,
    val category: FluencyCategory     = FluencyCategory.ANIMALS,
    val timeLimitSeconds: Int         = 60
) : GameConfig {
    companion object {
        const val GAME_ID = "verbal_fluency"
        fun forDifficulty(level: Int, sessionId: String, userId: String): VerbalFluencyConfig {
            val cat = FluencyCategory.entries[level.coerceIn(1,4) - 1]
            return VerbalFluencyConfig(sessionId=sessionId, userId=userId, difficultyLevel=level, category=cat, timeLimitSeconds=60)
        }
    }
}

data class VerbalFluencyResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val category: FluencyCategory, val validWords: List<String>,
    val repetitions: Int, val intrusions: Int, val wordsPerMinute: Float
) : GameResult

data class VerbalFluencyUiState(
    val category: FluencyCategory    = FluencyCategory.ANIMALS,
    val enteredWords: List<String>   = emptyList(),
    val currentInput: String         = "",
    val lastWordValid: Boolean?      = null,
    val timeLeft: Int                = 60
)
