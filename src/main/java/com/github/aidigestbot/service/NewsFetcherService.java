package com.github.aidigestbot.service;

import com.github.aidigestbot.model.NewsArticle;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewsFetcherService {

    private static final Logger logger = LoggerFactory.getLogger(NewsFetcherService.class);

    private static final List<String> RSS_URLS = List.of(
            "https://techcrunch.com/feed/",
            "https://news.ycombinator.com/rss",
            "https://www.theverge.com/rss/index.xml"
    );

    private final ExecutorService executor;

    public NewsFetcherService() {
        this.executor = Executors.newFixedThreadPool(3);
    }

    public List<NewsArticle> fetchAllArticles() {
        var futures = RSS_URLS.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> fetchFeed(url), executor))
                .toList();

        var allEntries = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        logger.info("Fetched {} total entries from {} feeds", allEntries.size(), RSS_URLS.size());

        var articles = allEntries.stream()
                .map(this::toArticle)
                .filter(Objects::nonNull)
                .toList();

        logger.info("Returning {} articles from feeds", articles.size());
        return articles;
    }

    private List<SyndEntry> fetchFeed(String feedUrl) {
        logger.info("Fetching RSS feed: {}", feedUrl);
        try (var reader = new XmlReader(new URL(feedUrl))) {
            SyndFeed feed = new SyndFeedInput().build(reader);
            List<SyndEntry> entries = feed.getEntries();
            logger.info("Fetched {} entries from {}", entries.size(), feedUrl);
            return entries;
        } catch (Exception e) {
            logger.error("Failed to fetch feed: {}", feedUrl, e);
            return Collections.emptyList();
        }
    }

    public void shutdown() {
        executor.shutdown();
        logger.info("NewsFetcherService executor shut down");
    }

    private NewsArticle toArticle(SyndEntry entry) {
        var title = entry.getTitle();
        var link = entry.getLink();
        var description = entry.getDescription() != null
                ? entry.getDescription().getValue()
                : "";

        if (link == null || link.isBlank()) {
            logger.warn("Skipping entry with missing link: {}", title);
            return null;
        }

        return new NewsArticle(
                title != null ? title : "",
                link.trim(),
                description != null ? description.trim() : ""
        );
    }
}
