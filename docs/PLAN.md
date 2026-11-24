# Plan de implementación — Goozy como tamagotchi con IA

Fecha: 2026-09-27 · Rama de trabajo: `estabilizacion` · Hallazgos de origen: [auditoria-2026-09-27.md](auditoria-2026-09-27.md)

## Estado (actualizado 2026-09-27)

| Fase | Estado | Qué quedó |
|---|---|---|
| 0 · Base | ✅ | AGP 8.12, compileSdk 36, JDK 21 automático, `tools/smoke.sh` |
| 1 · Destrabar | ✅ | Eventos que terminan, árbol reactivo, cooldowns críticos, tareas con timeout, sin bucle infinito, audio seguro |
| 2 · Una sola IA | ✅ | Un behavior tree, timers reseteados al interrumpir, modos secretos con duración, Seeking con cooldown |
| 3 · Reloj | ✅ | Delta real con tope, tiempo en `double` |
| 4 · Ciclo de vida | ✅ | Foreground service, `destroy()` real, `PetRepository` con escritura atómica, logros persistidos, permiso de notificaciones, notificaciones troll con cooldown de 30 min e ID fijo, vuelve solo tras reiniciar o actualizar |
| 5 · Pantalla y toques | ✅ | Una escala, ventana chica táctil que sigue al ganso, rotación, pointer ids |
| 6 · Tamagotchi | ✅ | Tasas en horas, offline con pisos, evolución conectada, hitos, sueño nocturno automático, higiene (barro, baño) y salud (enfermarse, remedio) |
| 7 · Rendimiento | ✅ salvo gradientes | 0 Paint/Path/RectF/BlurMaskFilter por frame; batería cacheada 1 min; chequeos por reloj cada 20 s. Quedan 24 gradientes por frame (dependen de la posición) |
| 8 · Núcleo del cerebro | ✅ | Interfaz, orquestador, parser tolerante, memoria, esquema JSON |
| 9 · Backends remotos | ✅ | OpenAI-compatible (Ollama, LM Studio, OpenRouter, Groq), Gemini, Claude (SDK oficial) |
| 10 · En el teléfono | 🟡 | Hecho: LiteRT-LM con 5 modelos descargables. Falta: llama.cpp (GGUF) |
| 11 · Pantalla de IA | ✅ | Backend, modelo, clave cifrada, GPU, probar, memoria, diario |
| 12 · Funciones | 🟡 | Hecho: chat, chat rápido con pulsación larga, memoria, diario, sueños, voz, notas en pantalla (escritas por la IA). Falta: reaccionar a notificaciones y a la app en uso (opcionales, requieren permisos sensibles) |

### Mediciones en emulador (x86_64, sin GPU)

| Modelo en el teléfono | Carga | Respuesta | Calidad observada |
|---|---|---|---|
| Qwen3 0.6B | 2,4 s | ~4 s | Pobre: frases sin sentido, a veces solo puntuación |
| LFM2.5 1.2B | 4,8 s | ~6-7 s | Floja: mezcla inglés, repite la necesidad dominante |
| Gemma 4 E2B | 3,1 s | ~5 s | Buena: frases con carácter y en español; guarda bien lo que le contás |

En la PC con Ollama (RTX 5070 Ti), gemma3:4b y qwen3:4b responden en ~2,5 s con JSON válido el 100 % de las veces.

**Recomendación para el POCO F5 Pro (12 GB):** Gemma 4 E2B como modelo en el teléfono; Ollama en la PC cuando estés en casa; Claude cuando quieras la mejor conversación.

## Objetivo

Una mascota virtual que vive sobre las demás apps, que **no se traba con el uso**, que se cuida como un tamagotchi (en escala de horas y días, no de minutos) y cuyo comportamiento, frases y memoria los produce un **modelo de lenguaje intercambiable**: en el teléfono, en la PC de casa o en la nube.

## Entorno verificado

| Elemento | Estado |
|---|---|
| Android SDK | `C:\Android\sdk` — plataformas 33-36, build-tools 33-36, NDK 23.1 y 26.3, CMake |
| JDK | Temurin 17.0.19 |
| Build | `./gradlew assembleDebug testDebugUnitTest` compila; 145 tests pasan |
| Emulador | AVD `zeroad-small` (Android 14, x86_64, 1080×2400) |
| Teléfono objetivo | POCO F5 Pro — Snapdragon 8+ Gen 1, 12 GB RAM reales (la expansión de 12 GB es swap en disco: sirve para no cerrar apps, no para inferencia) |
| AGP real | 8.0.0 (el `CLAUDE.md` decía 8.12.0) |

## Línea base medida en emulador (instalación limpia)

