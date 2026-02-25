package com.github.aidigestbot;

import com.github.aidigestbot.service.DatabaseService;
import com.github.aidigestbot.service.GeminiService;
import com.github.aidigestbot.service.NewsFetcherService;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int DEFAULT_DIGEST_HOUR = 9;

    public static void main(String[] args) {
        var dotenv = Dotenv.load();
        var botToken = dotenv.get("BOT_TOKEN");

        if (botToken == null || botToken.isBlank()) {
            logger.error("BOT_TOKEN is not set in the .env file");
            return;
        }

        int digestHour = parseDigestHour(dotenv.get("DIGEST_HOUR"));

        var databaseService = new DatabaseService();
        var newsFetcherService = new NewsFetcherService();
        var geminiService = new GeminiService();

        var bot = new DigestBot(botToken, newsFetcherService, geminiService, databaseService);

        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "digest-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        scheduleDaily(scheduler, digestHour, () -> sendScheduledDigest(bot, databaseService));

        try {
            var botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(botToken, bot);
            logger.info("Bot started successfully!");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down...");
                scheduler.shutdownNow();
                bot.shutdown();
                newsFetcherService.shutdown();
                try {
                    botsApplication.close();
                } catch (Exception e) {
                    logger.error("Error closing bot application", e);
                }
            }));

            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to start the bot", e);
        }
    }

    private static void scheduleDaily(ScheduledExecutorService scheduler, int hour, Runnable task) {
        var now = LocalDateTime.now();
        var targetToday = now.toLocalDate().atTime(LocalTime.of(hour, 0));
        var nextRun = now.isBefore(targetToday) ? targetToday : targetToday.plusDays(1);
        long initialDelay = Duration.between(now, nextRun).toMinutes();

        logger.info("Daily digest scheduled at {}:00, first run in {} minutes", hour, initialDelay);

        scheduler.scheduleAtFixedRate(task, initialDelay, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
    }

    private static void sendScheduledDigest(DigestBot bot, DatabaseService databaseService) {
        logger.info("Running scheduled daily digest");
        var chatIds = databaseService.getAllChatIds();

        if (chatIds.isEmpty()) {
            logger.info("No registered chats found, skipping scheduled digest");
            return;
        }

        logger.info("Sending scheduled digest to {} chats", chatIds.size());
        for (var chatId : chatIds) {
            try {
                bot.handleDigestCommand(chatId);
            } catch (Exception e) {
                logger.error("Failed to send scheduled digest to chat {}", chatId, e);
            }
        }
        logger.info("Scheduled digest completed");
    }

    private static int parseDigestHour(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_DIGEST_HOUR;
        }
        try {
            int hour = Integer.parseInt(value.trim());
            if (hour < 0 || hour > 23) {
                logger.warn("DIGEST_HOUR {} is out of range (0-23), using default {}", hour, DEFAULT_DIGEST_HOUR);
                return DEFAULT_DIGEST_HOUR;
            }
            return hour;
        } catch (NumberFormatException e) {
            logger.warn("Invalid DIGEST_HOUR value '{}', using default {}", value, DEFAULT_DIGEST_HOUR);
            return DEFAULT_DIGEST_HOUR;
        }
    }
}
