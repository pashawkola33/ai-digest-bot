package com.github.aidigestbot;

import com.github.aidigestbot.model.NewsArticle;
import com.github.aidigestbot.service.DatabaseService;
import com.github.aidigestbot.service.GeminiService;
import com.github.aidigestbot.service.NewsFetcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DigestBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger logger = LoggerFactory.getLogger(DigestBot.class);
    private static final int TELEGRAM_MESSAGE_LIMIT = 4096;

    private static final String CALLBACK_BEST_YEAR = "best_year:";
    private static final String CALLBACK_BEST_MONTH = "best_month:";
    private static final String CALLBACK_BEST_BACK = "best_back";
    private static final String CALLBACK_LANG = "lang:";

    private static final String[] MONTH_NAMES_EN = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private static final String[] MONTH_NAMES_RU = {
            "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
    };

    private static final String[] DOT_FRAMES = {".", ". .", ". . ."};

    private static final Map<String, Map<String, String>> LOCALIZATION = new HashMap<>();

    static {
        var en = new HashMap<String, String>();
        en.put("start.greeting",
                "Hello! I am your AI Tech Digest bot. I can summarize complex IT news into simple 1-2 sentence summaries.\n"
                        + "\n"
                        + "Available commands:\n"
                        + "/digest - Get the latest news from today\n"
                        + "/best - Find the most viral and important news for a specific month and year\n"
                        + "/language - Switch between English and Russian\n"
                        + "/reset - Clear your reading history");
        en.put("start.select_lang", "Please select your language:");
        en.put("lang.choose", "Choose digest language:");
        en.put("lang.changed", "Language changed to English");
        en.put("lang.en_label", "English");
        en.put("lang.ru_label", "Russian");
        en.put("lang.current_suffix", " (current)");
        en.put("reset.confirmation",
                "Your news history has been cleared. You can now use /digest to see the latest articles again.");
        en.put("digest.no_news", "No new tech news today!");
        en.put("best.select_year", "Select a year:");
        en.put("best.select_month", "Select a month (%d):");
        en.put("best.selected", "%s %d");
        en.put("best.generating", "Generating digest for %d-%02d...");
        en.put("wait.message", "Please wait");
        en.put("error.digest",
                "An error occurred while generating the digest. Please try again later.");
        en.put("error.monthly_digest",
                "An error occurred while generating the monthly digest. Please try again later.");
        en.put("best.back", "← Back");
        en.put("unknown.command",
                "I am sorry, I do not recognize that command. Please use /start to see the list of available commands.");

        var ru = new HashMap<String, String>();
        ru.put("start.greeting",
                "Привет! Я твой AI Tech Digest бот. Я могу пересказать сложные IT-новости простыми словами в 1-2 предложениях.\n"
                        + "\n"
                        + "Доступные команды:\n"
                        + "/digest - Получить последние новости за сегодня\n"
                        + "/best - Найти самые популярные и важные новости за определенный месяц и год\n"
                        + "/language - Переключить язык между английским и русским\n"
                        + "/reset - Очистить историю прочитанного");
        ru.put("start.select_lang", "Пожалуйста, выберите язык:");
        ru.put("lang.choose", "Выберите язык дайджеста:");
        ru.put("lang.changed", "Язык изменен на русский");
        ru.put("lang.en_label", "Английский");
        ru.put("lang.ru_label", "Русский");
        ru.put("lang.current_suffix", " (текущий)");
        ru.put("reset.confirmation",
                "Ваша история прочитанного очищена. Используйте /digest, чтобы снова увидеть последние новости.");
        ru.put("digest.no_news", "Нет новых IT-новостей сегодня!");
        ru.put("best.select_year", "Выберите год:");
        ru.put("best.select_month", "Выберите месяц (%d):");
        ru.put("best.selected", "%s %d");
        ru.put("best.generating", "Генерирую дайджест за %d-%02d...");
        ru.put("wait.message", "Пожалуйста, подождите");
        ru.put("error.digest",
                "Произошла ошибка при генерации дайджеста. Попробуйте позже.");
        ru.put("error.monthly_digest",
                "Произошла ошибка при генерации месячного дайджеста. Попробуйте позже.");
        ru.put("best.back", "← Назад");
        ru.put("unknown.command",
                "Извините, я не знаю такой команды. Пожалуйста, используйте /start, чтобы увидеть список доступных команд.");

        LOCALIZATION.put("EN", en);
        LOCALIZATION.put("RU", ru);
    }

    private final TelegramClient telegramClient;
    private final NewsFetcherService newsFetcherService;
    private final GeminiService geminiService;
    private final DatabaseService databaseService;

    private final ScheduledExecutorService animationScheduler;
    private final ExecutorService workerExecutor;

    // Tracks the messageId of the last sent "Main Menu" (greeting) per user
    // so the old one can be deleted when /start is called again.
    private final Map<Long, Integer> lastMenuMessageId = new ConcurrentHashMap<>();

    public DigestBot(String botToken, NewsFetcherService newsFetcherService,
                     GeminiService geminiService, DatabaseService databaseService) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.newsFetcherService = newsFetcherService;
        this.geminiService = geminiService;
        this.databaseService = databaseService;
        this.animationScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "animation-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.workerExecutor = Executors.newCachedThreadPool(r -> {
            var t = new Thread(r, "digest-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void shutdown() {
        animationScheduler.shutdownNow();
        workerExecutor.shutdownNow();
    }

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            onCallbackQuery(update);
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        var chatId = update.getMessage().getChatId();
        var text = update.getMessage().getText();

        switch (text) {
            case "/start" -> handleStartCommand(chatId);
            case "/digest" -> handleDigestCommand(chatId);
            case "/best" -> handleBestCommand(chatId);
            case "/language" -> handleLanguageCommand(chatId);
            case "/reset" -> handleResetCommand(chatId);
            default -> {
                if (text.startsWith("/")) {
                    handleUnknownCommand(chatId);
                }
            }
        }
    }

    private String getString(String key, String lang) {
        var langMap = LOCALIZATION.getOrDefault(lang.toUpperCase(), LOCALIZATION.get("EN"));
        return langMap.getOrDefault(key, key);
    }

    // --- Animation ---

    private record AnimationHandle(long chatId, int messageId, ScheduledFuture<?> future, String baseText) {}

    private AnimationHandle startWaitingAnimation(long chatId, String lang) {
        var baseText = getString("wait.message", lang);
        try {
            var sent = telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(baseText + " " + DOT_FRAMES[0])
                    .build());
            int msgId = sent.getMessageId();
            var frameIndex = new AtomicInteger(0);

            var future = animationScheduler.scheduleAtFixedRate(() -> {
                int idx = frameIndex.updateAndGet(i -> (i + 1) % DOT_FRAMES.length);
                try {
                    telegramClient.execute(EditMessageText.builder()
                            .chatId(chatId)
                            .messageId(msgId)
                            .text(baseText + " " + DOT_FRAMES[idx])
                            .build());
                } catch (TelegramApiException e) {
                    logger.warn("Animation update failed for chat {}: {}", chatId, e.getMessage());
                }
            }, 1, 1, TimeUnit.SECONDS);

            return new AnimationHandle(chatId, msgId, future, baseText);
        } catch (TelegramApiException e) {
            logger.error("Failed to send waiting message to chat {}", chatId, e);
            return null;
        }
    }

    private void stopAnimation(AnimationHandle handle) {
        if (handle == null) return;
        handle.future().cancel(false);
        try {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(handle.chatId())
                    .messageId(handle.messageId())
                    .text(handle.baseText() + " " + DOT_FRAMES[DOT_FRAMES.length - 1])
                    .build());
        } catch (TelegramApiException e) {
            logger.warn("Failed to freeze waiting message for chat {}: {}", handle.chatId(), e.getMessage());
        }
    }

    // --- Command Handlers ---

    private void handleStartCommand(long chatId) {
        logger.info("Received /start command from chat {}", chatId);

        // Delete the previous greeting to avoid stacking duplicate menus
        Integer oldMenuId = lastMenuMessageId.remove(chatId);
        if (oldMenuId != null) {
            try {
                telegramClient.execute(DeleteMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .messageId(oldMenuId)
                        .build());
            } catch (TelegramApiException e) {
                logger.warn("Could not delete old menu message {} for chat {}: {}", oldMenuId, chatId, e.getMessage());
            }
        }

        boolean isNewUser = !databaseService.hasLanguagePreference(chatId);

        if (isNewUser) {
            var greeting = getString("start.greeting", "EN")
                    + "\n\n"
                    + getString("start.greeting", "RU")
                    + "\n\n"
                    + getString("start.select_lang", "EN")
                    + " / "
                    + getString("start.select_lang", "RU");

            var row = new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text("English")
                            .callbackData(CALLBACK_LANG + "EN")
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("Русский")
                            .callbackData(CALLBACK_LANG + "RU")
                            .build()
            );

            var keyboard = InlineKeyboardMarkup.builder()
                    .keyboardRow(row)
                    .build();

            try {
                var sent = telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(greeting)
                        .replyMarkup(keyboard)
                        .build());
                lastMenuMessageId.put(chatId, sent.getMessageId());
            } catch (TelegramApiException e) {
                logger.error("Failed to send start greeting to chat {}", chatId, e);
            }
        } else {
            var lang = databaseService.getLanguage(chatId);
            try {
                var sent = telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(getString("start.greeting", lang))
                        .build());
                lastMenuMessageId.put(chatId, sent.getMessageId());
            } catch (TelegramApiException e) {
                logger.error("Failed to send start greeting to chat {}", chatId, e);
            }
        }
    }

    private void handleBestCommand(long chatId) {
        logger.info("Received /best command from chat {}", chatId);

        var lang = databaseService.getLanguage(chatId);

        var row = new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("2024")
                        .callbackData(CALLBACK_BEST_YEAR + "2024")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("2025")
                        .callbackData(CALLBACK_BEST_YEAR + "2025")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("2026")
                        .callbackData(CALLBACK_BEST_YEAR + "2026")
                        .build()
        );

        var keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(row)
                .build();

        var msg = SendMessage.builder()
                .chatId(chatId)
                .text(getString("best.select_year", lang))
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Failed to send year selection to chat {}", chatId, e);
        }
    }

    private void handleLanguageCommand(long chatId) {
        logger.info("Received /language command from chat {}", chatId);

        var lang = databaseService.getLanguage(chatId);

        var enLabel = getString("lang.en_label", lang);
        var ruLabel = getString("lang.ru_label", lang);

        if ("EN".equalsIgnoreCase(lang)) {
            enLabel += getString("lang.current_suffix", lang);
        } else {
            ruLabel += getString("lang.current_suffix", lang);
        }

        var row = new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(enLabel)
                        .callbackData(CALLBACK_LANG + "EN")
                        .build(),
                InlineKeyboardButton.builder()
                        .text(ruLabel)
                        .callbackData(CALLBACK_LANG + "RU")
                        .build()
        );

        var keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(row)
                .build();

        var msg = SendMessage.builder()
                .chatId(chatId)
                .text(getString("lang.choose", lang))
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Failed to send language selection to chat {}", chatId, e);
        }
    }

    private void handleResetCommand(long chatId) {
        logger.info("Received /reset command from chat {}", chatId);
        var lang = databaseService.getLanguage(chatId);
        databaseService.resetUserHistory(chatId);
        try {
            sendMessage(chatId, getString("reset.confirmation", lang));
        } catch (TelegramApiException e) {
            logger.error("Failed to send reset confirmation to chat {}", chatId, e);
        }
    }

    private void handleUnknownCommand(long chatId) {
        logger.info("Received unknown command from chat {}", chatId);
        var lang = databaseService.getLanguage(chatId);
        try {
            sendMessage(chatId, getString("unknown.command", lang));
        } catch (TelegramApiException e) {
            logger.error("Failed to send unknown command reply to chat {}", chatId, e);
        }
    }

    public void handleDigestCommand(long chatId) {
        logger.info("Received /digest command from chat {}", chatId);

        var lang = databaseService.getLanguage(chatId);
        var handle = startWaitingAnimation(chatId, lang);

        workerExecutor.submit(() -> {
            try {
                List<NewsArticle> allArticles = newsFetcherService.fetchAllArticles();

                List<NewsArticle> unprocessed = allArticles.stream()
                        .filter(article -> !databaseService.isUrlProcessed(chatId, article.url()))
                        .toList();

                if (unprocessed.isEmpty()) {
                    stopAnimation(handle);
                    sendMessage(chatId, getString("digest.no_news", lang));
                    return;
                }

                var digest = geminiService.generateDigest(unprocessed, lang);
                stopAnimation(handle);
                sendMessage(chatId, digest);

                for (var article : unprocessed) {
                    databaseService.saveArticle(article);
                    databaseService.markUrlAsProcessed(chatId, article.url());
                }
                logger.info("Marked {} articles as processed for chat {}", unprocessed.size(), chatId);

            } catch (Exception e) {
                stopAnimation(handle);
                logger.error("Error handling /digest command for chat {}", chatId, e);
                try {
                    sendMessage(chatId, getString("error.digest", lang));
                } catch (TelegramApiException ex) {
                    logger.error("Failed to send error message to chat {}", chatId, ex);
                }
            }
        });
    }

    // --- Callback Handlers ---

    private void onCallbackQuery(Update update) {
        var callback = update.getCallbackQuery();
        var data = callback.getData();
        var chatId = callback.getMessage().getChatId();
        var messageId = callback.getMessage().getMessageId();
        var callbackId = callback.getId();

        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .build());

            if (data.startsWith(CALLBACK_BEST_YEAR)) {
                handleYearSelected(chatId, messageId, data);
            } else if (data.startsWith(CALLBACK_BEST_MONTH)) {
                handleMonthSelected(chatId, messageId, data);
            } else if (data.equals(CALLBACK_BEST_BACK)) {
                handleBackToYearSelection(chatId, messageId);
            } else if (data.startsWith(CALLBACK_LANG)) {
                handleLanguageSelected(chatId, messageId, data);
            }
        } catch (TelegramApiException e) {
            logger.error("Error handling callback query in chat {}", chatId, e);
        }
    }

    private void handleLanguageSelected(long chatId, int messageId, String data) throws TelegramApiException {
        var lang = data.substring(CALLBACK_LANG.length());
        databaseService.setLanguage(chatId, lang);

        // Edit the current message (language picker or onboarding greeting) in-place
        // to become the localized greeting - no new message is sent.
        telegramClient.execute(EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(getString("start.greeting", lang))
                .build());

        lastMenuMessageId.put(chatId, messageId);
    }

    private void handleYearSelected(long chatId, int messageId, String data) throws TelegramApiException {
        int year = Integer.parseInt(data.substring(CALLBACK_BEST_YEAR.length()));
        var lang = databaseService.getLanguage(chatId);
        var monthNames = "RU".equalsIgnoreCase(lang) ? MONTH_NAMES_RU : MONTH_NAMES_EN;

        var now = LocalDate.now();
        // For the current year, only show months that have already passed or the current month
        int maxMonth = (year == now.getYear()) ? now.getMonthValue() : 12;

        var row1 = new InlineKeyboardRow();
        var row2 = new InlineKeyboardRow();
        var row3 = new InlineKeyboardRow();

        for (int m = 1; m <= maxMonth; m++) {
            var monthNum = String.format("%02d", m);
            var btn = InlineKeyboardButton.builder()
                    .text(monthNames[m - 1])
                    .callbackData(CALLBACK_BEST_MONTH + year + ":" + monthNum)
                    .build();
            if (m <= 4) row1.add(btn);
            else if (m <= 8) row2.add(btn);
            else row3.add(btn);
        }

        var backRow = new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(getString("best.back", lang))
                        .callbackData(CALLBACK_BEST_BACK)
                        .build()
        );

        var keyboardBuilder = InlineKeyboardMarkup.builder();
        keyboardBuilder.keyboardRow(row1);
        if (!row2.isEmpty()) keyboardBuilder.keyboardRow(row2);
        if (!row3.isEmpty()) keyboardBuilder.keyboardRow(row3);
        keyboardBuilder.keyboardRow(backRow);

        var edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(String.format(getString("best.select_month", lang), year))
                .replyMarkup(keyboardBuilder.build())
                .build();
        telegramClient.execute(edit);
    }

    private void handleBackToYearSelection(long chatId, int messageId) throws TelegramApiException {
        var lang = databaseService.getLanguage(chatId);

        var row = new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("2024")
                        .callbackData(CALLBACK_BEST_YEAR + "2024")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("2025")
                        .callbackData(CALLBACK_BEST_YEAR + "2025")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("2026")
                        .callbackData(CALLBACK_BEST_YEAR + "2026")
                        .build()
        );

        telegramClient.execute(EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(getString("best.select_year", lang))
                .replyMarkup(InlineKeyboardMarkup.builder().keyboardRow(row).build())
                .build());
    }

    private void handleMonthSelected(long chatId, int messageId, String data) throws TelegramApiException {
        var parts = data.substring(CALLBACK_BEST_MONTH.length()).split(":");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        var lang = databaseService.getLanguage(chatId);

        var monthNames = "RU".equalsIgnoreCase(lang) ? MONTH_NAMES_RU : MONTH_NAMES_EN;
        var selectedLabel = String.format(getString("best.selected", lang), monthNames[month - 1], year);

        // Remove the keyboard from the month selector message and confirm the selection
        telegramClient.execute(EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(selectedLabel)
                .build());

        var handle = startWaitingAnimation(chatId, lang);

        workerExecutor.submit(() -> {
            try {
                var articles = databaseService.getArticlesByMonth(year, month);

                String digest;
                if (articles.isEmpty()) {
                    digest = geminiService.findBestNewsFromWeb(year, month, lang);
                } else {
                    digest = geminiService.generateMonthlyDigest(articles, lang);
                }

                stopAnimation(handle);
                sendMessage(chatId, digest);

            } catch (Exception e) {
                stopAnimation(handle);
                logger.error("Error generating monthly digest for {}-{}", year, month, e);
                try {
                    sendMessage(chatId, getString("error.monthly_digest", lang));
                } catch (TelegramApiException ex) {
                    logger.error("Failed to send error to chat {}", chatId, ex);
                }
            }
        });
    }

    // --- Messaging Helpers ---

    /**
     * Splits the text on the two-blank-line separator that Gemini uses between news items,
     * sending each item as its own message so Telegram never collapses them.
     * Falls back to character-based chunking for items that are still too long.
     */
    private void sendMessage(long chatId, String text) throws TelegramApiException {
        // Three or more consecutive newlines mark the boundary between digest items
        String[] parts = text.split("\\n{3,}");
        if (parts.length > 1) {
            for (String part : parts) {
                var trimmed = part.strip();
                if (!trimmed.isEmpty()) {
                    sendChunked(chatId, trimmed);
                }
            }
        } else {
            sendChunked(chatId, text);
        }
    }

    private void sendChunked(long chatId, String text) throws TelegramApiException {
        if (text.length() <= TELEGRAM_MESSAGE_LIMIT) {
            executeSend(chatId, text);
            return;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + TELEGRAM_MESSAGE_LIMIT, text.length());
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start) {
                    end = lastNewline + 1;
                }
            }
            executeSend(chatId, text.substring(start, end));
            start = end;
        }
    }

    private void executeSend(long chatId, String text) throws TelegramApiException {
        var msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        telegramClient.execute(msg);
    }
}
