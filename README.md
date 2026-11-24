# Goozy 🪿

A chaotic, caring goose that lives **on top of your phone screen**. Goozy walks over your apps, honks, steals your attention and needs your care like a tamagotchi — and its words and decisions come from a language model you choose, which can run entirely on your phone. No sprites: the goose is drawn procedurally with Canvas, and every sound is synthesized by [`tools/gen_sounds.py`](tools/gen_sounds.py).

## Features

**Living Overlay** — The goose roams your screen as a system overlay, walking over your apps, honking, and causing mischief.

**Procedural Animation** — Fully rendered with Android Canvas draw calls. Skeletal rig system with bones, expressions, poses, and foot IK. No bitmaps for the goose itself.

**AI Behaviors** — Priority-based behavior tree drives decisions. The goose wanders, reacts to touch, sleeps, eats, plays, trolls you, and adapts to time of day, battery level, and inactivity.

**AI Brain** — What the goose says and decides comes from a pluggable language model: built-in templates, an on-device model (LiteRT-LM: Gemma 4, Qwen3, LFM2.5), Ollama on your PC, any OpenAI-compatible server, Gemini, or Claude. It remembers what you tell it, writes a nightly diary, dreams, brings you notes and can read its words aloud. If a model fails, it falls back to templates — the goose is never mute.

**Talk to It** — Hold the goose still for half a second to open a small chat window on top of any app.

**Tamagotchi Needs** — Hunger, energy, happiness, hygiene and health, on a scale of hours (not minutes). It sleeps at night by itself, gets dirty in the mud, can get sick if neglected, and never "dies" from a night without attention.

**Touch Interactions** — Pet, drag, throw, boop, tickle, belly rub, and more. Each gesture has unique responses and affects personality.

**Personality Evolution** — Traits like playfulness, affection, bravery, and mischief evolve based on how you interact. Range from -100 to +100.

**Evolution Stages** — Egg → Hatchling → Gosling → Adult → Elder → Legendary → Cosmic. Your goose grows over time.

**Mini-Games** — Feeding, chasing, catching, hide & seek, honk hero, and memory honk.

**Visual Effects** — Particle systems for hearts, sparkles, confetti, dust trails, and more.

**Sound System** — Extended honk types, footsteps, and emotional sounds.

**Easter Eggs** — Secret modes, hidden interactions, and the Konami code.

**Trolling** — Fake notifications, vibration, and troll messages. It's a goose, after all.

**Home Screen Widget** — Quick feed/play/sleep actions without opening the app.

**Customization** — Name your goose, change colors, pick hats, accessories, and creature types.

## Screenshots

> Coming soon

## Architecture

```
engine/TheGoose (central coordinator)
├── GooseAI             — Modular behavior system with memory and routines
├── GooseBehaviorTree   — Priority-based: critical needs → interaction → personality → idle
├── GoosePhysics        — Movement, jumping, bouncing, friction, surface types
├── GooseRig            — Skeletal system: bones, expressions, poses
├── GooseRenderer       — Procedural drawing, particles, trails, glow
├── GooseTouchHandler   — Gesture recognition (tap, pet, drag, throw, boop, tickle...)
├── GooseVisualEffects  — Particle effects (hearts, sparkles, confetti, dust)
├── GooseSoundEffects   — Honks, footsteps, emotional sounds
├── GooseDreams         — Sleep thought bubbles based on daily experiences
├── GooseEasterEggs     — Secret modes and hidden interactions
├── GooseTrolling       — Fake notifications, vibration, troll messages
├── GooseSystemReactions— Reacts to battery, time of day, inactivity
├── GooseNotes          — Notes the goose drags in from the screen edge
├── NightRoutine        — Sleeps by itself at night
└── MiniGames           — Feeding, chasing, hide & seek, honk hero, memory honk

overlay/GooseOverlayService — Foreground service that owns the overlay (two windows:
                              full-screen effects + a small touchable window that follows the goose)

brain/                  — Language-model brain
├── GooseBrain          — One thought at a time, rate limit, fallback, backoff
├── PromptBuilder       — Stable system prompt + JSON schema for the reply
├── IntentParser        — Tolerant parsing of model output
├── BrainMemory         — Long-term memory and diary
└── backend/            — Templates, LiteRT-LM, OpenAI-compatible, Anthropic

Pet Systems:
├── PetNeeds            — Hunger, energy, happiness, hygiene, health
├── PetRepository       — Single source of truth for saved state (atomic writes)
├── PetPersonality      — Evolving traits [-100..+100]
├── GooseEvolution      — Growth stages from Egg to Cosmic
├── PetAppearance       — Colors, hats, accessories, creature types
└── PetWidget           — Home screen widget
```

## Tech Stack

| | |
|---|---|
| **Language** | Java 11 |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 36 |
| **Build** | Gradle 8.13 + AGP 8.12.0, Gradle daemon on JDK 21 (picked automatically) |
| **UI** | Android Canvas (procedural rendering) |
| **AI** | Behavior tree + pluggable LLM brain (LiteRT-LM, OpenAI-compatible, Anthropic SDK) |
| **Physics** | Custom physics engine with IK |
| **Audio** | SoundPool with synthesized sounds |

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore config in local.properties)
./gradlew assembleRelease

# Run tests
./gradlew testDebugUnitTest
```

Building requires JDK 21 installed (the LiteRT-LM library ships Java 21 bytecode); `gradle/gradle-daemon-jvm.properties` makes Gradle pick it even if `JAVA_HOME` points elsewhere.

The APK will be generated in `app/build/outputs/apk/`.

## Installation

1. Build the APK or download from releases
2. Install on your Android device
3. Grant overlay permission when prompted
4. Configure your pet in the settings panel
5. Toggle the goose on!

## Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Screen overlay |
| `VIBRATE` | Haptic feedback & trolling |
| `POST_NOTIFICATIONS` | Pet need alerts |
| `SCHEDULE_EXACT_ALARM` | Timed reminders |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the goose on screen |
| `RECEIVE_BOOT_COMPLETED` | Bring the goose back after a reboot, if it was on |
| `INTERNET` | Remote brains and model downloads (plain HTTP only to the local network) |

## Documentation

- [`docs/GUIA.md`](docs/GUIA.md) — user guide (Spanish): install, HyperOS settings, choosing a brain, features.
- [`docs/PLAN.md`](docs/PLAN.md) — implementation plan, LLM research, measurements and status.
- [`docs/auditoria-2026-09-27.md`](docs/auditoria-2026-09-27.md) — the original bug audit.

## Troubleshooting

1. **Goose not appearing**: Check that overlay permission is granted in Settings > Apps > Goozy > Display over other apps
2. **Touch not working**: Enable "Touchable" option in the app settings
3. **App crashes**: Create an issue with logs from the error dialog

## License

All rights reserved. This source code is provided for educational and reference purposes.
