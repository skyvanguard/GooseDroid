# Plan G0 — Goozy: proyecto propio, limpio de código heredado

Fecha: 2026-10-11 · Rama: `goozy` (desde `estabilizacion`) · Contexto: [PROPUESTA-GOOZY.md](PROPUESTA-GOOZY.md)

## Diagnóstico

El proyecto empezó desde un port Android de terceros, publicado sin licencia. Sin licencia, ese código y esos assets no se pueden redistribuir. Además, aquel port reutilizaba sonidos y textos de otro juego de escritorio.

Comparación hecha el 2026-10-11 contra la rama principal del port:

- **Código**: de las 11 377 líneas no triviales del proyecto (≥30 caracteres, sin imports), 314 coinciden con el original (2,8 %). Gran parte son llamadas boilerplate de la API de Android. El código realmente heredado se concentra en:

| Pieza | Coincidencia | Acción |
|---|---|---|
| `GooseDesktop/Easings.java` | 99 % (solo se usa `CubicEaseInOut`) | Borrar; función propia de una línea |
| `SamEngine/Vector2`, `SamMath`, `Deck`, `Time` | 14-97 % | Reescribir como `core/Vec2`, `MathUtil`, `ShuffleBag`, `Time` |
| `GooseDesktop/FootMark.java` | 86 % | Reescribir |
| `PermissionHelper.java` + `PermissionRequest.java` | 92 % / 50 % | Reescribir en una sola clase |
| `ConfigureActivity.java` (lector INI, no es una Activity) | 12 % | Reescribir como `PetConfigFile` |
| `GooseDesktop/Sound.java` | estructura y rutas de assets | Reescribir sobre `SoundPool` con sonidos nuevos |
| Constantes de `GooseTasks`, colores de `TheGoose`, sombra de `GooseRenderer` | líneas sueltas | Reescribir con valores y nombres propios |

- **Assets idénticos** al port:
  - sonidos: honks, mordida, barro, palmadas y música;
  - imágenes: memes, gif, corazón;
  - ícono;
  - textos de notas (y sus traducciones al español, que también son derivadas);
  - `app/libs/core.jar`, una librería ajena que no se usa;
  - layouts `page_config`/`page_start` y `theme_overlays.xml`, que no se usan.

  Acción: **borrar todo**. En su lugar van **sonidos sintetizados por un script propio** (`tools/gen_sounds.py`), notas escritas de cero y un ícono vectorial nuevo.

## Pasos (un commit por paso)

1. **Borrar lo heredado que no se usa**: memes, gif, corazón, `core.jar`, layouts y temas sin uso, `zalo.txt`.
2. **Reescribir el núcleo matemático y utilitario**: `Vec2`, `MathUtil`, `ShuffleBag`, `Time`, `FootMark`, la curva propia, `PetConfigFile` y el permiso de overlay. Ajustar los tests.
3. **Sonido y notas propios**: sintetizador en `tools/gen_sounds.py` que genera los `.ogg`, `Sound` reescrito y notas nuevas en español e inglés.
4. **Renombrar todo**:
   - paquete y `applicationId` `com.skyvanguard.goozy`;
   - subpaquetes `engine` (antes `GooseDesktop`) y `core` (antes `SamEngine`);
   - nombre visible **Goozy**;
   - ícono adaptativo y de notificación nuevos;
   - sin comentarios en chino heredados.
5. **Documentación**: README, `CLAUDE.md`, `AGENTS.md`, `docs/` sin referencias al port ni a su origen; `LICENSE` propio (Apache-2.0).
6. **Verificación**:
   - build;
   - todos los tests;
   - `tools/smoke.sh` en el emulador;
   - volver a correr la comparación contra el port, que debe quedar sin piezas heredadas (solo boilerplate de Android);
   - `grep` de referencias en cero.

## Fuera de esta rama (requiere decisión del dueño)

- **Historial de git**: los commits viejos del repo público siguen conteniendo el código y los assets del port. Limpiar `main` no los borra. Opciones:
  - (a) **repo nuevo `goozy`** con historial desde cero y el viejo archivado o borrado;
  - (b) reescribir el historial con force push a `main`.
  
  Recomendación: (a).
- **Renombrar el repo en GitHub** y publicar el APK en Releases.
- **Datos en el teléfono**: al cambiar el `applicationId`, Goozy se instala como app nueva. El estado del ganso viejo no se migra.

## Estado al 2026-10-11 (G0a hecho)

Pasos 1 a 5 hechos en la rama `goozy`, salvo el `LICENSE`, que espera a G0b. Verificado así:

- `assembleDebug` y 323 tests en verde.
- `tools/smoke.sh` OK en el emulador. Logcat sin errores de carga de sonido.
- El ícono nuevo se ve en el launcher.

Las líneas no triviales que coinciden con el original bajaron de 314 a 93. Lo que queda es en su mayoría boilerplate de la API de Android (`createNotificationChannel`, `onStartCommand`…), más estas piezas:

### G0b · Lo que falta para quedar limpio

La locomoción, el rig y el sistema de tareas conservan la **estructura** del original aunque las líneas cambiaron:

- nombres como `footHome`, `lFootMoveOrigin`, `neckLerpPercent` y `underbodyCenter`;
- las tareas `NabMouse`/`CollectWindow`/`TrackMud` y sus etapas;
- las constantes de `GooseTasks`;
- las claves heredadas de `config.ini` (`Task_CanAttackMouse`, `AttackRandomly`, `*WanderingTimeSeconds`, `GooseDefault*`).

Son unas 280 referencias en `GooseAI`, `GoosePhysics`, `GooseRig`, `GooseRenderer` y `GooseTasks`. Se reescriben con diseño propio junto con la fase L1: el sistema de acciones nuevo es el que va a manejar el modelo de decisiones. Recién después:

- `LICENSE` propio;
- repo nuevo con historial limpio.

## Estado al 2026-10-11 (G0b hecho)

Commits `9179ab9` y `ae54e2c` en la rama `goozy`.

### Cuerpo nuevo (`engine/body/`)

- **Qué es.** `GooseModel` (Java puro, con tests) y `GooseBodyPainter` reemplazan el rig, el dibujo del cuerpo y el solver de patas.
- **Vista y geometría.** Vista tres cuartos con un solo sistema de coordenadas. La silueta es suave y tiene un solo contorno, y el cuello es un tubo.
- **Animación.** Caminata por fases con pisadas. Ojos expresivos (`HAPPY`, `JOY`, `LOVE`…), mirada, parpadeo y posturas.
- **Formas propias.** Huevo, pollito y pato tienen su propia forma.
- **Tacto.** Las zonas táctiles coinciden con lo dibujado.

### Tareas propias

- Paseo, buscar nota y correr embarrado.
- Se eliminan la tarea del mouse y las claves heredadas de `config.ini` y del panel de configuración.

### Verificación

- 336 tests en verde.
- `tools/smoke.sh` OK.
- Revisión de código aplicada.
- Capturas en el emulador del huevo, el pollito y el adulto, más una prueba de arrastre.

### Lo que queda heredado

- 83 líneas coinciden con el original (antes 314), casi todas boilerplate de Android.
- No queda ningún identificador de la estructura original.

### Pendiente (decisión del dueño)

- `LICENSE`: se propone Apache-2.0.
- Repo nuevo con historial limpio, o reescritura del actual.
- Spike de Laya (L0): bloqueado porque esta red corta el TLS hacia huggingface.co.
