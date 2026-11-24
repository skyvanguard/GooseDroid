# Auditoría Goozy — 2026-09-27

Revisión por lectura de código de los ~21.000 LOC de `app/src/main/java`. **Nada se compiló ni se ejecutó**: la máquina donde se hizo no tiene Android SDK. Todo lo que dice "confirmado" significa confirmado leyendo el código; lo que depende del comportamiento en un teléfono está marcado como "a confirmar en dispositivo".

Síntoma de partida: "cuanto más se usa, termina bugeándose".

## Resumen

El problema no es una fuga de memoria ni hilos: no hay hilos secundarios y todas las colecciones tienen tope. Son **máquinas de estado que se traban y no se destraban**, más un **ciclo de vida atado a la Activity** y un **reloj mal medido**. Casi todos se disparan con el uso normal (tocar al ganso, dejar que le dé hambre, apagar y prender el overlay).

Varios bugs se tapan entre sí: mientras el ganso está trabado en un evento (1.1), otras fallas no llegan a ejecutarse. Al arreglar uno van a aparecer los siguientes.

---

## 1. El ganso se traba (IA)

| # | Problema | Dónde | Qué pasa |
|---|---|---|---|
| 1.1 | Un evento aleatorio nunca termina | `GooseAI.java:291-294`, `:543-549` | `runAI()` hace `return` si hay evento activo, y el timer que lo termina solo avanza en `updateRandomEvents()`, que queda después de ese `return`. Con behavior tree activo ni se llama. Cualquier evento (estirarse, zoomies, girar) congela la tarea actual para siempre. |
| 1.2 | El árbol queda pegado en "EnjoyPets" tras el primer toque | `GooseBehaviorTree.java:101-128`, `BehaviorTree.java:103-106`, `:148-151` | `EnjoyPets` y `ReactToDrag` devuelven siempre `RUNNING`. `Sequence` y `Selector` guardan `currentChild` y reanudan ahí sin reevaluar la condición. Resultado: emoji ":)" permanente y el resto del árbol no corre más. |
| 1.3 | Hambre > 90: honk y emoji en cada frame | `GooseBehaviorTree.java:65-89`, `Sound.java:218-232` | Las tres ramas críticas no tienen `Cooldown`. `HONCC()` crea un `MediaPlayer` con `prepare()` síncrono en el hilo principal, por frame. El ganso además queda clavado en el centro. |
| 1.4 | Tareas que no pueden completarse | `GooseAI.java:1611`, `GoosePhysics.java:465-486` | TrackMud y CollectWindow apuntan a x = -50, pero la física limita la posición a [20, ancho-20]. Nunca llega; rebota contra el borde sin timeout. `NabMouse` está vacío. |
| 1.5 | Hay dos behavior trees | `TheGoose.java:419`, `GooseAI.java:232` | Dos instancias se ejecutan por frame, cada una con sus cooldowns, y ambas cambian la tarea. |
| 1.6 | Timers de tarea no se resetean al interrumpir | `GooseAI.java:1324-1452`, `setTask` en `:1654` | Si tocás al ganso mientras duerme, la próxima siesta termina en un frame. Igual con comer y jugar. |
| 1.7 | Wander degenera hasta dejarlo parado | `GooseAI.java:1090` | La distancia del próximo paso usa la velocidad instantánea, que tras la pausa es 0. *(reportado por la auditoría, no re-verificado)* |
| 1.8 | Bucle Seeking ↔ Wander tras 2 min sin tocarlo | `GooseAI.java:433-440`, `:1546` | Emoji "?" constante. *(ídem)* |

## 2. Ciclo de vida

| # | Problema | Dónde | Qué pasa |
|---|---|---|---|
| 2.1 | No hay `Service` | `AndroidManifest.xml` | El overlay vive dentro de `MainActivity`. Si Android mata la Activity, el ganso desaparece. `FOREGROUND_SERVICE` está declarado sin usarse. |
| 2.2 | `onPause` detiene el render | `MainActivity.java:577` | Al salir de la app el loop se para y nada lo reanuda. Según el código el ganso queda congelado sobre las otras apps. **A confirmar en dispositivo.** |
| 2.3 | Apagar el overlay no limpia nada | `MainActivity.java:140-143` | Solo hace `removeView`. `TheGoose.destroy()`, `pause()` y `resume()` no tienen ningún llamador. La música sigue sonando. |
| 2.4 | Estado estático que sobrevive al reinicio | `TheGoose`, `GooseEasterEggs`, `GooseDreams`, `MiniGames` | Al prender por segunda vez se mezcla estado viejo con nuevo. Los logros se re-desbloquean con festejo cada vez. |
| 2.5 | Bucle infinito (la app se cuelga) | `GooseEasterEggs.java:89`, `:355-357` | Cada `init()` agrega otro `NONE` a una lista estática. Con `[NONE, NONE]` el `do/while` no sale nunca. Se dispara con un gesto de círculo tras reiniciar el overlay. |
| 2.6 | Modos secretos que nunca se apagan | `GooseEasterEggs.java:376` | `deactivateMode()` no tiene llamadores. Gigante, turbo y dorado se acumulan; el dorado pisa el color elegido por el usuario y se guarda en disco. |
| 2.7 | El audio muere tras `onTrimMemory` | `GoozyApplication.java:183-215` | Libera todo el sonido y nadie lo reinicializa. *(flujo confirmado; la frecuencia real depende de Android)* |
| 2.8 | Limpieza de audio rota | `Sound.java:143-155` | Llama `isPlaying()` sobre reproductores ya liberados (lanza excepción) y borra de la lista mientras la recorre. Se activa a partir del sonido 11. Desde un `Handler` nadie la atrapa: crash. |
| 2.9 | Excepciones tragadas en el loop | `GooseView.java:136-142` | Si `Tick()` falla, ese frame no se dibuja y solo queda un log. |

