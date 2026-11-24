# AGENTS.md — Goozy

Mascota virtual en Android: ganso dibujado 100% con Canvas
(sin sprites) que vive como overlay del sistema, con "cerebro" LLM enchufable.
App Java de un solo módulo (`:app`), paquete `com.skyvanguard.goozy`, Java 11,
minSdk 24, compile/target 36, AGP 8.12.0, Gradle 8.13, version 1.0.4 (versionCode 5).
Guía extendida: `CLAUDE.md` (en inglés). Sin CI en el repo.

## Build

- **El daemon de Gradle debe correr en JDK 21**: `litertlm-android` trae
  bytecode de Java 21. `gradle/gradle-daemon-jvm.properties` fija
  `toolchainVersion=21`, así Gradle elige un JDK 21 instalado aunque `JAVA_HOME`
  apunte a otro. Si el build falla por versión de Java, que haya JDK 21 instalado.
- `local.properties` (no versionada): `sdk.dir` con barras forward; opcionalmente
  `keystore.path` / `keystore.password` / `keystore.alias` / `keystore.alias_password`.
  Sin keystore, `assembleRelease` genera `app-release-unsigned.apk` (sin firmar).
- Core library desugaring activado (el SDK de Anthropic usa `java.time`);
  no tocar `coreLibraryDesugaringEnabled`.

```bash
./gradlew assembleDebug                                   # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                               # tests JVM
./gradlew testDebugUnitTest --tests "*GooseBrainTest"     # una clase
./gradlew assembleRelease                                 # minify + shrink
```

## Tooling de emulador (Windows + Git Bash)

Scripts de `tools/` son bash para Git Bash (usan `MSYS_NO_PATHCONV=1`); por
defecto `ADB=/c/Android/sdk/platform-tools/adb.exe`, sobreescribible con la env `ADB`.
Requieren emulador/dispositivo conectado.

- `tools/smoke.sh` — instala el debug, enciende el overlay, verifica que el ganso
  se anima fuera de la app (dos screenshots distintos) y sin `Error during
  render|tick`/`FATAL` en logcat. **Correrlo tras tocar render, lifecycle o el servicio.**
- `tools/ui.sh tap|type|text|taptext|scrolltap|scroll|shot` — maneja la UI por
  resource-id vía adb (el id sin prefijo de paquete).
- `tools/goosepos.sh` — centro (x y) y tamaño de la ventana chica del ganso
  (para taps/drags de test).
- `tools/eval_models.py` — compara modelos con los prompts reales. Primero
  generarlos: `./gradlew testDebugUnitTest --tests "*PromptSamplesTest"`
  (escribe `app/build/prompt-samples.json`), luego
  `python tools/eval_models.py <modelo> --url http://localhost:11434/v1`.

## Arquitectura (lo que no se nota por el nombre)

```
MainActivity ──▶ overlay/GooseOverlayService (foreground service, type specialUse)
    ├── GooseLayerView WORLD  (full screen, no touchable: efectos, texto)
    └── GooseLayerView GOOSE  (ventana chica touchable que sigue al ganso)
    Loop de frames: Choreographer → Time.tick → TheGoose.Tick/PrepareFrame
```

- `engine/` — el motor del ganso; `TheGoose` es el coordinador **estático**
  (AI, behavior tree, physics, rig, renderer, touch, minigames).
- `core/` — helpers bajos del motor: `Time`, `Vec2`, `MathUtil`, `ShuffleBag`, `Footprint`.
- Sonidos: los genera `tools/gen_sounds.py` en `assets/sound/`; no agregar audio grabado.
- `brain/` — el cerebro LLM: `GooseBrain` (un pensamiento a la vez, rate limit,
  backoff, fallback a plantillas), `PromptBuilder`/`IntentParser` (prompt estable
  + JSON tolerante), `BrainMemory` (hechos + diario), `backend/` (template,
  LiteRT-LM on-device, OpenAI-compatible, Anthropic), `model/` (descarga de
  modelos locales, reanudable).
- Paquete raíz — activities y sistemas de mascota (`PetRepository` es la única
  fuente de estado guardado: escritura atómica; Activity, servicio y widget
  comparten proceso y `PetState`).

### Reglas que rompen al tocar el motor

- **El loop de render nunca llama a un modelo.** Las peticiones al brain son
  async; la intención llega por el executor de entrega (hilo principal) y se
  mapea a tareas existentes (`BrainAction`).
- **Tiempo**: `Time.deltaTime` es el delta real de frame con tope de 50 ms;
  `Time.time` es tiempo de juego en `double`. Todo timestamp que se vaya a
  comparar después se guarda como `double`.
- **Coordenadas**: un solo espacio mundial, origen arriba-izquierda de la
  ventana WORLD; pantalla = origen + mundo × `TheGoose.WorldScale` (2.5).
  El cuerpo del ganso se escala por `TheGoose.DrawScale`.
- **HTTP plano solo a red local**: `LocalNetwork.isLocalUrl` (localhost,
  `.local`, `.lan`, IPv4 privado literal); los backends cloud piden https.
- **Estado estático**: `TheGoose.Init` llama a `resetStaticState()`; lo que no
  debe sobrevivir un reinicio del overlay se resetea ahí.

## Tests

- Tests JVM puros (JUnit 4, `org.json`, OkHttp `MockWebServer` en test classpath).
  Mantener tipos de Android fuera de las clases que se quieren unit testear.
- La verificación estándar es `testDebugUnitTest`; si cambió el overlay,
  completar con `tools/smoke.sh` en emulador.

## Convenciones

- Commits convencionales con mensajes en español (`feat:`, `fix(seguridad):`,
  `chore:`). Ramas: `main` y `goozy` (la de trabajo actual).
- Comentarios en español (algunos archivos viejos todavía en inglés).
- Dependencias: `gradle/libs.versions.toml` + algunas coordenadas directas en
  `app/build.gradle` (litertlm, anthropic, security-crypto, desugar).

## Docs

- `docs/GUIA.md` — guía de usuario (español).
- `docs/PLAN.md` — plan de implementación, research de LLMs y estado.
- `docs/auditoria-2026-09-27.md` — auditoría de bugs original.
