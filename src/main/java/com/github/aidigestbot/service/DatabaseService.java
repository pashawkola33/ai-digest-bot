package com.github.aidigestbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private static final String DB_URL = "jdbc:sqlite:news_db.sqlite";

    private final Connection connection;

    public DatabaseService() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            logger.info("Connected to SQLite database at {}", DB_URL);
            initializeTable();
        } catch (SQLException e) {
            logger.error("Failed to connect to SQLite database", e);
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private void initializeTable() {
        var sql = """
                CREATE TABLE IF NOT EXISTS processed_urls (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    url TEXT UNIQUE
                )
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            logger.info("Table 'processed_urls' is ready");
        } catch (SQLException e) {
            logger.error("Failed to create table 'processed_urls'", e);
            throw new RuntimeException("Table initialization failed", e);
        }
    }

    public boolean isUrlProcessed(String url) {
        var sql = "SELECT 1 FROM processed_urls WHERE url = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, url);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking if URL is processed: {}", url, e);
            return false;
        }
    }

    public void markUrlAsProcessed(String url) {
        var sql = "INSERT OR IGNORE INTO processed_urls (url) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, url);
            pstmt.executeUpdate();
            logger.info("Marked URL as processed: {}", url);
        } catch (SQLException e) {
            logger.error("Error marking URL as processed: {}", url, e);
        }
    }
}
