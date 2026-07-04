package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.repository.NewsRepository
import com.example.data.repository.RecommendationResult
import com.example.data.network.AIAnalysisResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface NewsUiState {
    object Idle : NewsUiState
    object Loading : NewsUiState
    object Success : NewsUiState
    data class Error(val message: String) : NewsUiState
}

sealed interface AnalysisUiState {
    object Idle : AnalysisUiState
    object Loading : AnalysisUiState
    data class Success(val analysis: AIAnalysisResponse) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

data class RecommendedArticle(
    val article: NewsArticle,
    val reason: String
)

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = NewsRepository(database.newsDao())

    // --- Core Flows ---
    val allCachedNews: StateFlow<List<NewsArticle>> = repository.allCachedNews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readingHistory: StateFlow<List<ReadingHistory>> = repository.readingHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferences> = repository.userPreferences
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    // --- UI State Management ---
    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Idle)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _currentTab = MutableStateFlow("noticias") // "noticias", "recomendaciones", "marcadores", "ajustes"
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // --- Detailed Article and AI Analysis ---
    private val _selectedArticle = MutableStateFlow<NewsArticle?>(null)
    val selectedArticle: StateFlow<NewsArticle?> = _selectedArticle.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisState: StateFlow<AnalysisUiState> = _analysisState.asStateFlow()

    // --- AI Recommendations state ---
    private val _aiRecommendations = MutableStateFlow<List<RecommendedArticle>>(emptyList())
    val aiRecommendations: StateFlow<List<RecommendedArticle>> = _aiRecommendations.asStateFlow()

    private val _isGeneratingRecommendations = MutableStateFlow(false)
    val isGeneratingRecommendations: StateFlow<Boolean> = _isGeneratingRecommendations.asStateFlow()

    // --- Internal alerts center (Simulating Push History) ---
    private val _notificationLogs = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val notificationLogs: StateFlow<List<Pair<String, String>>> = _notificationLogs.asStateFlow()

    init {
        createNotificationChannel()
        // Automatically fetch news on first launch
        viewModelScope.launch {
            val prefs = repository.getPreferences()
            if (prefs != null) {
                fetchNewsForInterests()
            }
        }
    }

    // --- Set Tab ---
    fun setTab(tab: String) {
        _currentTab.value = tab
        if (tab == "recomendaciones") {
            generateAIRecommendations()
        }
    }

    // --- Set Category Filter ---
    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    // --- Fetch News based on selected preferences ---
    fun fetchNewsForInterests() {
        viewModelScope.launch {
            _uiState.value = NewsUiState.Loading
            val categoriesString = userPreferences.value.selectedCategories
            val categoriesList = categoriesString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val result = repository.fetchLatestNews(categoriesList)
            result.onSuccess {
                _uiState.value = NewsUiState.Success
                // Automatically generate any push notification for preferred notification topics if matching new articles exist
                triggerPushSimulation()
            } .onFailure { error ->
                _uiState.value = NewsUiState.Error(error.localizedMessage ?: "Error desconocido al obtener noticias")
            }
        }
    }

    // --- Bookmark management ---
    fun toggleBookmark(article: NewsArticle) {
        viewModelScope.launch {
            val isBookmarkedCurrently = bookmarks.value.any { it.url == article.url }
            if (isBookmarkedCurrently) {
                repository.removeBookmark(article.url)
            } else {
                repository.addBookmark(article)
            }
        }
    }

    // --- Select Article & Register View ---
    fun selectArticle(article: NewsArticle?) {
        _selectedArticle.value = article
        _analysisState.value = AnalysisUiState.Idle
        if (article != null) {
            viewModelScope.launch {
                // Register read history
                repository.addToHistory(article)

                // If article has cached analysis, display it immediately
                if (!article.aiAnalysis.isNullOrBlank()) {
                    try {
                        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                        val adapter = moshi.adapter(AIAnalysisResponse::class.java)
                        val analysis = adapter.fromJson(article.aiAnalysis)
                        if (analysis != null) {
                            _analysisState.value = AnalysisUiState.Success(analysis)
                            return@launch
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // If not cached, trigger background analysis
                analyzeCurrentArticle(article)
            }
        }
    }

    // --- Perform AI Article Analysis ---
    private fun analyzeCurrentArticle(article: NewsArticle) {
        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.Loading
            val analysis = repository.analyzeArticle(article)
            if (analysis != null) {
                _analysisState.value = AnalysisUiState.Success(analysis)
            } else {
                _analysisState.value = AnalysisUiState.Error("No se pudo obtener el análisis inteligente para esta noticia.")
            }
        }
    }

    // --- Generate Recommendations ---
    fun generateAIRecommendations() {
        viewModelScope.launch {
            if (allCachedNews.value.isEmpty()) return@launch
            _isGeneratingRecommendations.value = true
            
            val prefs = userPreferences.value
            val history = readingHistory.value
            val currentArticles = allCachedNews.value

            val recs = repository.generateRecommendations(prefs, history, currentArticles)
            
            // Map recommendations back to NewsArticles
            val recommendedList = recs.mapNotNull { recResult ->
                val article = currentArticles.find { it.url == recResult.url }
                if (article != null) {
                    RecommendedArticle(article, recResult.recommendationReason)
                } else {
                    null
                }
            }
            
            _aiRecommendations.value = recommendedList
            _isGeneratingRecommendations.value = false
        }
    }

    // --- Save user preferences profile ---
    fun updateCategories(categories: String) {
        viewModelScope.launch {
            val updated = userPreferences.value.copy(selectedCategories = categories)
            repository.savePreferences(updated)
            fetchNewsForInterests()
        }
    }

    fun updateNotificationTopics(topics: String) {
        viewModelScope.launch {
            val updated = userPreferences.value.copy(notificationTopics = topics)
            repository.savePreferences(updated)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userPreferences.value.copy(isDarkMode = enabled)
            repository.savePreferences(updated)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userPreferences.value.copy(notificationsEnabled = enabled)
            repository.savePreferences(updated)
        }
    }

    // --- Notifications Management (Real + Simulation logs) ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Noticias al Día"
            val descriptionText = "Canal de notificaciones push de última hora"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("NEWS_ALERTS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendLocalAlert(title: String, message: String) {
        val context = getApplication<Application>()
        
        // Save to in-app simulated logs
        val list = _notificationLogs.value.toMutableList()
        list.add(0, Pair(title, message))
        _notificationLogs.value = list

        if (!userPreferences.value.notificationsEnabled) return

        // Build and trigger standard Android notification
        val builder = NotificationCompat.Builder(context, "NEWS_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerPushSimulation() {
        // Find if there's any highly relevant news matching users' preferred notification topic to trigger a "push"
        val topics = userPreferences.value.notificationTopics.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (topics.isNotEmpty() && allCachedNews.value.isNotEmpty()) {
            val matchedArticle = allCachedNews.value.firstOrNull { article ->
                topics.any { topic -> article.category.equals(topic, ignoreCase = true) || article.title.contains(topic, ignoreCase = true) }
            }
            if (matchedArticle != null) {
                sendLocalAlert(
                    "Última hora en ${matchedArticle.category}",
                    "${matchedArticle.title} - por ${matchedArticle.source}"
                )
            }
        }
    }
}
