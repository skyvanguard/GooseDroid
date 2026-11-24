# Guía de Goozy

Un ganso que vive en la pantalla del teléfono, camina por encima de tus apps y hay que cuidar como a un tamagotchi. Lo que dice y decide lo piensa un "cerebro" que podés elegir: frases predefinidas, un modelo de IA dentro del teléfono, uno en tu PC o uno en la nube.

## Instalar

1. En la PC, dentro de la carpeta del proyecto:
   ```bash
   ./gradlew assembleDebug
   ```
   El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
2. Pasalo al teléfono (cable con `adb install -r app-debug.apk`, o copiándolo) e instalalo.
3. Abrí Goozy, activá el interruptor principal y aceptá el permiso **"Mostrar sobre otras apps"**. Después aceptá el de **notificaciones**.

Para una versión optimizada (más chica y rápida): `./gradlew assembleRelease`. Necesita una keystore configurada en `local.properties` (`keystore.path`, `keystore.password`, `keystore.alias`, `keystore.alias_password`).

### POCO / Xiaomi (HyperOS): ajustes obligatorios

HyperOS cierra las apps en segundo plano con mucha agresividad. Sin estos ajustes el ganso desaparece al rato:

- **Ajustes → Apps → Goozy → Inicio automático:** activado.
- **Ajustes → Apps → Goozy → Ahorro de batería:** *Sin restricciones*.
- **Ajustes → Apps → Goozy → Otros permisos → "Mostrar ventanas emergentes mientras se ejecuta en segundo plano":** permitido. Sin este permiso, mantener apretado al ganso no abre la ventana para hablarle (desde la notificación sí funciona).
- En **Apps recientes**, bloqueá Goozy con el candado.

## Cuidar al ganso

| Necesidad | Baja sola en | Se recupera con |
|---|---|---|
| Hambre | ~8 h | **Alimentar** |
| Energía | ~12 h | **Dormir**; de noche se duerme solo |
| Felicidad | ~6 h | Caricias, **Jugar** |
| Higiene | ~24 h, y más rápido cuando se embarra | **Bañar** |
| Salud | Solo si alguna necesidad queda al límite | Se recupera sola si está bien cuidado; **Remedio** si se enferma |

Si no lo mirás durante horas, las necesidades bajan a la mitad de velocidad y nunca llegan al peor valor: no se muere por una noche sin atención. La energía, en cambio, se recupera mientras no lo ves.

El ganso **evoluciona** (huevo → polluelo → joven → adulto → sabio → legendario → cósmico) según la edad, cuánto lo cuidás, qué tan feliz lo mantenés y los secretos que descubras.

## Tocarlo

- **Acariciar:** pasá el dedo suave sobre él.
- **Arrastrar y lanzar:** agarralo y soltalo con impulso.
- **Hablarle:** mantenelo apretado medio segundo sin mover el dedo; se abre una ventanita encima de cualquier app. También desde la notificación, con **Hablar**.
- Hay gestos secretos (círculos, toques rápidos, patrones…). Descubrirlos desbloquea modos especiales.

Los toques fuera del ganso pasan a la app de abajo: podés seguir usando el teléfono normalmente.

## El cerebro

Se configura en **Cerebro: hablar, memoria y diario**, desde la pantalla principal. Elegí una opción, completá los datos y tocá **Guardar y probar**: vas a ver la respuesta y cuánto tardó. Si el cerebro elegido falla, el ganso usa las frases predefinidas; nunca se queda mudo.

| Opción | Privacidad | Costo | Calidad | Qué necesita |
|---|---|---|---|---|
| **Plantillas** | Todo en el teléfono | Gratis | Frases fijas | Nada |
| **Modelo en el teléfono** | Todo en el teléfono | Gratis (usa batería al pensar) | Buena con Gemma 4 E2B | Descargar un modelo (0,2 a 2,5 GB) |
| **Ollama en mi red** | Queda en tu red | Gratis | Muy buena con modelos de 4B | La PC encendida y en la misma red |
| **Servidor compatible con OpenAI** | Sale del teléfono | Depende del servicio | Depende del modelo | URL, modelo y clave |
| **Claude (Anthropic)** | Sale del teléfono | Pago por uso | La mejor | Clave de API de Anthropic |
| **Gemini** | Sale del teléfono | Tiene nivel gratuito | Muy buena | Clave de API de Google |

