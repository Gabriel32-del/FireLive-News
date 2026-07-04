package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    // --- Cached News ---
    @Query("SELECT * FROM cached_news ORDER BY timestamp DESC")
    fun getAllCachedNews(): Flow<List<NewsArticle>>

    @Query("SELECT * FROM cached_news WHERE category = :category ORDER BY timestamp DESC")
    fun getCachedNewsByCategory(category: String): Flow<List<NewsArticle>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<NewsArticle>)

    @Query("UPDATE cached_news SET aiAnalysis = :analysis WHERE url = :url")
    suspend fun updateArticleAnalysis(url: String, analysis: String)

    @Query("DELETE FROM cached_news")
    suspend fun clearAllCachedNews()

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    // --- Reading History ---
    @Query("SELECT * FROM reading_history ORDER BY timestamp DESC")
    fun getReadingHistory(): Flow<List<ReadingHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingHistory(history: ReadingHistory)

    @Query("DELETE FROM reading_history")
    suspend fun clearReadingHistory()

    // --- User Preferences ---
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun getUserPreferencesFlow(): Flow<UserPreferences?>

    @Query("SELECT * FROM user_preferences WHERE id = 1")
    suspend fun getUserPreferences(): UserPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPreferences(preferences: UserPreferences)
}
