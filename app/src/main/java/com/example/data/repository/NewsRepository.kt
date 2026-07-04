package com.example.data.repository

import com.example.BuildConfig
import com.example.data.database.*
import com.example.data.network.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NewsRepository(private val newsDao: NewsDao) {

    // --- Cached News FLOWS ---
    val allCachedNews: Flow<List<NewsArticle>> = newsDao.getAllCachedNews()

    fun getCachedNewsByCategory(category: String): Flow<List<NewsArticle>> {
        return newsDao.getCachedNewsByCategory(category)
    }

    // --- Bookmarks FLOWS ---
    val allBookmarks: Flow<List<Bookmark>> = newsDao.getAllBookmarks()

    fun isBookmarked(url: String): Flow<Boolean> = newsDao.isBookmarked(url)

    suspend fun addBookmark(article: NewsArticle) = withContext(Dispatchers.IO) {
        val bookmark = Bookmark(
            url = article.url,
            title = article.title,
            source = article.source,
            summary = article.summary,
            date = article.date,
            category = article.category,
            imageQuery = article.imageQuery,
            timestamp = System.currentTimeMillis()
        )
        newsDao.insertBookmark(bookmark)
    }

    suspend fun removeBookmark(url: String) = withContext(Dispatchers.IO) {
        newsDao.deleteBookmarkByUrl(url)
    }

    // --- Reading History FLOWS ---
    val readingHistory: Flow<List<ReadingHistory>> = newsDao.getReadingHistory()

    suspend fun addToHistory(article: NewsArticle) = withContext(Dispatchers.IO) {
        val history = ReadingHistory(
            url = article.url,
            title = article.title,
            category = article.category,
            timestamp = System.currentTimeMillis()
        )
        newsDao.insertReadingHistory(history)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        newsDao.clearReadingHistory()
    }

    // --- User Preferences FLOWS ---
    val userPreferences: Flow<UserPreferences?> = newsDao.getUserPreferencesFlow()

    suspend fun getPreferences(): UserPreferences? = withContext(Dispatchers.IO) {
        var prefs = newsDao.getUserPreferences()
        if (prefs == null) {
            prefs = UserPreferences()
            newsDao.insertUserPreferences(prefs)
        }
        prefs
    }

    suspend fun savePreferences(preferences: UserPreferences) = withContext(Dispatchers.IO) {
        newsDao.insertUserPreferences(preferences)
    }

    // --- API Network Logic: Fetch News with Google Search Grounding ---
    suspend fun fetchLatestNews(categories: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("API Key is missing. Configure GEMINI_API_KEY in Secrets."))
        }

        try {
            // Fetch news in parallel for all selected categories
            val jobs = categories.map { category ->
                async {
                    fetchCategoryNewsFromApi(apiKey, category)
                }
            }

            val allFetchedArticles = jobs.awaitAll().flatten()
            if (allFetchedArticles.isNotEmpty()) {
                // Clear and insert fresh news to keep caching clean
                newsDao.clearAllCachedNews()
                newsDao.insertArticles(allFetchedArticles)
                Result.success(Unit)
            } else {
                Result.failure(Exception("No se pudieron obtener noticias en este momento."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchCategoryNewsFromApi(apiKey: String, category: String): List<NewsArticle> {
        val systemPrompt = """
            Eres un recolector de noticias inteligente. Tu tarea es buscar noticias reales de hoy usando Google Search en español sobre la categoría '$category'.
            Genera de 4 a 5 noticias reales y de actualidad. Para cada noticia, debes devolver un objeto JSON estricto con los siguientes campos de texto (ninguno puede ser nulo o vacío):
            - title: El título de la noticia en español.
            - source: El medio de comunicación de origen (ej. El País, El Mundo, BBC Mundo, etc.).
            - summary: Un resumen riguroso de la noticia en español de 2 a 3 oraciones completas.
            - date: El momento de publicación relativo (ej. 'Hace 2 horas', 'Hoy', 'Hace 4 horas').
            - url: El enlace web real u original estimado del artículo.
            - category: El nombre exacto de la categoría '$category'.
            - imageQuery: Un término de búsqueda de imagen en inglés para ilustrar la noticia (ej. 'artificial intelligence chatgpt' o 'soccer ball in net').

            Devuelve un array de objetos JSON únicamente. El array debe estar formateado en un bloque de código markdown ```json. No incluyas explicaciones previas ni posteriores.
        """.trimIndent()

        val prompt = "Busca las últimas noticias reales en español sobre '$category' para hoy."

        val request = GenerateContentRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.5f),
            tools = listOf(Tool(googleSearch = GoogleSearchTool())),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val parsed = RetrofitClient.parseNewsList(jsonText)
            parsed?.map {
                NewsArticle(
                    url = it.url,
                    title = it.title,
                    source = it.source,
                    summary = it.summary,
                    date = it.date,
                    category = it.category,
                    imageQuery = it.imageQuery,
                    timestamp = System.currentTimeMillis()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // --- AI News Detailed Analysis ---
    suspend fun analyzeArticle(article: NewsArticle): AIAnalysisResponse? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext null
        }

        val systemPrompt = """
            Eres un analista de noticias senior. Tu tarea es analizar en profundidad el siguiente artículo y proporcionar un análisis de IA riguroso estructurado en formato JSON estricto.
            El JSON devuelto debe contener los siguientes campos exactos:
            - keyPoints: Un array de strings con un máximo de 3 puntos clave resumidos.
            - contextAndImpact: Una explicación concisa (2-3 oraciones) sobre el contexto histórico o social de la noticia y su posible impacto a futuro.
            - sentiment: Una sola palabra que describa el tono emocional preponderante (ej. 'Favorable', 'Neutral', 'Preocupante', 'Optimista', 'Crítico').
            - sentimentExplanation: Una frase que explique el análisis de sentimiento del tono.

            Devuelve únicamente el objeto JSON dentro de un bloque markdown ```json. No agregues texto adicional fuera del bloque.
        """.trimIndent()

        val prompt = """
            Título: ${article.title}
            Categoría: ${article.category}
            Fuente: ${article.source}
            Resumen: ${article.summary}
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2f),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val analysis = RetrofitClient.parseAnalysis(jsonText)
            if (analysis != null) {
                // Save the string representation to the database for future offline access
                newsDao.updateArticleAnalysis(article.url, jsonText)
            }
            analysis
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- AI Recommendation Engine ---
    suspend fun generateRecommendations(
        preferences: UserPreferences,
        history: List<ReadingHistory>,
        articles: List<NewsArticle>
    ): List<RecommendationResult> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || articles.isEmpty()) {
            return@withContext emptyList()
        }

        val interests = preferences.selectedCategories
        val recentlyRead = history.take(10).joinToString("\n") { "- ${it.title} (${it.category})" }
        val availableArticles = articles.joinToString("\n") { "- URL: ${it.url}\nTítulo: ${it.title}\nCategoría: ${it.category}\nResumen: ${it.summary}\n" }

        val systemPrompt = """
            Eres el cerebro de recomendación personalizada de Noticias al Día. Tu trabajo es analizar los intereses del lector y su historial de lectura, y seleccionar las 3 mejores noticias recomendadas entre los artículos disponibles.
            - Intereses favoritos del usuario: $interests
            - Artículos leídos recientemente: 
            $recentlyRead

            Debes evaluar los artículos disponibles y seleccionar exactamente 3 que mejor se adapten al usuario.
            Devuelve un array JSON de recomendaciones. Cada objeto en el array debe tener exactamente estos campos:
            - url: Debe ser el URL exacto del artículo original seleccionado.
            - recommendationReason: Una explicación persuasiva, en español, de 1 o 2 frases explicando al lector por qué se le recomienda esta noticia según sus intereses y lecturas.

            Devuelve únicamente el array JSON válido dentro de un bloque markdown ```json. No agregues comentarios.
        """.trimIndent()

        val prompt = "Genera las recomendaciones de lectura del día basadas en la lista de artículos:\n$availableArticles"

        val request = GenerateContentRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.4f),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleanJson = cleanMarkdownJson(jsonText)
            
            // Custom parsing for recommendations
            val listType = Types.newParameterizedType(List::class.java, RecommendationResult::class.java)
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter<List<RecommendationResult>>(listType)
            adapter.fromJson(cleanJson) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun cleanMarkdownJson(input: String): String {
        var clean = input.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }
}

@JsonClass(generateAdapter = true)
data class RecommendationResult(
    val url: String,
    val recommendationReason: String
)
