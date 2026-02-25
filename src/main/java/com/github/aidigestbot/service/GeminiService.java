package com.github.aidigestbot.service;

import com.github.aidigestbot.model.NewsArticle;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private static final String SYSTEM_PROMPT = """
        Explain each news item in 1-2 very short sentences using simple words (like explaining to a 5-year-old).
        Pick exactly 6 stories.

        Format:
        - Numbered list (1, 2, 3...).
        - Title in ALL CAPS.
        - One blank line between title and text.
        - Source URL in parentheses after the text.
        - TWO blank lines between items.
        - NO bold (**text**), no long dashes, no angle quotes.

        You MUST write the entire response in %s.
        """;

    private static final String MONTHLY_DIGEST_PROMPT = """
        You are given an archive of tech news from one month.
        Pick the top 5-7 most impactful stories.

        Format:
        - Start with "TOP STORIES OF THE MONTH".
        - Numbered list, title in ALL CAPS.
        - One blank line between title and summary.
        - 3-5 sentence summary per story explaining what happened and why it matters.
        - Source URL in parentheses after each summary.
        - TWO blank lines between items.
        - NO bold (**text**), no long dashes, no angle quotes.

        You MUST write the entire response in %s.
        """;

    private static final String WEB_BEST_NEWS_PROMPT = """
        Act as a tech historian. Identify the 5 most significant, viral, and impactful tech news that happened in %s %d.

        For each news:
        - Title in ALL CAPS.
        - 1-2 very simple sentences explaining what happened (ELI5 style).
        - Provide a real source URL if possible, or a general link to a major tech site covering it.

        Format:
        - Numbered list (1, 2, 3...).
        - One blank line between title and text.
        - Source URL in parentheses after the text.
        - TWO blank lines between items.
        - NO bold (**text**), no long dashes, no angle quotes.

        You MUST write the entire response in %s.
        """;

    private final GoogleAiGeminiChatModel chatModel;

    public GeminiService() {
        var dotenv = Dotenv.load();
        var apiKey = dotenv.get("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            logger.error("GEMINI_API_KEY is not set in the .env file");
            throw new IllegalStateException("Missing GEMINI_API_KEY");
        }

        this.chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-flash-latest")
                .temperature(0.4)
                .maxOutputTokens(8192)
                .build();

        logger.info("GeminiService initialized with model gemini-flash-latest");
    }

    public String generateDigest(List<NewsArticle> articles, String language) {
        if (articles.isEmpty()) {
            logger.info("No new articles to summarize");
            return "No new articles for the digest.";
        }

        var langName = "EN".equalsIgnoreCase(language) ? "English" : "Russian";

        var articlesList = articles.stream()
                .limit(30)
                .map(a -> "- %s\n  %s\n  Link: %s".formatted(a.title(), a.description(), a.url()))
                .collect(Collectors.joining("\n"));

        var prompt = SYSTEM_PROMPT.formatted(langName) + "\n\n" + articlesList;

        logger.info("Sending {} articles to Gemini for digest generation (lang={})", articles.size(), langName);
        logger.debug("Prompt:\n{}", prompt);

        try {
            var result = chatModel.chat(prompt);
            logger.info("Digest generated successfully ({} chars)", result.length());
            return result;
        } catch (Exception e) {
            logger.error("Failed to generate digest via Gemini", e);
            throw new RuntimeException("Digest generation failed", e);
        }
    }

    public String generateMonthlyDigest(List<NewsArticle> articles, String language) {
        if (articles.isEmpty()) {
            return "No articles found for the selected period.";
        }

        var langName = "EN".equalsIgnoreCase(language) ? "English" : "Russian";

        var articlesList = articles.stream()
                .map(a -> "- %s\n  %s\n  Link: %s".formatted(a.title(), a.description(), a.url()))
                .collect(Collectors.joining("\n"));

        var prompt = MONTHLY_DIGEST_PROMPT.formatted(langName) + "\n\n" + articlesList;

        logger.info("Sending {} archived articles to Gemini for monthly digest (lang={})", articles.size(), langName);

        try {
            var result = chatModel.chat(prompt);
            logger.info("Monthly digest generated successfully ({} chars)", result.length());
            return result;
        } catch (Exception e) {
            logger.error("Failed to generate monthly digest via Gemini", e);
            throw new RuntimeException("Monthly digest generation failed", e);
        }
    }

    public String findBestNewsFromWeb(int year, int month, String language) {
        var langName = "EN".equalsIgnoreCase(language) ? "English" : "Russian";
        var monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        var prompt = WEB_BEST_NEWS_PROMPT.formatted(monthName, year, langName);

        logger.info("Requesting best tech news from web for {}-{} (lang={})", year, month, langName);

        try {
            var result = chatModel.chat(prompt);
            logger.info("Web best news generated successfully ({} chars)", result.length());
            return result;
        } catch (Exception e) {
            logger.error("Failed to generate web best news via Gemini for {}-{}", year, month, e);
            throw new RuntimeException("Web best news generation failed", e);
        }
    }
}
