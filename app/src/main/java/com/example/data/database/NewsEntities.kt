package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_news")
data class NewsArticle(
    @PrimaryKey val url: String,
    val title: String,
    val source: String,
    val summary: String,
    val date: String,
    val category: String,
    val imageQuery: String,
    val timestamp: Long = System.currentTimeMillis(),
    val aiAnalysis: String? = null
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val url: String,
    val title: String,
    val source: String,
    val summary: String,
    val date: String,
    val category: String,
    val imageQuery: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reading_history")
data class ReadingHistory(
    @PrimaryKey val url: String,
    val title: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 1,
    val selectedCategories: String = "Tecnología,Ciencia,Deportes,Economía",
    val notificationTopics: String = "Tecnología",
    val isDarkMode: Boolean = true,
    val notificationsEnabled: Boolean = true
)
