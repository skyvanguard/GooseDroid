# Propuesta — Goozy

Fecha: 2026-10-11 · Base: rama `estabilizacion` (commit `1b3598e`) · Investigación de mercado hecha el mismo día; las cifras marcadas *(secundaria)* vienen de prensa o de la propia empresa, no de una fuente primaria.

## 1. La tesis en una frase

**El único companion con IA que no vive dentro de una app: vive encima de tu pantalla, es un ganso, hace lío, te cuida sin culparte y su cerebro no cuesta nada porque corre en tu teléfono.**

## 2. Lo que ya tenemos (análisis del código)

| Área | Estado | Valor para el producto |
|---|---|---|
| Overlay en foreground service, dos ventanas, toques que pasan a la app de abajo | Estable, probado en emulador | **Es el diferencial**: ningún referente con tracción vive sobre la pantalla |
| Render procedural (rig de huesos, IK de patas, expresiones, partículas) | 0 asignaciones por frame salvo 24 gradientes | Permite generar clips/GIF del ganso **sin capturar la pantalla** (se puede dibujar a un bitmap propio) |
| Tamagotchi: 5 necesidades en escala de horas, offline con pisos, nunca muere | Hecho | Ya cumple el patrón "sin castigo" que hace exitoso a Finch |
| Evolución huevo → cósmico, personalidad con rasgos −100..+100 | Hecho | Base para colección y formas raras |
| Cerebro intercambiable: plantillas, LiteRT-LM (Gemma 4 E2B), Ollama, OpenAI-compatible, Gemini, Claude | Hecho, con fallback y JSON con esquema | Costo de inferencia cero por defecto (la gran debilidad económica de Character.AI) |
| Memoria, diario nocturno, sueños, notas en pantalla, voz TTS, chat rápido | Hecho | Las cuatro funciones que comparten todas las mascotas IA existentes |
| Minijuegos, easter eggs, trolling, widget, personalización | Heredado del port original | Material para clips; hay que revisarlo con la nueva identidad |
| Tests | 145 JVM tests (cifra de `docs/PLAN.md`, sin volver a correr hoy) | — |

### Deudas que importan para crecer

1. **Procedencia y licencia (bloqueante para publicar).** Parte del código y de los assets iniciales venían de un proyecto de terceros publicado sin licencia. Se resuelve reescribiendo o reemplazando todo lo heredado: ver [PLAN-G0-GOOZY.md](PLAN-G0-GOOZY.md).
2. **Android 15+ y el arranque automático.** Con `targetSdk ≥ 35`, `SYSTEM_ALERT_WINDOW` ya no exime de la restricción de iniciar un foreground service desde segundo plano salvo que haya un overlay visible. Nuestro arranque tras reinicio puede fallar con `ForegroundServiceStartNotAllowedException`; hay que probarlo con `adb shell am compat enable FGS_SAW_RESTRICTIONS <paquete>`.
3. **Archivos gigantes**: `GooseRenderer` (3126 líneas), `TheGoose` (2013), `GooseAI` (1836), `GooseTouchHandler` (1228). No frenan hoy, pero cada función nueva cuesta más. `GooseLLM` (645, frases heredadas) convive con `brain/`.
4. **Sin cumplimiento de companions**: no hay aviso de "soy una IA", ni recordatorio de pausa, ni protocolo de crisis, ni modo menores. Hoy es obligatorio en California (SB 243, desde 2026-01-01), Nueva York (desde nov-2025) y la UE (AI Act art. 50, desde 2026-08-02).

## 3. Estado del arte en resumen

