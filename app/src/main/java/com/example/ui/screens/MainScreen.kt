package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.database.Bookmark
import com.example.data.database.NewsArticle
import com.example.data.network.AIAnalysisResponse
import com.example.ui.viewmodel.AnalysisUiState
import com.example.ui.viewmodel.NewsUiState
import com.example.ui.viewmodel.NewsViewModel
import com.example.ui.viewmodel.RecommendedArticle
import java.text.SimpleDateFormat
import java.util.*

// --- Color Palette Definitions ---
val DarkSlateBg = Color(0xFF0F172A)
val DarkCardBg = Color(0xFF1E293B)
val LightSlateBg = Color(0xFFF8FAFC)
val LightCardBg = Color(0xFFFFFFFF)

val PrimaryEmerald = Color(0xFF10B981)
val SecondaryTeal = Color(0xFF0D9488)
val AccentAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: NewsViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val selectedArticle by viewModel.selectedArticle.collectAsState()

    val isDark = userPreferences.isDarkMode

    // Dynamic Color Scheme Override
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = PrimaryEmerald,
            secondary = SecondaryTeal,
            background = DarkSlateBg,
            surface = DarkCardBg,
            onBackground = Color.White,
            onSurface = Color(0xFFE2E8F0)
        )
    } else {
        lightColorScheme(
            primary = SecondaryTeal,
            secondary = PrimaryEmerald,
            background = LightSlateBg,
            surface = LightCardBg,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF334155)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (selectedArticle == null) {
                        NewsBottomBar(currentTab = currentTab, onTabSelected = { viewModel.setTab(it) })
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AnimatedContent(
                        targetState = selectedArticle,
                        transitionSpec = {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { -it })
                        },
                        label = "ScreenTransition"
                    ) { targetArticle ->
                        if (targetArticle != null) {
                            ArticleDetailScreen(
                                article = targetArticle,
                                viewModel = viewModel,
                                onBack = { viewModel.selectArticle(null) }
                            )
                        } else {
                            when (currentTab) {
                                "noticias" -> NewsFeedTab(viewModel = viewModel)
                                "recomendaciones" -> AIRecommendationsTab(viewModel = viewModel)
                                "marcadores" -> BookmarksTab(viewModel = viewModel)
                                "ajustes" -> SettingsTab(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Bottom Navigation Bar ---
@Composable
fun NewsBottomBar(currentTab: String, onTabSelected: (String) -> Unit) {
    NavigationBar(
        modifier = Modifier.testTag("news_bottom_navigation"),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentTab == "noticias",
            onClick = { onTabSelected("noticias") },
            icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
            label = { Text("Noticias") },
            modifier = Modifier.testTag("tab_home")
        )
        NavigationBarItem(
            selected = currentTab == "recomendaciones",
            onClick = { onTabSelected("recomendaciones") },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Para Ti") },
            label = { Text("Para Ti") },
            modifier = Modifier.testTag("tab_recommendations")
        )
        NavigationBarItem(
            selected = currentTab == "marcadores",
            onClick = { onTabSelected("marcadores") },
            icon = { Icon(Icons.Default.Bookmark, contentDescription = "Marcadores") },
            label = { Text("Guardados") },
            modifier = Modifier.testTag("tab_bookmarks")
        )
        NavigationBarItem(
            selected = currentTab == "ajustes",
            onClick = { onTabSelected("ajustes") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
            label = { Text("Ajustes") },
            modifier = Modifier.testTag("tab_settings")
        )
    }
}

// ==========================================
// TAB 1: NEWS FEED TAB
// ==========================================
@Composable
fun NewsFeedTab(viewModel: NewsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val articles by viewModel.allCachedNews.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val preferences by viewModel.userPreferences.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val categories = remember(preferences.selectedCategories) {
        listOf("Todas") + preferences.selectedCategories.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val filteredArticles = remember(articles, selectedCategory) {
        if (selectedCategory == "Todas") {
            articles
        } else {
            articles.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // News Header
        NewsHeader(
            onRefresh = { viewModel.fetchNewsForInterests() },
            isLoading = uiState is NewsUiState.Loading
        )

        // Categories Scrolling Rail
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setSelectedCategory(category) },
                    label = { Text(category, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("category_chip_$category")
                )
            }
        }

        // State Machine Renderer
        when (val state = uiState) {
            is NewsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Buscando noticias reales con IA...", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            is NewsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("error_text"))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.fetchNewsForInterests() }, modifier = Modifier.testTag("retry_button")) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            else -> {
                if (filteredArticles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, contentDescription = "Sin Noticias", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No hay noticias cargadas.", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                            Text("Pulsa recargar para buscar en Google Search.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredArticles) { article ->
                            val isBookmarked = bookmarks.any { it.url == article.url }
                            NewsCard(
                                article = article,
                                isBookmarked = isBookmarked,
                                onBookmarkClick = { viewModel.toggleBookmark(article) },
                                onCardClick = { viewModel.selectArticle(article) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsHeader(onRefresh: () -> Unit, isLoading: Boolean) {
    val dateString = remember {
        val sdf = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
        sdf.format(Date())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = dateString.replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodyMedium,
                color = AccentAmber,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Noticias al Día",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                .testTag("refresh_button")
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Recargar",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ==========================================
// NEWS CARD LAYOUT
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsCard(
    article: NewsArticle,
    isBookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    onCardClick: () -> Unit
) {
    val context = LocalContext.current
    val coverUrl = remember(article.category, article.imageQuery) {
        getCoverUrlForArticle(article.category, article.imageQuery)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("news_card_${article.url.hashCode()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // News Cover Photo with overlay badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = article.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Linear gradient fade
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 100f
                            )
                        )
                )
                // Category Badge
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = article.category.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                // Source and Date Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.source,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = article.date,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }

            // News Text Description
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Card Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Analizar con IA ✦",
                        color = PrimaryEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    IconButton(
                        onClick = onBookmarkClick,
                        modifier = Modifier.testTag("bookmark_button_${article.url.hashCode()}")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Guardar",
                            tint = if (isBookmarked) AccentAmber else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 2: AI RECOMMENDATIONS TAB
// ==========================================
@Composable
fun AIRecommendationsTab(viewModel: NewsViewModel) {
    val recommendations by viewModel.aiRecommendations.collectAsState()
    val isGenerating by viewModel.isGeneratingRecommendations.collectAsState()
    val history by viewModel.readingHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Recomendaciones IA",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Adaptadas dinámicamente a tu perfil e historial de lectura diaria",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isGenerating) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PrimaryEmerald)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Generando recomendaciones personalizadas con IA...", color = PrimaryEmerald)
                }
            }
        } else {
            if (recommendations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Spark", tint = AccentAmber, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Sin recomendaciones todavía",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Lee algunas noticias en la pestaña principal para que el motor de IA conozca tus preferencias de lectura diarias.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recommendations) { rec ->
                        RecommendedNewsCard(
                            recommendation = rec,
                            onCardClick = { viewModel.selectArticle(rec.article) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendedNewsCard(recommendation: RecommendedArticle, onCardClick: () -> Unit) {
    val article = recommendation.article
    val context = LocalContext.current
    val coverUrl = remember(article.category) {
        getCoverUrlForArticle(article.category, article.imageQuery)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("rec_news_card_${article.url.hashCode()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Recommendation rationale header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryEmerald.copy(alpha = 0.12f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "IA", tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Por qué te recomendamos esto:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryEmerald
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryEmerald.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = recommendation.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontStyle = FontStyle.Italic
                )
            }

            // Article body
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = article.category.uppercase(),
                        color = PrimaryEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${article.source} • ${article.date}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

// ==========================================
// TAB 3: BOOKMARKS TAB
// ==========================================
@Composable
fun BookmarksTab(viewModel: NewsViewModel) {
    val bookmarks by viewModel.bookmarks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Artículos Guardados",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Lee tus marcadores guardados sin prisas",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (bookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Filled.BookmarkBorder, contentDescription = "Vacío", tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No tienes artículos guardados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Pulsando el icono de marcador 🔖 en cualquier noticia la guardarás aquí automáticamente para leerla más tarde.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(bookmarks) { bookmark ->
                    // Transform Bookmark back to NewsArticle to display/select
                    val article = NewsArticle(
                        url = bookmark.url,
                        title = bookmark.title,
                        source = bookmark.source,
                        summary = bookmark.summary,
                        date = bookmark.date,
                        category = bookmark.category,
                        imageQuery = bookmark.imageQuery,
                        timestamp = bookmark.timestamp
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectArticle(article) }
                            .testTag("bookmark_item_${bookmark.url.hashCode()}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookmark.category.uppercase(),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = bookmark.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${bookmark.source} • ${bookmark.date}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = { viewModel.toggleBookmark(article) }) {
                                Icon(Icons.Filled.Bookmark, contentDescription = "Quitar", tint = AccentAmber)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 4: SETTINGS & PREFERENCES TAB
// ==========================================
@Composable
fun SettingsTab(viewModel: NewsViewModel) {
    val prefs by viewModel.userPreferences.collectAsState()
    val logs by viewModel.notificationLogs.collectAsState()
    val context = LocalContext.current

    var isAddingTopic by remember { mutableStateOf(false) }
    var topicText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Ajustes de Perfil",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Personaliza tus intereses de IA y configuración de notificaciones",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        // Section 1: Dark Mode Setting
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = "Dark Mode", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Modo Oscuro Nocturno", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Agradable lectura por la noche", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Switch(
                        checked = prefs.isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode(it) },
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }
            }
        }

        // Section 2: Notifications Enable
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notis", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Notificaciones Push", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Alertas sobre tus temas favoritos", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Switch(
                        checked = prefs.notificationsEnabled,
                        onCheckedChange = { viewModel.toggleNotifications(it) },
                        modifier = Modifier.testTag("notifications_switch")
                    )
                }
            }
        }

        // Section 3: Custom Categories of Interest (Simulating preferences editing)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Mis Intereses de IA", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("Selecciona las categorías de noticias que el motor inteligente buscará hoy", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    val allAvailableCategories = listOf("Tecnología", "Ciencia", "Deportes", "Economía", "Entretenimiento", "Cultura", "Internacional")
                    val currentSelected = prefs.selectedCategories.split(",").map { it.trim() }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 8.dp
                    ) {
                        allAvailableCategories.forEach { category ->
                            val isSelected = currentSelected.contains(category)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updatedList = currentSelected.toMutableList()
                                    if (isSelected) {
                                        if (updatedList.size > 1) updatedList.remove(category)
                                    } else {
                                        updatedList.add(category)
                                    }
                                    viewModel.updateCategories(updatedList.joinToString(","))
                                },
                                label = { Text(category) },
                                modifier = Modifier.testTag("pref_chip_$category")
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Alert Topics for "Push" Notification Alerts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Temas de Alertas Críticas", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("Palabras clave de las que quieres recibir notificaciones push en tiempo real", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = topicText,
                            onValueChange = { topicText = it },
                            placeholder = { Text("Ej. Inteligencia Artificial") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("topic_alert_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (topicText.isNotBlank()) {
                                    val currentTopics = prefs.notificationTopics.split(",").map { it.trim() }.toMutableList()
                                    if (!currentTopics.contains(topicText)) {
                                        currentTopics.add(topicText)
                                        viewModel.updateNotificationTopics(currentTopics.joinToString(","))
                                        topicText = ""
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("add_topic_button")
                        ) {
                            Text("Añadir")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    // Active Topic tags
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 8.dp
                    ) {
                        prefs.notificationTopics.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { topic ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    val list = prefs.notificationTopics.split(",").map { it.trim() }.toMutableList()
                                    list.remove(topic)
                                    viewModel.updateNotificationTopics(list.joinToString(","))
                                },
                                label = { Text(topic) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Quitar", modifier = Modifier.size(14.dp)) },
                                modifier = Modifier.testTag("alert_topic_chip_$topic")
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Demonstration Interactive Triggers
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Simulación de Alertas", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("Haz clic para simular una alerta push sobre tus intereses y probar el servicio", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.sendLocalAlert(
                                "Alerta en Tiempo Real ✦ Noticias al Día",
                                "Se ha detectado un acontecimiento de última hora en tu categoría de interés principal."
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulate_push_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simular Alerta de Última Hora")
                    }
                }
            }
        }

        // Section 6: Notification History Log
        item {
            Text(
                "Historial de Alertas Push Recibidas",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    "Aún no se han recibido alertas.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(logs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(log.first, fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(log.second, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

// ==========================================
// ARTICLE DETAIL SCREEN WITH AI HUBS
// ==========================================
@Composable
fun ArticleDetailScreen(
    article: NewsArticle,
    viewModel: NewsViewModel,
    onBack: () -> Unit
) {
    val analysisState by viewModel.analysisState.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val isBookmarked = bookmarks.any { it.url == article.url }
    val context = LocalContext.current
    val coverUrl = remember(article.category) {
        getCoverUrlForArticle(article.category, article.imageQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Detailed Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = MaterialTheme.colorScheme.onBackground)
            }
            Row {
                IconButton(onClick = { viewModel.toggleBookmark(article) }, modifier = Modifier.testTag("detail_bookmark_button")) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Guardar",
                        tint = if (isBookmarked) AccentAmber else MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, article.title)
                            putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nLee más en: ${article.url}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir noticia"))
                    },
                    modifier = Modifier.testTag("detail_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title & Source Info
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = article.category.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Fuente: ${article.source}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = article.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            // Big Cover Photo
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }

            // News Text Summary
            item {
                Text(
                    text = "Resumen del Artículo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // AI Hub section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_insight_hub"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryEmerald.copy(alpha = 0.08f)),
                    border = CardBorder(
                        width = 1.dp,
                        color = PrimaryEmerald.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "IA", tint = PrimaryEmerald)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Insight de Inteligencia Artificial ✦",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        when (val analysis = analysisState) {
                            is AnalysisUiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = PrimaryEmerald, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Generando análisis profundo con Gemini...", fontSize = 12.sp, color = PrimaryEmerald)
                                    }
                                }
                            }
                            is AnalysisUiState.Success -> {
                                val result = analysis.analysis
                                // Display key takeaways
                                Text("Puntos Clave:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                                result.keyPoints.forEach { point ->
                                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("• ", fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                        Text(point, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Contexto e Impacto:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                                Text(result.contextAndImpact, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tono / Sentimiento: ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                                    Surface(
                                        color = getSentimentColor(result.sentiment).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            result.sentiment,
                                            color = getSentimentColor(result.sentiment),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(result.sentimentExplanation, fontSize = 12.sp, color = Color.Gray, fontStyle = FontStyle.Italic)
                            }
                            is AnalysisUiState.Error -> {
                                Text(analysis.message, color = Color.Red, fontSize = 12.sp)
                            }
                            else -> {
                                Text("Análisis en cola...", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // External Link
            item {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .testTag("open_source_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Launch, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ver noticia completa original")
                }
            }
        }
    }
}

// Helper to determine sentiment badge colors
fun getSentimentColor(sentiment: String): Color {
    return when (sentiment.lowercase()) {
        "favorable", "optimista", "positivo" -> PrimaryEmerald
        "preocupante", "crítico", "crítica", "negativo" -> Color.Red
        "neutral" -> Color.Gray
        else -> AccentAmber
    }
}

// Simple FlowRow helper for chip tags wrapping
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth
        val lines = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentLine = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentLineWidth = 0

        placeables.forEach { placeable ->
            if (currentLineWidth + placeable.width + mainAxisSpacing.roundToPx() > layoutWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                currentLine = mutableListOf()
                currentLineWidth = 0
            }
            currentLine.add(placeable)
            currentLineWidth += placeable.width + mainAxisSpacing.roundToPx()
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        var totalHeight = 0
        lines.forEachIndexed { index, line ->
            val maxLineHeight = line.maxOf { it.height }
            totalHeight += maxLineHeight
            if (index < lines.size - 1) {
                totalHeight += crossAxisSpacing.roundToPx()
            }
        }

        layout(layoutWidth, totalHeight) {
            var currentY = 0
            lines.forEach { line ->
                var currentX = 0
                val maxLineHeight = line.maxOf { it.height }
                line.forEach { placeable ->
                    placeable.placeRelative(currentX, currentY + (maxLineHeight - placeable.height) / 2)
                    currentX += placeable.width + mainAxisSpacing.roundToPx()
                }
                currentY += maxLineHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}

// Helper to declare a custom border for card
@Composable
fun CardBorder(width: androidx.compose.ui.unit.Dp, color: Color, shape: RoundedCornerShape): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(width, color)
}

fun getCoverUrlForArticle(category: String, query: String): String {
    return when (category.lowercase()) {
        "tecnología", "technology" -> "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=500&q=80"
        "ciencia", "science" -> "https://images.unsplash.com/photo-1507668077129-56e32842fceb?auto=format&fit=crop&w=500&q=80"
        "deportes", "sports" -> "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=500&q=80"
        "economía", "economy", "negocios" -> "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?auto=format&fit=crop&w=500&q=80"
        "entretenimiento", "entertainment" -> "https://images.unsplash.com/photo-1603190287605-e6ade32fa852?auto=format&fit=crop&w=500&q=80"
        else -> "https://images.unsplash.com/photo-1504711434969-e33886168f5c?auto=format&fit=crop&w=500&q=80"
    }
}
