Here is the updated README.md file in English. It follows all your formatting constraints: no bold text, no long dashes, and no angle quotes.

PROJECT DESCRIPTION: AI TECH DIGEST BOT
This is a professional Telegram bot designed to help you stay updated on IT news without the noise. It uses the gemini-1.5-flash-latest model to transform complex technical articles into short, 1-2 sentence summaries that anyone can understand.

CORE FEATURES
Smart Summaries: Uses gemini-flash-latest to explain tech news in simple terms (ELI5 style).

Multi-Language Support: Full support for English and Russian for both the interface and the news content.

Historical Archive: The /best command allows you to find the most viral and impactful news from any specific month and year.

Smart Date Logic: The month selection menu automatically hides future months for the current year (2026).

Animated UI: Features a cycling animation for waiting messages that automatically deletes itself once the digest is ready.

Reading History: Uses an SQLite database to track what you have read so you never see the same news twice.

TECHNICAL STACK
Programming Language: Java 17.

AI Engine: Google Gemini API (via LangChain4j).

Database: SQLite for storing user settings and article history.

Platform: TelegramBots API (Long Polling).

Configuration: Dotenv for secure API key management.

INSTALLATION GUIDE
Clone this repository to your local machine.

Create a file named .env in the root directory of the project.

Add your credentials to the .env file:
BOT_TOKEN=your_telegram_bot_token
GEMINI_API_KEY=your_google_gemini_api_key

Build the project using Maven.

Run the Main class to start the bot.

AVAILABLE COMMANDS
/start: Welcome message, brief instructions, and a full list of commands.
/digest: Get the latest tech news summarized in 1-2 simple sentences.
/best: Access a searchable archive of the most viral tech stories by month and year.
/language: Toggle the interface and news language between English and Russian.
/reset: Clear your reading history to view previous articles again.

FORMATTING RULES
The bot follows strict rules for all AI-generated responses:

No bold text is allowed.

Use only short dashes (-).

Use standard double quotes (").

Maximum of 2 emojis per post.

Neutral and professional tone.