| Referente | Qué enseña |
|---|---|
| **Dot** (New Computer) — cerró el 2025-10-05 | La memoria "que te conoce" sola no sostiene un producto |
| **Grok Companions** (Ani, Rudi) | Avatar animado + voz + personalidad fuerte generan picos enormes (#1 en Japón), pero cortos; el ángulo romántico/NSFW atrae a los reguladores (FTC 6(b), sep-2025) |
| **Tolan** (Portola) — >3 M descargas, >USD 1 M/mes *(secundaria)* | **El referente más cercano**: no humano, no romántico, con lore cuidado (contrataron a Ted Chiang) |
| **Finch** — 4,95★ con >550 k reseñas *(secundaria)* | Mascota que crece con tu autocuidado, rachas sin castigo, widget como canal de regreso, "aventura" diaria |
| **Pengu** | La mascota compartida entre dos personas como motor social |
| **Character.AI / Replika** | Variedad y vínculo, pero demandas, multas (Replika €5 M, GDPR) y cierre del chat abierto a menores |
| **Friend** (colgante) | "Escucha todo" = rechazo público. La privacidad es parte de la marca |
| **Tamagotchi Paradise** — franquicia >100 M unidades | Evolución y coleccionismo siguen vendiendo sin IA |
| **Mascotas de escritorio y VTubers con IA** | El caos y la irreverencia generan clips; los clips generan usuarios |

Patrones que funcionan: memoria **visible**, una cita diaria con historia (no notificaciones genéricas), personaje no humano y coherente, voz **breve** (el estudio MIT/OpenAI de 2025 asocia el uso de voz prolongado con más soledad), progresión sin castigo, algo para compartir.

Técnico 2026: Gemma 4 E2B/E4B en `.litertlm` con decodificación restringida (ya lo usamos), Gemini Nano por ML Kit Prompt API (alfa, mejor en Pixel), Live Updates de Android 16 como superficie extra, y Play exige declarar `specialUse` con video.

## 4. Nombre e identidad

**Recomendación: "Goozy"** como nombre de trabajo. Dos sílabas, suena tierno, contiene *goose*, se pronuncia igual en español, inglés y portugués.

Lo verificado: no hay app "Goozy" a secas en Play; existe "Goozy Fitness" y la marca "GOOZIE" (portalatas, EE.UU.), en otras categorías. **Falta** buscar en USPTO, EUIPO y DINAPI, y que estén libres `goozy` en dominio, TikTok e Instagram. Alternativas si choca: *Honkie*, *Goosling*, *Gooz*.

Identidad del personaje (una "biblia" corta que todo prompt y toda plantilla respeten):

- **Qué es**: un ganso pequeño, caótico y leal. Roba cosas, deja barro, te cuida a su manera. **Nunca romántico**, nunca humano.
- **Cómo habla**: frases cortas, bocinazos, humor seco. Te tutea (voseo en es-AR/PY).
- **Su mundo**: sale por los bordes de la pantalla a "excursiones" y vuelve con objetos y anécdotas. Ese "afuera" es el lore que crece.
- **Su promesa**: "No subo nada a ningún lado salvo que vos lo elijas. Y si estás mucho rato conmigo, te mando a tomar aire."

## 4 bis. Que el modelo decida todo: cerebro de dos sistemas con Laya

Hoy el behavior tree decide casi todo y el LLM solo opina cada tanto (frase + una `BrainAction`). Para que **las decisiones salgan del modelo** sin gastar batería ni frenar el render, la arquitectura pasa a tres niveles:

| Nivel | Quién | Frecuencia | Decide |
|---|---|---|---|
| Reflejos | Behavior tree actual | Cada frame | Física, reacción inmediata al toque, seguridad (no salir de pantalla, no trabarse) |
| **Sistema 1** | **Laya** (modelo de decisiones) | Cada pocos segundos, ~decenas de ms | Próxima tarea, humor, ¿hablar ahora?, ¿qué necesidad atender?, ¿interrumpir al humano? |
| Sistema 2 | LLM actual (Gemma 4 / Ollama / Claude…) | Cuando hace falta texto o Laya duda | Frases, chat, diario, notas, recuerdos, decisiones con baja confianza (escalado) |

### Qué es Laya (verificado en fichas secundarias; Hugging Face no respondía desde la PC el 2026-10-11)

- `convaiinnovations/laya` y `convaiinnovations/laya-typed-decisions`, de Convai Innovations. Publicado el 2026-09-18. **Apache 2.0**.
- No genera texto: recibe un **estado** (por ejemplo, JSON) y **preguntas tipadas** declaradas de antemano, y devuelve probabilidades. Tipos: `choice` (una opción de un conjunto, conviene <20), `score` (escala ordinal) y probabilidad de verdadero/falso.
- ModernBERT-large, ~421 M parámetros, contexto de 512 tokens (inglés) o 1024 (otras variantes). Hay checkpoint multilingüe; **no está confirmado que cubra español** (para el estado en JSON no importa; para leer el chat sí).
- Latencias reportadas: decenas de ms en una T4; 7-13 ms en un M3 Max con MLX.
- Formatos: oficial PyTorch/Transformers; ONNX y MLX hechos por la comunidad. **No hay versión oficial para Android**: el camino es exportar a ONNX, cuantizar a int8 y correrlo con ONNX Runtime para Android (tiene API Java).
- **Ojo:** de base, sin ajuste, acierta ~0,36 contra 0,32 del azar en la evaluación del propio proyecto; ajustado, 0,766. **Hay que entrenarlo para el ganso.**

### Cómo se entrena para el ganso
Destilación desde los LLM que ya tenemos: se generan miles de estados de mascota (como hace `PromptSamplesTest`), Claude o Gemma 4 decide la acción/humor/hablar para cada uno, y con eso se ajusta Laya en la PC (RTX 5070 Ti; 400 M parámetros es un entrenamiento chico). Así el "criterio" del LLM grande queda dentro de un modelo que corre en el teléfono en milisegundos.

### Spike antes de comprometerse (fase L0)
1. Bajar el modelo, exportarlo a ONNX int8 y medir tamaño.
2. Probar zero-shot con preguntas del ganso (esperamos que sea malo).
3. Generar ~5 000 decisiones etiquetadas con Gemma 4/Claude y ajustar; medir acierto contra el LLM.
4. Medir latencia y RAM en el POCO F5 Pro con ONNX Runtime.

**Criterio de éxito:** ≥80 % de acuerdo con el LLM maestro, <150 ms por decisión en el teléfono y APK + modelo en un tamaño razonable. Si no se cumple, el plan B es que Gemma 4 E2B tome las decisiones con menos frecuencia (más lento y más batería, pero ya funciona).

## 5. Plan por fases

Cada fase termina como hasta ahora: build limpio, tests en verde, `tools/smoke.sh` en emulador y prueba en el POCO F5 Pro cuando toca hardware.

### Fase G0 · Poder publicarlo (bloqueante)
1. Reescribir o reemplazar todo lo heredado de terceros (detalle en [PLAN-G0-GOOZY.md](PLAN-G0-GOOZY.md)).
2. Agregar un `LICENSE` propio cuando no quede nada heredado (después de G0b).
3. Rebrand: `applicationId` nuevo (por ejemplo `com.skyvanguard.goozy`), nombre, ícono, strings. Como no está publicado, cambiar el id no rompe a nadie (sí implica reinstalar en el teléfono y perder el estado guardado: se puede exportar/importar el `config.ini` y la memoria).
4. Arreglar el arranque automático para Android 15+ (mostrar primero el overlay, o caer en notificación "tocá para despertar a Goozy").

### Fase G1 · Personaje y primer contacto
1. Biblia del personaje en `docs/` y en el system prompt de `PromptBuilder`; reescribir plantillas para que suenen al mismo ganso.
2. **Onboarding como momento memorable**: el huevo aparece en tu pantalla, se mueve, lo tocás, eclosiona y lo primero que hace es robarte algo. Pide el nombre del humano ahí mismo (primer recuerdo).
3. Aviso de IA dentro del personaje ("soy un ganso con un cerebro de IA, no una persona") al inicio y cada 3 h de chat continuo.

### Fase G2 · El ritual diario
1. **Excursión diaria**: una vez al día sale por un borde y vuelve horas después con un **objeto coleccionable** y una anécdota (escrita por el modelo o por plantilla). Reutiliza `NoteCarrier` y el diario.
2. **Álbum** de objetos y recuerdos en la app (colección visible).
3. Widget renovado como canal de regreso (estado + "Goozy volvió con algo").
4. Las necesidades siguen sin castigo; se puede ajustar la frecuencia en ajustes.

### Fase G3 · Memoria que se nota
1. Callbacks: el ganso trae a cuento algo que le contaste días atrás ("¿cómo te fue en el examen del martes?"). Requiere fecha en los recuerdos de `BrainMemory`.
2. Línea de tiempo de recuerdos y diario, con **exportar** y **borrar todo**.

### Fase G4 · Compartible (motor de crecimiento)
1. **Clip de Goozy**: como el ganso es procedural, se renderizan sus últimos segundos a un GIF/MP4 propio, sin permiso de captura de pantalla, con fondo neutro y marca de agua con el nombre.
2. Tarjetas compartibles: la página del diario, el objeto de la excursión, la evolución.
3. Repo público con README, GIF y APK en Releases (canal inicial antes de Play).

### Fase G5 · Bienestar y cumplimiento
1. Detección de mensajes de crisis (lista de patrones + clasificación del modelo) que corta el personaje y muestra líneas de ayuda locales.
2. Recordatorio de pausa después de uso prolongado ("andá a tomar aire, yo cuido la pantalla").
3. Modo menores: sin chat libre, solo plantillas y acciones.
4. Política de privacidad en lenguaje claro (necesaria para Play).

### Fase G6 · Un agente de verdad (con límites)
Lo que convierte a la mascota en "agente" como Dot o Grok, pero con herramientas acotadas que el modelo pide por JSON y el juego valida, igual que hoy con `BrainAction`:
1. **Recordatorios**: "recordame tomar agua a las 5" → el ganso aparece a esa hora y te persigue.
2. **Modo foco**: el ganso "cuida" la pantalla durante un pomodoro y protesta si abrís otra cosa (opcional; reaccionar a la app en uso requiere permiso de uso, apagado por defecto).
3. Gemini Nano como backend extra en equipos compatibles; Gemma 4 E2B sigue como defecto.

### Fase G7 · Play Store e internacional
1. Inglés y portugués; el modelo ya habla en el idioma configurado.
2. Declaración de `specialUse` con video, capturas, ficha.
3. Plan B por si Play rechaza el overlay: modo "dentro de la app" + widget + Live Update de Android 16.

### Más adelante (requiere servidor)
- **Visitas entre gansos**: tu Goozy aparece en la pantalla de un amigo con un regalo o una travesura. Es el gran motor social (patrón Pengu) pero exige backend, cuentas y moderación; se evalúa con tracción real.

### Fase L · Cerebro de dos sistemas (Laya)
- **L0**: spike descrito en la sección 4 bis.
- **L1**: `DecisionBackend` (interfaz nueva junto a `LlmBackend`) con `LayaBackend` (ONNX Runtime) y un backend "LLM como decisor" de respaldo. El behavior tree pide la próxima tarea al Sistema 1 en vez de elegirla él.
- **L2**: escalado al Sistema 2 cuando la confianza es baja; registro de decisiones para volver a entrenar.
- **L3**: descarga del modelo de decisiones desde la app, reutilizando `ModelDownloader`.

## 6. Orden recomendado

Decidido el 2026-10-11: el destino es el **repo público** de GitHub de skyvanguard (ya público, 7 estrellas, `estabilizacion` ya mergeada en `main` por el PR #1). **Está publicado sin `LICENSE`**, lo que vuelve urgente G0.

```
G0 (licencia, rebrand, Android 15) ─► L0 (spike Laya) ─► L1-L2 (dos sistemas) ─► G1 (personaje)
                                                                              ├► G2 (ritual) ─► G4 (clips, README con GIF)
                                                                              └► G3 (memoria) ─► G5 (bienestar) ─► G6
```

G7 (Play) queda fuera de alcance por ahora; la distribución es el APK en GitHub Releases. G5 se mantiene porque el aviso de IA y el protocolo de crisis también aplican fuera de Play.

## 7. Decisiones abiertas

1. **Nombre**: confirmar "Goozy" tras revisar marcas y handles. Renombrar el repo en GitHub mantiene redirecciones desde el nombre viejo.
2. **Licencia propia**: depende de la del port original; si se puede elegir, MIT o Apache-2.0 (Apache encaja con Laya y Gemma).
3. **Monetización**: ninguna por ahora (open source).

## Fuentes principales

- Dot: https://techcrunch.com/2025/09/05/personalized-ai-companion-app-dot-is-shutting-down
- Tolan: https://www.geekwire.com/2025/ai-companionship-app-tolan-raises-20m-to-help-more-people-grow-with-a-virtual-alien-friend/
- Grok en Japón: https://sensortower.com/ja/blog/grok-with-ani-JP
- Character.AI y menores: https://fortune.com/2025/10/29/character-ai-ban-children-teens-chatbots-regulatory-pressure-age-verification-online-harms
- FTC 6(b): https://www.ftc.gov/news-events/news/press-releases/2025/09/ftc-launches-inquiry-ai-chatbots-acting-companions
- California SB 243: https://perkinscoie.com/insights/update/california-companion-chatbot-law-now-effect
- Nueva York: https://www.manatt.com/insights/newsletters/client-alert/new-york-s-safeguards-for-ai-companions-are-now-in-effect
- AI Act: https://www.aljazeera.com/economy/2026/8/6/what-came-into-force-with-the-eus-ai-act-this-week-and-what-didnt
- Finch: https://foxdata.com/en/blogs/finch-as-app-store-editors-choice-a-self-care-companion/
- Estudio MIT/OpenAI: https://ispr.info/2025/03/27/openai-and-mit-media-lab-release-research-on-affective-engagement-with-chatgpt-and-emotional-well-being/
- Gemma 4 en el borde: https://developers.googleblog.com/bring-state-of-the-art-agentic-skills-to-the-edge-with-gemma-4/
- Prompt API (Gemini Nano): https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html
- Android 15, cambios de comportamiento: https://developer.android.com/about/versions/15/behavior-changes-15
- Foreground services en Play: https://support.google.com/googleplay/android-developer/answer/13392821