## 3. Tiempo

| # | Problema | Dónde | Qué pasa |
|---|---|---|---|
| 3.1 | `deltaTime` es una constante (1/120) | `Time.java:10`, `GooseView.java:69` | El loop corre cada 16 ms o más, así que el juego va a la mitad de velocidad o menos, y más lento cuanto más se traba el teléfono. |
| 3.2 | `Time.time` es `float` | `Time.java:26` | Pierde precisión con las horas: ≈2 ms a las 4,5 h, ≈8 ms a las 18 h, ≈16 ms a las 36 h. Animaciones y pasos se ven entrecortados. |
| 3.3 | El reloj sigue corriendo en pausa | `Time.java` | Al reanudar, todos los timers vencen juntos. |

## 4. Persistencia

| # | Problema | Dónde | Qué pasa |
|---|---|---|---|
| 4.1 | `saveState()` nunca escribe a disco | `TheGoose.java:511-536` | Solo modifica un `Properties` en memoria. Estadísticas y logros no persisten. |
| 4.2 | "Guardar config" borra la mascota | `MainActivity.java:708-730` | Reescribe `config.ini` con 13 claves; se pierden hambre, personalidad, apariencia y `PetModeEnabled`. |
| 4.3 | Escritura no atómica | `ConfigureActivity.java:40` | Si el proceso muere a mitad queda truncado, y al abrir `string2boolean(null)` lanza NPE. |
| 4.4 | Reencender el overlay revierte las necesidades | `TheGoose.java:541-553` | `loadState()` pisa los valores vivos con los del archivo. |
| 4.5 | Tres contadores de caricias | `GooseLLM`, `PetPersonality`, `TheGoose.stats` | Tres almacenes distintos que divergen. |
| 4.6 | Una caricia se registra por frame | `GooseAI.java:1585` | Mantener el dedo 5 s cuenta cientos de caricias y encola una escritura de preferencias por frame. *(reportado, no re-verificado)* |
| 4.7 | El widget no lee ni guarda | `PetWidget.java` | Con el proceso muerto muestra valores por defecto; alimentar desde el widget no se guarda. *(ídem)* |

## 5. Balance de tamagotchi

| # | Problema | Dónde | Qué pasa |
|---|---|---|---|
| 5.1 | Las necesidades decaen en minutos | `PetNeeds.java:14-16` | Hambre de 50 a 90 en unos 3 minutos reales. |
| 5.2 | Volver tras 12 minutos = todo al peor valor | `PetNeeds.java:62-77` | El decaimiento offline está topado en 1 hora, pero con estas tasas alcanza con 12 minutos. |
| 5.3 | La evolución está bloqueada | `GooseEvolution.java:218`, `:223` | `recordHappiness` y `recordSecretFound` no tienen llamadores: felicidad promedio y secretos valen siempre 0. |
| 5.4 | El huevo tiene tamaño 0 | `GooseEvolution.java:19`, `TheGoose.java:388` | `DrawScale = 2.5 × 0`. No se dibuja y el radio táctil es 0. **A confirmar en instalación limpia.** |
| 5.5 | Hitos pegados | `GooseLLM.java:737-751` | Compara con `==` sin marca de "ya mostrado": todo el día 7 de racha el único pensamiento es "1 WEEK!". |

## 6. Pantalla y toques