1. El ganso **no se dibuja nunca**: etapa huevo = escala 0 → `IllegalArgumentException: ending radius must be > 0` en `GooseRenderer.renderShadow:880`, una vez por frame.
2. Al ir al home, el overlay **queda congelado**: dos capturas separadas por 4 s son idénticas.

## Reglas de trabajo

- Cada fase termina con: build limpio, tests unitarios en verde y prueba en emulador (captura + logcat sin excepciones).
- Cada bug arreglado lleva, cuando la lógica es testeable en JVM, un test que fallaba antes.
- Commits chicos con conventional commits, en la rama `estabilizacion`. Sin push hasta que se pida.
- Código nuevo en archivos chicos; no se agranda `TheGoose` ni `GooseAI`.

---

## Parte A — Estabilización

### Fase 0 · Base de trabajo
- Script de prueba en emulador (`tools/smoke.sh`): instala, concede permisos, enciende el overlay, captura frames y cuenta excepciones en logcat.
- Subir AGP a 8.12.0 y `compileSdk` a 36 para igualarlo con `targetSdk`.
- Corregir `CLAUDE.md` con los datos reales.

### Fase 1 · Destrabar
| Arreglo | Hallazgo |
|---|---|
| Huevo con tamaño visible y radio táctil mínimo; gradientes protegidos contra radio 0 | 5.4 |
| Timer de eventos aleatorios avanza dentro de `runAI()` | 1.1 |
| `EnjoyPets` y `ReactToDrag` dejan de devolver `RUNNING` eterno; composites reactivos | 1.2 |
| `Cooldown` en las tres ramas de necesidades críticas | 1.3 |
| Objetivos fuera de pantalla alcanzables + timeout en toda etapa de tarea; quitar `NabMouse` | 1.4 |
| `Wander` usa velocidad máxima, no instantánea | 1.7 |
| Lista de modos sin `NONE` duplicado; selección sobre lista filtrada | 2.5 |
| Limpieza de reproductores de audio segura | 2.8 |
| `try` separados para `Tick` y `Render`, con recuperación tras fallos repetidos | 2.9 |

### Fase 2 · Una sola IA
- Eliminar el behavior tree duplicado de `TheGoose`.
- Único punto de entrada `requestTask(tarea, prioridad)`; resetea los timers de la tarea saliente.
- Modos secretos con duración y restauración de valores originales; no tocan `PetAppearance`.
- Cooldown en `Seeking`.

### Fase 3 · Reloj
- `Time` mide el delta real con `System.nanoTime()`, con tope de 50 ms por frame.
- Tiempo de juego en `double`, que se detiene cuando el render se pausa.
- Cooldowns con `SystemClock.elapsedRealtime()`.

### Fase 4 · Ciclo de vida y guardado
- `GooseOverlayService`: foreground service dueño del overlay, con notificación persistente y botón de apagar. La Activity solo lo enciende y lo apaga.
- Render pausado únicamente con pantalla apagada.
- `TheGoose.destroy()` real y reinicio limpio de todo el estado estático.
- `PetRepository`: única fuente de verdad. Escritura atómica (archivo temporal + rename), guardado con debounce, merge sobre claves existentes. Lo usan Activity, overlay y widget.
- Un solo contador por estadística; logros persistidos.
- Pedir `POST_NOTIFICATIONS` en runtime; notificaciones troll con cooldown e ID fijo.
- El audio no se libera mientras el overlay esté activo.

### Fase 5 · Pantalla y toques
- Una sola escala (la del ganso). Se elimina el `canvas.scale` global.
- Ventana chica que sigue al ganso para los toques, más capa de pantalla completa no táctil para efectos. Así no se bloquean las apps de abajo.
- `onSizeChanged` para rotación.
- Pointer ids en multi-touch; `ACTION_CANCEL` no ejecuta gesto; velocidad de lanzamiento con `VelocityTracker`.

### Fase 6 · Tamagotchi de verdad
- Tasas en escala de horas: hambre llena → vacía en ~8 h, energía ~12 h, felicidad ~6 h. Configurable.
- Decaimiento offline real, sin el tope de 1 h, con piso para que no "muera" por una noche sin mirar.
- Sueño nocturno automático que recupera energía.
- Evolución conectada: felicidad muestreada, secretos contados.
- Hitos mostrados una sola vez.
- Nuevas necesidades: higiene y salud (enfermarse si se lo descuida, curarlo).

### Fase 7 · Rendimiento
- `Paint`, `Path` y gradientes cacheados en `GooseRenderer`.
- Hora y batería consultadas una vez por minuto.
- `Canvas` pasado por parámetro a `Render`.
- Objetivo: 0 asignaciones gráficas por frame en el camino caliente.

---

## Parte B — Cerebro con IA

