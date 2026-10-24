# Casto

Emisor (sender) de Cast para Android con **Kotlin** y **Jetpack Media3 1.11.1**.

Incluye:

- `PlaybackService`, un `MediaSessionService` que es **dueño del único reproductor**:
  un **CastPlayer** (ExoPlayer local + receptor Cast) expuesto mediante una `MediaSession`.
- `MainActivity` con un `MediaRouteButton` para elegir el dispositivo Cast y un
  `PlayerView` enlazado a un **`MediaController`** conectado al servicio (no crea players propios).
- `DefaultCastOptionsProvider` declarado en el `AndroidManifest.xml`.
- CI en GitHub Actions: build debug, lint y tests, con el APK debug como artifact.

## Arquitectura

```
MainActivity ──MediaController──▶ PlaybackService (MediaSessionService)
  MediaRouteButton                    └── MediaSession
                                          └── CastPlayer
                                                ├── ExoPlayer   (sin sesión Cast)
                                                └── RemoteCastPlayer (con sesión Cast)
```

La UI nunca toca el reproductor directamente: `MainActivity` se conecta al
`MediaSessionService` y le pasa el `MediaItem`. Así la reproducción sobrevive a la
rotación de pantalla y el botón Cast cambia el reproductor del servicio al dispositivo remoto.

## Estructura

```
app/src/main/java/com/example/casto/
├── MainActivity.kt      # MediaRouteButton + MediaController + PlayerView
└── PlaybackService.kt   # MediaSessionService dueño del CastPlayer
app/src/main/res/raw/
└── sample_video.mp4     # ⚠️ reemplazar por un MP4 real
```

## Video local

El archivo `app/src/main/res/raw/sample_video.mp4` es un **placeholder vacío**.
Reemplázalo por un MP4 real (mismo nombre) o cambia la URI en `MainActivity`:

```kotlin
val videoUri = Uri.parse("android.resource://$packageName/${R.raw.mi_video}")
```

> **Limitación**: un `android.resource://` (o `content://`) solo lo puede leer el teléfono.
> La reproducción local funciona, y el botón Cast descubre y conecta dispositivos, pero el
> receptor remoto **no** puede descargar ese archivo. Para que el video se vea en el
> Chromecast hay que servirlo por HTTP(S) (por ejemplo un servidor local en la LAN).


## Compilar en local

```bash
./gradlew :app:assembleDebug
```

Requiere JDK 17 y Android SDK con `compileSdk 36`.

## Descargar el APK (sin configurar nada)

El workflow `.github/workflows/android-ci.yml` compila en cada push a `main`
(build debug + lint + tests) y deja el APK como **artifact**:

1. Abre el repo en GitHub → pestaña **Actions**.
2. Entra al run más reciente de **Android CI** (el del check verde).
3. Baja hasta **Artifacts** y descarga **`app-debug`** (un `.zip`).
4. Descomprime y pasa `app-debug.apk` al teléfono; instálalo (activando
   "instalar apps de origen desconocido" si lo pide).

Ese APK ya viene firmado con la clave de debug automática, así que sirve para
probar en cualquier teléfono. No hay secrets, keystores ni configuración extra.

> Si algún día necesitas un APK de release firmado para publicar, hay que volver a
> añadir un workflow de firma con un keystore propio.

## Notas

- El proyecto usa `compileSdk 36` porque Media3 1.11.1 lo requiere.
- Las APIs de Cast de Media3 están anotadas con `@UnstableApi`; el opt-in se hace con
  `@androidx.annotation.OptIn` en las clases que las usan.