| # | Problema | Dónde | Qué pasa |
|---|---|---|---|
| 6.1 | Doble escala | `GooseView.java:123`, `:187-188` | El canvas se escala 2,5× desde el centro, así que solo se ve el 40 % central del espacio donde se mueve el ganso. El resto del tiempo está fuera de pantalla. |
| 6.2 | Mapeo de toques incorrecto | `GooseView.java:187-188` | Usa `x / s`; lo correcto es `(x - cx) / s + cx`. El toque se registra desplazado. |
| 6.3 | El overlay bloquea las apps de abajo | `MainActivity.java:125-136` | Ventana de pantalla completa y táctil. Devolver `false` en `onTouchEvent` no reenvía el toque a otra ventana. **A confirmar en dispositivo.** |
| 6.4 | Lanzar deja de funcionar tras el primer toque | `GooseTouchHandler.java:262`, `:403` | El `dt` se mide desde el fin del toque anterior. *(reportado, no re-verificado)* |
| 6.5 | Rotación no manejada | `MainActivity.java:135-136`, `TheGoose.java:342` | Tamaño de ventana y límites se leen una sola vez. |
| 6.6 | Multi-touch sin pointer ids | `GooseView.java:191` | Con dos dedos el ganso salta de posición. *(ídem)* |

## 7. Rendimiento

- `GooseRenderer` crea por frame unos 40-50 `Paint`, 40 `Path`, 45-50 gradientes y 700-1000 `Vector2` (conteo aproximado de la auditoría). Genera presión constante de GC y tirones.
- `Calendar.getInstance()` varias veces por frame; consulta de batería por IPC por frame tras el cooldown (`GooseSystemReactions.java:113-124`).
- `Canvas` guardado en un campo estático en el primer frame (`TheGoose.java:334`): Android no garantiza que sea el mismo objeto en cada `onDraw`.

## 8. Build y permisos

- `compileSdk 34` con `targetSdk 36` (`app/build.gradle:26`, `:31`): conviene igualarlos.
- `POST_NOTIFICATIONS` está en el manifest pero nunca se pide en runtime: en Android 13+ no sale ninguna notificación.
- `SYSTEM_OVERLAY_WINDOW` no es un permiso real de Android.
- Las notificaciones troll se saltan su cooldown y usan 100 IDs distintos (`GooseTrolling.java:247`, `GooseAI.java:791`).

---

## Sobre el "LLM"

`GooseLLM.java` no es un modelo de lenguaje: son plantillas con selección aleatoria ponderada. Funciona, no gasta batería, pero no genera nada nuevo.

| Opción | A favor | En contra |
|---|---|---|
| **LiteRT-LM** (Google) con Gemma 3 1B 4-bit | Es donde Google concentra el desarrollo; aceleración GPU/NPU; API Kotlin | Hay que descargar el modelo (cientos de MB) |
| **llama.cpp** por JNI con GGUF | Corre en cualquier teléfono; libertad de modelo | Integración nativa más trabajosa; 10-20 tokens/s en gama alta |
| **Gemini Nano** (ML Kit Prompt API) | Sin descargar modelo | Solo en teléfonos con AICore (Pixel 9/10, Galaxy S26 y similares) |

MediaPipe LLM Inference quedó en modo mantenimiento; no conviene empezar ahí.

Diseño recomendado: el modelo como **capa opcional encima de las plantillas**, nunca en el loop de render. Se lo consulta cada tanto, en un hilo aparte, con el estado real de la mascota como contexto (necesidades, personalidad, hora, últimos eventos), y devuelve una frase corta o una decisión entre acciones ya existentes. Si el teléfono no da o el modelo no está descargado, siguen las plantillas.

Fuentes consultadas el 2026-09-27:
- https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/
- https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
- https://www.forasoft.com/blog/article/neural-networks-on-android-369
- https://developers.google.com/ml-kit/genai/prompt/android/get-started
- https://developer.android.com/ai/gemini-nano
- https://mobile-artificial-intelligence.com/maid/guides/llama-cpp

---

## Plan propuesto

| Fase | Contenido | Tamaño |
|---|---|---|
| **1. Destrabar** | 1.1, 1.2, 1.3, 1.4, 2.5, 2.8, 1.7 | Cambios chicos y localizados. Es lo que más se nota. |
| **2. Una sola IA** | 1.5, 1.6, 1.8, 2.6 | Eliminar el árbol duplicado, un único punto para cambiar de tarea. |
| **3. Reloj** | 3.1, 3.2, 3.3 | Delta real medido con clamp, tiempo en `double`. Toca ~70 usos. |
| **4. Ciclo de vida y guardado** | 2.1-2.4, 2.7, 2.9, 4.1-4.7 | Foreground Service, `destroy()` real, un único repositorio de estado con escritura atómica. Es la fase más grande. |
| **5. Pantalla y toques** | 6.1-6.6 | Una sola escala, ventana chica que sigue al ganso. |
| **6. Tamagotchi** | 5.1-5.5 | Rebalancear tasas (horas, no minutos), desbloquear evolución, dibujar el huevo. |
| **7. Rendimiento** | Sección 7 | Cachear objetos gráficos. |
| **8. LLM** | — | Recién con la base estable. |

Antes de la fase 1 hace falta poder compilar y correr tests: instalar Android SDK en la máquina de desarrollo o trabajar desde donde ya esté Android Studio.