### Principio
El modelo **nunca corre en el loop de render** y **nunca es imprescindible**. Decide cada tanto, en un hilo aparte, y el resultado entra al juego como una intención más. Si no hay modelo, o tarda, o responde basura, siguen las plantillas actuales.

### Arquitectura

```
GooseBrain  (orquestador; decide cuándo pensar y aplica el resultado)
   ├── ContextBuilder     estado de la mascota + memoria + hora + evento → prompt
   ├── ResponseParser     JSON del modelo → intención validada (frase, emoción, acción)
   ├── GooseMemory        memoria a largo plazo resumida, en disco
   └── LlmBackend  (interfaz)
         ├── TemplateBackend        plantillas actuales (siempre disponible)
         ├── OpenAiCompatBackend    Ollama en la PC, LM Studio, OpenRouter, Groq…
         ├── AnthropicBackend       Claude
         ├── GeminiBackend          Gemini API
         ├── LlamaCppBackend        modelos GGUF en el teléfono
         └── LiteRtBackend          modelos .litertlm en el teléfono
```

`LlmBackend` expone: `isAvailable()`, `generate(request, callback)` con streaming opcional, `cancel()` y `release()`.

El modelo responde un JSON acotado:

```json
{"say": "frase corta", "mood": "happy", "action": "WANDER", "remember": "dato opcional"}
```

`action` solo puede ser una de las tareas que el juego ya sabe ejecutar. Lo que no valida, se descarta.

### Fase 8 · Núcleo
- Interfaz, orquestador, constructor de contexto, parser y backend de plantillas.
- Presupuesto: máximo una consulta cada N segundos, cancelación si el estado cambió, tope de tokens.
- Tests unitarios del parser y del constructor de contexto.

### Fase 9 · Backends remotos
- `OpenAiCompatBackend` primero: se prueba contra el Ollama que ya está en la PC, sin costo.
- `AnthropicBackend` y `GeminiBackend`.
- Las claves se guardan cifradas (`EncryptedSharedPreferences`), nunca en `config.ini` ni en el repo.

### Fase 10 · Backends en el teléfono
- Motor y modelo por defecto según el resultado de la investigación (sección siguiente).
- Descarga del modelo desde la app, con progreso y verificación de tamaño.
- El modelo se descarga de memoria tras unos minutos sin uso.

### Fase 11 · Pantalla de IA
- Elegir backend, modelo, URL y clave; botón "probar"; ver la última respuesta y su latencia.

### Fase 12 · Funciones
| Función | Descripción | Permiso extra |
|---|---|---|
| Hablar con el ganso | Tocarlo abre una burbuja para escribirle; contesta con su personalidad | — |
| Memoria | Recuerda tu nombre, lo que le contaste, cómo lo trataste | — |
| Diario | Cada noche escribe cómo fue su día | — |
| Personalidad que evoluciona | Los rasgos cambian el tono de todo lo que dice | — |
| Sueños | Al dormir, sueña con lo que pasó | — |
| Notas en pantalla | Deja mensajes generados por la IA | — |
| Voz | Lee sus frases en voz alta (TTS del sistema) | — |
| Reacciona al teléfono | Batería, carga, hora, auriculares, pantalla | — |
| Opina de tus notificaciones | Comenta lo que llega (opcional, apagado por defecto) | Acceso a notificaciones |
| Opina de la app que usás | "¿Otra vez en redes?" (opcional, apagado por defecto) | Acceso a uso |
| Escuchar | Hablarle por voz (opcional) | Micrófono |

Las tres últimas leen datos sensibles: vienen apagadas, se activan una por una, y con backend remoto la app avisa que ese contenido sale del teléfono.

---

## Orden y dependencias

```
F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7
                      └──────────→ F8 → F9 → F10 → F11 → F12
```

La Parte B arranca cuando termina la Fase 4: el cerebro necesita un servicio que sobreviva y un guardado que funcione.

## Investigación de LLM (2026-09-27)

Datos verificados contra Maven, GitHub y Hugging Face salvo donde se indica.

### Motores en el teléfono