Con las opciones que salen del teléfono, el estado de la mascota y lo que le escribís viajan al servicio elegido. En el nivel gratuito de Gemini, Google usa ese contenido para mejorar sus productos.

Las claves de API se guardan cifradas con una llave del propio teléfono y no entran en las copias de seguridad.

### Modelo en el teléfono

En la lista de modelos tocá **Descargar** (conviene con Wi-Fi), marcalo y tocá **Guardar y probar**. Si la descarga se corta, retoma desde donde quedó.

Para el POCO F5 Pro (12 GB de RAM) la recomendación es **Gemma 4 E2B**: en las pruebas fue el único modelo chico que respondió con carácter y en buen español. Los más livianos (Qwen3 0.6B, LFM2.5) funcionan, pero sus frases son flojas.

Probá también el interruptor **Usar la GPU**: según el teléfono es más rápida o más lenta que la CPU. El modelo se libera de la memoria a los 5 minutos sin uso.

### Ollama en tu PC

1. Instalá [Ollama](https://ollama.com) y bajá un modelo, por ejemplo `ollama pull gemma3:4b`.
2. Para que el teléfono llegue a la PC, Ollama tiene que escuchar en la red: en Windows, activá **"Expose Ollama to the network"** en la configuración de Ollama, o definí la variable de entorno `OLLAMA_HOST=0.0.0.0` y reiniciá Ollama.
   **Ojo:** así cualquiera en esa red puede usar tu Ollama, que no tiene contraseña. Hacelo solo en una red de confianza, como la de tu casa; en la red de una oficina o una red pública, no.
3. En la app: URL `http://IP-DE-TU-PC:11434/v1` (la IP la ves con `ipconfig`) y el nombre del modelo, por ejemplo `gemma3:4b`.

La app solo se conecta sin cifrado a direcciones de la red local. Para cualquier servidor en internet exige `https://`.

Para comparar modelos con los prompts reales del ganso: `python tools/eval_models.py gemma3:4b qwen3:4b` (ver `tools/eval_models.py`).

### Claude

Creá una clave en la consola de Anthropic y pegala en **Clave de API**. El modelo por defecto es `claude-opus-5`; podés escribir otro en el campo **Modelo**. Cada respuesta tiene costo: usá el campo **Segundos entre pensamientos espontáneos** para controlar cuánto habla solo el ganso. Las respuestas a lo que hacés vos (caricias, chat) no esperan ese intervalo.

## Lo que hace con IA

- **Habla** con su propia personalidad, que cambia según cómo lo tratás (travieso, cariñoso, juguetón…).
- **Recuerda** lo que le contás de vos. En la pantalla del cerebro ves lo que recuerda, y podés tocar un recuerdo para que lo olvide.
- **Escribe un diario** cada noche (desde las 21 h) y lo podés leer en la misma pantalla.
- **Sueña** cuando se duerme.
- **Trae notas:** a veces sale por un borde de la pantalla y vuelve arrastrando un papelito. Con IA, la nota la escribe él; si no, usa notas incluidas.
- **Voz:** activá **Leer lo que dice en voz alta** para que hable con el sintetizador del teléfono.
- **Reacciona** a la hora, a la batería baja, a cuando lo acariciás, lo alimentás, lo bañás o lo dejás solo.

## Cuando algo no anda

| Síntoma | Qué revisar |
|---|---|
| El ganso desaparece al rato | Los ajustes de HyperOS de arriba |
| No vuelve después de reiniciar | Que no lo hayas apagado desde la notificación: si lo apagás vos, queda apagado |
| Mantenerlo apretado no abre el chat | Permiso de ventanas emergentes en segundo plano (HyperOS); mientras tanto, usá **Hablar** en la notificación |
| "Guardar y probar" muestra un error de clave | La clave está mal copiada o vencida |
| Ollama no responde | Misma red Wi-Fi, Ollama expuesto a la red, firewall de Windows permitiendo el puerto 11434, IP correcta |
| El modelo local tarda mucho | Probá con o sin GPU, o un modelo más chico |
