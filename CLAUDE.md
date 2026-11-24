# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Goozy is an Android virtual pet (tamagotchi-style). An animated goose lives as a screen overlay with AI behaviors, touch interactions, needs, evolution, mini-games and procedural rendering (no sprites — all Canvas draw calls). Its words and decisions come from a pluggable language-model "brain" (templates, on-device model, local server or cloud API).

**Package**: `com.skyvanguard.goozy` | **Java 11 source** | **Min SDK 24** | **Target/compile SDK 36**
**AGP**: 8.12.0 | **Gradle**: 8.13 | **Version**: 1.0.4 (versionCode 5)

## Build Requirements

- **The Gradle daemon must run on JDK 21**: `litertlm-android` ships Java 21 bytecode. `gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=21`, so Gradle picks an installed JDK 21 automatically even if `JAVA_HOME` points to JDK 17.
- Android SDK path goes in `local.properties` (`sdk.dir=C:/Android/sdk` on the dev machine — forward slashes).
- Core library desugaring is enabled (the Anthropic SDK uses `java.time`).

## Build & Test Commands

```bash
./gradlew assembleDebug                              # Debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest                          # All JVM unit tests
./gradlew testDebugUnitTest --tests "*GooseBrainTest" # One test class
./gradlew assembleRelease                            # Minified release (keystore in local.properties)
```

Release signing reads `keystore.path`, `keystore.password`, `keystore.alias`, `keystore.alias_password` from `local.properties`.

### Emulator tooling (`tools/`)

- `tools/smoke.sh` — installs the APK, enables the overlay, checks the goose animates outside the app and that logcat has no loop exceptions. Run after any change to rendering, lifecycle or the service.
- `tools/ui.sh tap|type|text|taptext|scrolltap|shot` — drive the UI by resource-id over adb.
- `tools/goosepos.sh` — prints the goose window center (for tap/drag tests).
- `tools/eval_models.py` — compares language models (through any OpenAI-compatible server, e.g. Ollama) on the real prompts. Generate the prompts first with `./gradlew testDebugUnitTest --tests "*PromptSamplesTest"`.

## Architecture

```
MainActivity  ── starts/stops ──▶  overlay/GooseOverlayService   (foreground service, type specialUse)
                                      ├── GooseLayerView WORLD   (full screen, not touchable: effects, text)
                                      ├── GooseLayerView GOOSE   (small touchable window that follows the goose)
                                      └── Choreographer frame loop → Time.tick → TheGoose.Tick/PrepareFrame
TheGoose (static coordinator)
    ├── GooseAI + GooseBehaviorTree   (single behavior tree, owned by GooseAI)
    ├── GoosePhysics, GooseRig, GooseRenderer (Layer.WORLD / Layer.GOOSE), GooseTouchHandler
    ├── GooseEasterEggs, GooseDreams, GooseTrolling, GooseSystemReactions, MiniGames, Sound
    └── brain/BrainController  ◀── game events (petted, fed, need critical, sleep, diary, chat)
brain/
    ├── GooseBrain         one thought at a time, rate limit, fallback to templates, backoff
    ├── PromptBuilder      stable system prompt + per-request user prompt; IntentSchema (JSON schema)
    ├── IntentParser       tolerant JSON parsing (prose, fences, <think>, truncation, typos)
    ├── BrainMemory        long-term facts + diary (JSON file, atomic writes)
    ├── BrainConfig        prefs; API keys in EncryptedSharedPreferences
    ├── BackendCatalog     the backend choices shown in BrainActivity
    ├── GooseVoice         TextToSpeech
    ├── backend/  TemplateBackend · LiteRtBackend (on-device .litertlm) · OpenAiCompatBackend
    │             (Ollama/LM Studio/OpenRouter/Groq/Gemini) · AnthropicBackend (official SDK)
    └── model/    LocalModelCatalog · ModelDownloader (resumable, verified) · ModelDownloads
PetRepository  — single source of truth for saved pet state (config.ini, merge + atomic write)
```

### Key rules

- **The render loop never calls a model.** Brain requests are async; intents are delivered on the main thread and mapped to existing tasks/events (`BrainAction`).
- **All pet state saves go through `PetRepository`** (Activity, service and widget share one process and one `PetState`).
- **Time**: `Time.deltaTime` is the real frame delta (capped at 50 ms); `Time.time` is game time in `double`. Timestamps stored for later comparison must be `double`.
- **Coordinates**: one world space, origin at the top-left of the WORLD window; screen = origin + world × `TheGoose.WorldScale`. The goose body is scaled by `TheGoose.DrawScale`.
- **Plain HTTP only to the local network** (`LocalNetwork`); cloud backends need https.
- **Static state**: `TheGoose.Init` calls `resetStaticState()`; anything static that must not survive an overlay restart goes there.

## Code Conventions

- Comments in Spanish (some older files still have English ones).
- `engine/` holds the goose engine, `core/` the small math/time helpers (`Vec2`, `MathUtil`, `ShuffleBag`, `Footprint`, `Time`); root package has activities and pet systems; `brain/` and `overlay/` are the AI and overlay layers.
- Sounds are synthesized by `tools/gen_sounds.py` into `app/src/main/assets/sound/`; regenerate instead of adding recorded audio.
- Dependencies via `gradle/libs.versions.toml` plus a few direct coordinates in `app/build.gradle`.
- Unit tests are plain JVM tests (JUnit 4, `org.json` and OkHttp `MockWebServer` on the test classpath); keep Android types out of classes you want to unit test.

## Docs

- `docs/auditoria-2026-09-27.md` — original bug audit.
- `docs/PLAN.md` — implementation plan, LLM research and phase status.
