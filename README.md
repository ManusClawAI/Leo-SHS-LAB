# 🦁 LEO — SHS LAB

### *The Android AI Assistant That Does Everything*

[![Platform: Android 10+](https://img.shields.io/badge/Platform-Android%2010%2B-green)]()
[![Language: Kotlin](https://img.shields.io/badge/Language-Kotlin-purple)]()
[![Providers: 100+](https://img.shields.io/badge/Providers-100%2B-blue)]()
[![Tools: 50+](https://img.shields.io/badge/Tools-50%2B-orange)]()

> *Your phone's new brain. 100+ AI providers. 50+ built-in tools. Offline GGUF support. Voice-activated. Memory-aware. Fully agentic.*

---

## 🎯 WHAT IS LEO?

Leo is a **production-grade Android AI assistant** that can:
- Chat with **100+ AI providers** (OpenAI, Claude, Gemini, NVIDIA NIM, DeepSeek, Groq, Mistral, and more)
- Run **GGUF models offline** (via Ollama, llama.cpp, LM Studio, vLLM, KoboldCPP, GPT4All)
- Control your phone with **50+ built-in tools** (files, apps, settings, calls, SMS, camera, shell, git, and more)
- Remember your preferences with a **Memory system**
- Execute **scheduled tasks** automatically
- Respond to **voice commands** (Siri-like activation)
- Speak responses with **fully functional TTS**
- Manage **chat history** with pin, archive, search, and delete

---

## 🎨 MODERN UI

Inspired by ChatGPT and Gemini — clean, minimal, premium:

- **3-dot menu** compresses all features (Overlay, Vault, Settings, Surveillance, Accessibility, Voice)
- **Slide-in drawer** (half screen) with chat sessions, library, scheduled tasks
- **Keyboard-aware input** that rises above the keyboard
- **File/image upload** directly in the chat input
- **Message actions**: edit, copy, regenerate, like/dislike, listen (TTS)
- **Chat history**: New Chat, search, pin, archive, delete, rename
- **Dark/Light/System** themes with custom message colors

---

## 🤖 AGENTIC CAPABILITIES

### Long-Horizon Task Support
Leo is designed for complex, multi-step tasks:
- Packs **maximum work into each request** (avoids wasting API calls)
- Completes as much as possible in **one request** (3-4 requests max for large tasks)
- **No unnecessary clarification questions** — makes reasonable assumptions

### Provider-Based Rate Limiting
- **NVIDIA NIM**: Enforces 40 RPM limit automatically
- **Other providers**: No artificial limit applied
- Rate limit awareness is **configurable** in Settings

---

## 🌐 100+ AI PROVIDERS

### Major Cloud Providers
OpenAI · Anthropic Claude · Google Gemini · Azure OpenAI · AWS Bedrock · Google Vertex AI

### Aggregator Platforms
OpenRouter · Together AI · Fireworks AI · Groq · NVIDIA NIM · Replicate · Anyscale · Hugging Face

### Direct Providers
Mistral AI · Cohere · DeepSeek · xAI Grok · Perplexity · AI21 Labs · Cerebras · SambaNova · Novita · Hyperbolic · Lepton · Chutes · Nebius · Infermatic · Kluster · and 40+ more

### Chinese Providers
Alibaba DashScope (Qwen) · Zhipu (ChatGLM) · Moonshot (Kimi) · Baichuan · MiniMax · StepFun · 01.AI (Yi) · ByteDance Doubao · Tencent Hunyuan · Baidu ERNIE · iFlytek Spark · SenseTime · ModelScope

### Local/Offline Providers
Ollama · LM Studio · llama.cpp (GGUF) · vLLM · Jan · GPT4All · KoboldCPP · TextGen WebUI · LocalAI · LiteLLM Proxy

---

## 🔧 50+ BUILT-IN TOOLS

| Category | Tools |
|----------|-------|
| **File** | read, write, delete, list, copy, move, mkdir, download |
| **App** | open, list, info, install, uninstall, clear cache |
| **System** | WiFi, Bluetooth, brightness, volume, flashlight, rotation, DND, battery saver, airplane mode |
| **Communication** | call, SMS, email, contacts |
| **Calendar** | alarms, timers, calendar events |
| **Hardware** | device info, battery, camera, screenshot, vibrate |
| **Git** | clone, commit, push, create repo, upload file |
| **Shell** | execute commands, root commands, run scripts |
| **Web** | open URL, Google search, fetch content |
| **Media** | play, pause, next, previous |
| **Notifications** | send, clear all |
| **Clipboard** | copy, paste |
| **Network** | info, speed test, ping |
| **UI** | click, swipe, type text, read screen, screenshot |
| **Memory** | save, recall, clear |

---

## 🧠 MEMORY SYSTEM

- **ON/OFF toggle** in Settings
- **Manual add**: Add memories directly in Settings
- **Auto-save**: Leo detects phrases like "I like...", "remember...", "I prefer..." and saves automatically
- **Injection**: Saved memories are injected into the AI's system prompt for all future interactions

---

## 🗣️ VOICE & TTS

### Voice Input (STT)
- Uses Android's built-in speech recognizer
- Mic button in chat input
- Siri-like activation via Default Assistant

### Voice Output (TTS)
- **Fully functional** using Android TextToSpeech engine (works offline)
- Streaming: queues text chunks as the model produces them
- Adjustable speed and pitch
- Toggle in 3-dot menu or Settings

---

## 💬 CHAT FEATURES

### Message Actions
**User messages:**
- Edit
- Copy selected text
- Copy entire message

**Agent messages:**
- Copy selected text
- Listen (TTS)
- Regenerate

### Chat Bar
- **Regenerate** button
- **Like** button
- **Dislike** button
- **Mic** button (voice output toggle)

### Chat History
- **New Chat** icon at top
- Every chat session saved automatically
- **Search** chats
- **Pin** important chats
- **Archive** old chats
- **Delete** chats
- **Rename** chats
- **View uploaded files** per session

---

## 📂 LEFT SLIDE-IN MENU

### Two Menu Interaction
- **Open**: Swipe right-to-left OR tap left edge
- **Close**: Swipe left-to-right OR tap close
- Menu occupies **half the screen**

### Menu Content
**Top:**
- App icon + name (left)
- Search icon (right)

**Below:**
- **Library** — View all uploaded files, download/delete individual or multiple
- **Scheduled Tasks** — Create tasks with date/time

**Chat Lists:**
- **Pinned Chats** — All pinned sessions
- **Normal Chats** — Scrollable list of active sessions

**Bottom (fixed):**
- **New Chat** icon (left)
- **Profile** icon (right) — Opens main Settings

---

## ⚙️ SETTINGS (PROFILE)

### Personalization
Save custom instructions that Leo follows in all interactions.

### Behavior
5 default behavior presets + custom:
- Professional
- Friendly
- Concise
- Creative
- Custom

### Memory
- ON/OFF toggle
- View, add, delete memories

### Theme
- Light
- Dark
- System

### Color
Customize background color of:
- User-sent messages
- Agent output messages

### General
- App language
- Tools ON/OFF

### Notifications
- New messages
- Completed work
- Completed tasks
- Task reminders

### Archived Chats
- View archived chats
- Restore archived chats
- Delete archived chats

### Leo Settings
- Provider selection (100+ providers)
- Base URL
- API key
- Model name
- Offline GGUF model upload + Run
- Agent name
- User name (how Leo addresses you)

---

## 📅 SCHEDULED TASKS

- Create tasks with specific date and time
- Leo executes the task when the scheduled time arrives
- Notification on completion
- View, delete scheduled tasks
- Uses WorkManager for reliable background execution

---

## 🏗️ ARCHITECTURE

```
leo-shs-lab/
├── android-leo/
│   ├── app/src/main/java/com/shslab/leo/
│   │   ├── MainActivity.kt           # Modern chat UI with drawer
│   │   ├── SettingsActivity.kt       # Full settings screen
│   │   ├── ScheduleActivity.kt       # Scheduled tasks UI
│   │   ├── VaultActivity.kt          # Encrypted vault
│   │   ├── SurveillanceActivity.kt   # Surveillance mode
│   │   ├── LeoApplication.kt         # App entry, init
│   │   │
│   │   ├── network/
│   │   │   ├── ProviderRegistry.kt   # 100+ providers
│   │   │   └── LeoNetworkClient.kt   # Rate-limit-aware agentic client
│   │   │
│   │   ├── security/
│   │   │   └── SecurityManager.kt    # AES-256 encrypted vault v2
│   │   │
│   │   ├── chat/
│   │   │   ├── ChatDatabase.kt       # SQLite: sessions, messages, files
│   │   │   ├── ChatAdapter.kt        # Message adapter with actions
│   │   │   ├── SessionAdapter.kt     # Drawer session list
│   │   │   └── ChatMessage.kt        # Data models
│   │   │
│   │   ├── tools/
│   │   │   └── ToolRegistry.kt       # 50+ built-in tools
│   │   │
│   │   ├── memory/
│   │   │   └── MemoryManager.kt      # Preferences store + auto-save
│   │   │
│   │   ├── voice/
│   │   │   ├── LeoTtsManager.kt      # Android TTS (fully functional)
│   │   │   ├── SpeechManager.kt      # STT manager
│   │   │   ├── LeoVoiceService.kt    # Default Assistant service
│   │   │   └── ...                   # Piper/Sherpa/Vosk voice models
│   │   │
│   │   ├── schedule/
│   │   │   ├── ScheduleManager.kt    # Task scheduling
│   │   │   ├── ScheduleReceiver.kt   # Alarm receiver
│   │   │   ├── ScheduleService.kt    # Task execution service
│   │   │   └── ScheduledTaskWorker.kt# WorkManager worker
│   │   │
│   │   ├── overlay/                  # Dynamic Island + Siri bubble
│   │   ├── accessibility/            # God-Mode UI control
│   │   ├── executor/                 # Action dispatcher + command queue
│   │   ├── parser/                   # JSON command parser
│   │   ├── file/                     # File engine
│   │   ├── git/                      # GitHub REST API
│   │   ├── shell/                    # Shell bridge
│   │   ├── hardware/                 # Hardware manager
│   │   ├── browser/                  # Browser engine
│   │   ├── automation/               # Notification listener + social media
│   │   ├── cognitive/                # Deletion safety gate
│   │   ├── persona/                  # Doraemon persona
│   │   └── core/                     # Logger, protocol, boot receiver
│   │
│   └── app/src/main/res/
│       ├── layout/                   # Modern UI layouts
│       ├── drawable/                 # Vector icons + shapes
│       ├── values/                   # Colors, themes, strings
│       └── menu/                     # 3-dot menu
│
├── build_and_release.sh              # Build script
├── deploy_api_upload.sh              # Deploy script
└── deploy_to_github.sh               # GitHub deploy script
```

---

## 🚀 BUILD INSTRUCTIONS

### Prerequisites
- Android Studio (Hedgehog or newer)
- JDK 17+
- Android SDK 34
- Kotlin 1.9+

### Build
```bash
cd android-leo
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

### Universal APK (all architectures)
```bash
./gradlew assembleDebug
# Splits config generates per-ABI + universal APK
```

---

## 📱 REQUIREMENTS

- Android 10+ (API 29+)
- ~100 MB free space
- Internet connection (for cloud providers)
- Optional: Local LLM server for offline use

---

## 🔒 PERMISSIONS

Leo requires extensive permissions for its agentic capabilities:
- **INTERNET** — AI provider communication
- **RECORD_AUDIO** — Voice input
- **CALL_PHONE** — Make calls
- **CAMERA** — Take photos
- **SYSTEM_ALERT_WINDOW** — Overlay bubble
- **BIND_ACCESSIBILITY_SERVICE** — UI automation
- **MANAGE_EXTERNAL_STORAGE** — File access
- **WRITE_SETTINGS** — System setting changes
- **SCHEDULE_EXACT_ALARM** — Scheduled tasks
- **POST_NOTIFICATIONS** — Task notifications

---

## 📜 LICENSE

Proprietary — SHS LAB. All rights reserved.

---

## 🙏 ACKNOWLEDGEMENTS

- OpenFL/Lime for inspiration on cross-platform architecture
- All AI providers for their APIs
- The open-source community for tools and libraries
