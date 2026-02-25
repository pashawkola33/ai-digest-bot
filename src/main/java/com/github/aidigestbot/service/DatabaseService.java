package com.github.aidigestbot.service;

import com.github.aidigestbot.model.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private static final String DB_URL = "jdbc:sqlite:news_db.sqlite";

    private final Connection connection;

    public DatabaseService() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            logger.info("Connected to SQLite database at {}", DB_URL);
            initializeTables();
        } catch (SQLException e) {
            logger.error("Failed to connect to SQLite database", e);
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private void initializeTables() {
        var processedSql = """
                CREATE TABLE IF NOT EXISTS processed_news (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id INTEGER,
                    url TEXT,
                    UNIQUE(chat_id, url)
                )
                """;
        var articlesSql = """
                CREATE TABLE IF NOT EXISTS articles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    url TEXT UNIQUE NOT NULL,
                    description TEXT,
                    published_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;
        var settingsSql = """
                CREATE TABLE IF NOT EXISTS user_settings (
                    chat_id INTEGER PRIMARY KEY,
                    language TEXT NOT NULL DEFAULT 'EN'
                )
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(processedSql);
            stmt.execute(articlesSql);
            stmt.execute(settingsSql);
            logger.info("Tables 'processed_news', 'articles', and 'user_settings' are ready");
        } catch (SQLException e) {
            logger.error("Failed to create tables", e);
            throw new RuntimeException("Table initialization failed", e);
        }
    }

    public void saveArticle(NewsArticle article) {
        var sql = "INSERT OR IGNORE INTO articles (title, url, description) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, article.title());
            pstmt.setString(2, article.url());
            pstmt.setString(3, article.description());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving article: {}", article.url(), e);
        }
    }

    public List<NewsArticle> getArticlesByMonth(int year, int month) {
        var sql = """
                SELECT title, url, description FROM articles
                WHERE strftime('%Y', published_at) = ?
                  AND strftime('%m', published_at) = ?
                ORDER BY published_at DESC
                """;
        var articles = new ArrayList<NewsArticle>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            pstmt.setString(2, String.format("%02d", month));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    articles.add(new NewsArticle(
                            rs.getString("title"),
                            rs.getString("url"),
                            rs.getString("description")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving articles for {}-{}", year, month, e);
        }
        logger.info("Found {} articles for {}-{}", articles.size(), year, String.format("%02d", month));
        return articles;
    }

    public boolean isUrlProcessed(long chatId, String url) {
        var sql = "SELECT 1 FROM processed_news WHERE chat_id = ? AND url = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, url);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking if URL is processed for chat {}: {}", chatId, url, e);
            return false;
        }
    }

    public List<Long> getAllChatIds() {
        var sql = "SELECT DISTINCT chat_id FROM processed_news";
        var chatIds = new ArrayList<Long>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                chatIds.add(rs.getLong("chat_id"));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving all chat IDs", e);
        }
        return chatIds;
    }

    public void resetUserHistory(long chatId) {
        var sql = "DELETE FROM processed_news WHERE chat_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            int deleted = pstmt.executeUpdate();
            logger.info("Cleared {} processed entries for chat {}", deleted, chatId);
        } catch (SQLException e) {
            logger.error("Error resetting history for chat {}", chatId, e);
        }
    }

    public void markUrlAsProcessed(long chatId, String url) {
        var sql = "INSERT OR IGNORE INTO processed_news (chat_id, url) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, url);
            pstmt.executeUpdate();
            logger.info("Marked URL as processed for chat {}: {}", chatId, url);
        } catch (SQLException e) {
            logger.error("Error marking URL as processed for chat {}: {}", chatId, url, e);
        }
    }

    public boolean hasLanguagePreference(long chatId) {
        var sql = "SELECT 1 FROM user_settings WHERE chat_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking language preference for chat {}", chatId, e);
            return false;
        }
    }

    public String getLanguage(long chatId) {
        var sql = "SELECT language FROM user_settings WHERE chat_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("language");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting language for chat {}", chatId, e);
        }
        return "EN";
    }

    public void setLanguage(long chatId, String lang) {
        var sql = "INSERT INTO user_settings (chat_id, language) VALUES (?, ?) ON CONFLICT(chat_id) DO UPDATE SET language = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, lang);
            pstmt.setString(3, lang);
            pstmt.executeUpdate();
            logger.info("Set language to '{}' for chat {}", lang, chatId);
        } catch (SQLException e) {
            logger.error("Error setting language for chat {}", chatId, e);
        }
    }
}
