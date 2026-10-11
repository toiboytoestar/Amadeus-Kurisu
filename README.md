# Amadeus: Kurisu, your AI study & life assistant

An Android app that brings the *Steins;Gate 0* **Amadeus** Kurisu to your phone and makes her useful:
she listens, thinks and **talks back in real time**, helps you study and research, and keeps your day organised.

**Made by TOIBOY TOESTAR** · Instagram [@toiboytoestar](https://www.instagram.com/toiboytoestar/)

This project is a fork of, and builds on, [Yink/Amadeus](https://github.com/Yink/Amadeus) (the original cosplay-focused app, GPL-3.0).
Package / application ID: `com.toiboytoestar.kurisu`.

## What's new

**Real-time conversation**
- Live speech recognition with partial transcripts and a mic-level pulse.
- Replies stream from the AI and are **spoken sentence by sentence while still being generated**, so she starts talking after the first sentence.
- Barge-in (tap the mic to interrupt her), hands-free **AUTO** mode that re-listens after every answer, typing fallback.
- Expressive avatar: the model picks her mood per sentence (16 expressions), mouth flaps while she talks, she blinks and breathes.
- Speaks and understands English, Hindi, Japanese, German, French, Spanish, Portuguese, Italian, Russian, Chinese, Korean (depends on your phone's speech engine).

**Daily life**
- Voice commands that work offline: timers, reminders, alarms, tasks, notes, "remember that...", open apps, web search, time and date.
- Tasks with due-time notifications, a calendar-event helper, a Pomodoro focus timer.
- **Wake-up call**: Kurisu phones you at a set time (original ringtones); answer and she briefs you on your agenda.
- She remembers things about you (editable in Settings) and knows your agenda when planning your day.

**Study & research**
- Explain, quiz-me and plan-my-day modes; ask in your own words.
- Flashcards with spaced repetition (SM-2); ask her to "make flashcards about..." and she builds the deck.
- Research tools: **Wikipedia**, **arXiv paper search** and **web page reading**, no API keys needed. She is instructed never to invent citations.
- Share any text or link to Kurisu from another app, or select text and choose "Ask Kurisu", to get a summary or explanation.
- Notes (she saves long answers there), conversation log, share/export.

**Still here from the original:** the call screen, the Steins;Gate voice lines for easter eggs (Christina, nullpo, @channel...), the original ringtones, and an offline mode that plays the recorded lines when no AI is configured.

### Added in 1.2

- **Photo and PDF help**: attach a homework photo or a PDF (first 6 pages) and Kurisu reads it (needs a vision model: Gemini and Claude work).
- **Study planner**: add exams in the Hub; countdowns appear in the morning briefing, the widget and her study plans.
- **Evening check-in and streak**: optional nightly nudge, a day-streak counter, and a weekly summary on Sundays.
- **Voice notes**: dictate for as long as you like and she saves a clean note (and tasks).
- **Memory manager**: see, add and delete what she remembers (Hub > MEMORY), or say "forget ...".
- **"Hey Kurisu" mode**: hands-free listening that only answers when addressed (works while the app is open on screen).
- **Stress-aware replies**: she responds gently when you sound stressed, especially late at night.
- **Home-screen widget**: nearest exam, open tasks, streak and a one-tap talk button.
- **Natural voice**: optional expressive voice through the Gemini speech API, falling back to the phone voice.
- **Situational expressions**: her face reacts to what you say and to her own replies.

## Setup

1. Install the APK (allow installation from unknown sources).
2. Tap the Amadeus logo, then **AI brain**. Pick a provider and paste your API key, then **Test connection** (a successful test also saves it).
   - **No credits? Start free:** Google Gemini and Groq have free tiers (no credit card), and Ollama runs locally. Anthropic and OpenAI need paid credits on your account.
   - Works with **Anthropic (Claude)**, **OpenAI**, **Google Gemini**, **Groq**, **OpenRouter**, or any **OpenAI-compatible server** (e.g. Ollama on your computer, no key needed).
   - Some providers have a free tier. The key is stored only on your device and sent only to the provider you choose.
3. Choose your speech language and, optionally, a voice and your name.
4. Connect and talk.

Voice quality comes from your phone's text-to-speech engine (Settings > Voice picks among installed voices); the recorded game lines are the only "real" Kurisu voice.

## Building

```
# verified path, no Gradle needed (apt install aapt dalvik-exchange zipalign apksigner)
ANDROID_JAR=/path/to/android-34/android.jar ./tools/build_apk.sh

# unit tests for the pure-Java core (stream parser, LLM clients against a mock server, intents, SM-2, ...)
./tools/test.sh
```
`build.gradle` / `app/build.gradle` were modernised (AGP 8.5, no support-library dependency) but the Gradle build has **not** been run; the script above is what produced the released APK. The app uses only framework APIs.

The release APK is signed with a throwaway debug key generated by the script. To publish your own builds, create a keystore and run `KEYSTORE=/path/to/my.jks KS_PASS=... ./tools/build_apk.sh` (an APK signed with a different key cannot be installed over one signed with another).

Known limitations: parts of the app were built and unit-tested without a physical device, so expect rough edges (if the app crashes, the next launch shows the error); the "Hey Kurisu" mode only listens while the app is open; offline command confirmations are English; no API key means canned-lines mode; reminders appear in the system "next alarm" indicator because exact delivery uses `setAlarmClock`.

# Credits (original project)
<ul>
  <li><a href="https://github.com/RIP95">Emojikage</a> for various improvements and advice, Russian translation</li>
  <li><a href="https://github.com/8BitAsian/">8-Bit☆Asian</a> for Zero and S;G Voice Lines and the Website</li>
  <li><a href="https://twitter.com/SG_EPK_X29">EPK</a> for Phenogram Voice Lines</li>
  <li><a href="https://twitter.com/DavixxaYT">Davixxa</a> for Assets</li>
  <li>But He's a Guy and Deadeye for finding the hidden animation in 0</li>
  <li>MARIORUI11 for the Portuguese (Portugal) translation</li>
  <li><a href="https://twitter.com/TehVict">TehVict</a> for the Portuguese (Brazil) translation</li>
  <li><a href="https://twitter.com/gioghurt">Gioghurt</a> for the Spanish translation</li>
  <li><a href="https://twitter.com/DelusionParadox">Ice</a> for a revision of the Spanish translation</li>
  <li><a href="https://twitter.com/AlternisDim96">Alternis Dim96</a> for the Italian translation</li>
  <li>Woute and Okami Amaterasu for the French translation</li>
  <li>History-exe for the Chinese (Simplified) translation</li>
  <li>jaidTw and ekrekeler for the Chinese (Traditional) translation</li>
  <li>Mk-Chan and alexsh for refactoring</li>
  <li><a href="https://twitter.com/rintarokabe">Tiffylenia</a> for the German translation</li>
  <!--<li><a href="https://twitter.com/Chaos_World_300">Rigs</a> for the Japanese subtitles</li>-->
</ul>