| Motor | Versión | Desde Java | JSON forzado | Requisito | Decisión |
|---|---|---|---|---|---|
| **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`) | 0.17.1 (16-09-2026) | Sí, con callbacks | Sí (`ResponseFormat.json`) | Compilar con **JDK 21**; minSdk 24; trae arm64-v8a y x86_64 | **Primer motor on-device** |
| **llama.cpp** con build propio (NDK + CMake + JNI) | tag fijo | Sí (JNI propio) | Sí (gramáticas GBNF) | Upstream usa NDK 29; compatibilidad con NDK 26.3 sin confirmar | **Segundo motor**: abre todo el ecosistema GGUF |
| Llamatik (wrapper de llama.cpp) | 1.10.1 | Sí | Sí | minSdk 26 y JDK 21 | Atajo si el build propio se complica |
| MediaPipe LLM Inference | 0.10.35 | Sí | No | — | Descartado: solo mantenimiento |
| Gemini Nano (ML Kit Prompt API) | 1.0.0-beta4 | Sí | — | AICore | **Descartado**: el POCO F5 Pro no está soportado y la API bloquea el uso fuera de primer plano |

### Modelos candidatos (formato `.litertlm`)

| Modelo | Tamaño | Licencia | Requiere cuenta HF | Español declarado | Uso |
|---|---|---|---|---|---|
| Qwen3 0.6B (nothink, int4) | 347 MB | Apache-2.0 | No | No indicado | Emulador y teléfonos modestos |
| **LFM2.5 1.2B Instruct (int4)** | 736 MB | LFM 1.0 (gratis bajo USD 10 M de ingresos) | No | Sí | **Por defecto en el POCO F5 Pro** |
| Qwen3 1.7B | 977 MB | Apache-2.0 | No | No indicado | Alternativa 100 % Apache |
| Gemma 4 E2B (GPU) | 2,0 GB | Apache-2.0 | No | Sin dato | Opción de calidad alta |
| Gemma 3 1B / Llama 3.2 1B | 584 / 964 MB | gemma / llama3.2 | **Sí** | — | No como defecto: piden token |

No hay benchmarks publicados de calidad en español para modelos de este tamaño: se mide con prompts reales del ganso antes de fijar el defecto.

### Rendimiento esperado en el POCO F5 Pro

- No hay mediciones publicadas para este equipo. Referencias cercanas: Snapdragon 8 Gen 1 con un modelo de 1B en Q4 da ~12 tokens/s en CPU con llama.cpp; en la GPU Adreno 730 con llama.cpp cae a ~1,6.
- Estimación propia: 10 a 25 tokens/s en CPU para 1B. Una frase de 20-30 tokens sale en 1 a 3 segundos.
- No hay binarios NPU para el 8+ Gen 1. Con llama.cpp conviene CPU limitada a los núcleos grandes; con LiteRT-LM se prueba GPU en el equipo real.
- RAM: ~1 GB para un modelo de 1B. Sobra con 12 GB, pero HyperOS mata procesos con agresividad: el modelo se carga bajo demanda y se libera tras inactividad.

### Backends remotos

| Backend | Cómo | Nota |
|---|---|---|
| **Compatible con OpenAI** | `POST {base}/chat/completions`, streaming SSE | Cubre Ollama (`http://<ip>:11434/v1/`), LM Studio, OpenRouter, Groq. **Se implementa primero**: Java puro, sin tocar el build, se prueba desde el emulador contra el Ollama de la PC (`10.0.2.2`) |
| Gemini API | Tiene endpoint compatible con OpenAI | Reutiliza el backend anterior. En el tier gratuito Google usa el contenido para mejorar sus productos |
| Claude (Anthropic) | Messages API | Backend propio |

Cambios que exigen: permiso `INTERNET` y permitir HTTP sin cifrar solo hacia la red local.

### Restricciones de Android que condicionan el diseño

- El servicio del overlay usa el tipo `specialUse` (sin límite de tiempo). Publicar en Google Play exigiría declararlo con un video de demostración.
- Descarga de modelos: reanudable, con verificación de tamaño, a un archivo `.part` que se renombra al terminar. Se guardan en `getExternalFilesDir`, que además permite cargarlos por `adb push`.
- Una inferencia a la vez, con cancelación. Sin inferencia con pantalla apagada ni batería baja.

### Referencias de producto

Mascotas con IA existentes (Sweekar, AIdorable, Mochi Crew, AI Tamago) coinciden en cuatro funciones: memoria de lo que pasó, diario escrito por la mascota, etapas de crecimiento y que la mascota inicie la conversación. Todas entran en la Fase 12 sin permisos extra.

Se descarta usar `AccessibilityService`: Google Play prohíbe usarla para que una app actúe de forma autónoma, y es la función más invasiva de todas.

### Orden de implementación de la Parte B

1. Núcleo + plantillas como respaldo.
2. Backend compatible con OpenAI (Ollama) → iterar prompts y esquema JSON.
3. LiteRT-LM con JDK 21.
4. Claude y Gemini.
5. llama.cpp con build propio.

### Pendiente de confirmar al implementar

- Que AGP 8.12 con JDK 21 procese el AAR de LiteRT-LM.
- Que LiteRT-LM corra en el emulador x86_64 en CPU.
- Que llama.cpp actual compile con NDK 26.3.
- Tokens/s reales en el POCO F5 Pro.
