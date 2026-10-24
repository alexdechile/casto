# Casto

Emisor (sender) de Cast para Android con **Kotlin** y **Jetpack Media3 1.11.1**.

Incluye:

- `PlaybackService`, un `MediaSessionService` que es **dueño del único reproductor**:
  un **CastPlayer** (ExoPlayer local + receptor Cast) expuesto mediante una `MediaSession`.
- `MainActivity` con un `MediaRouteButton` para elegir el dispositivo Cast y un
  `PlayerView` enlazado a un **`MediaController`** conectado al servicio (no crea players propios).
- `DefaultCastOptionsProvider` declarado en el `AndroidManifest.xml`.
- CI en GitHub Actions: build debug, lint y tests; APK de release firmado.

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

## GitHub Actions

### `.github/workflows/android-ci.yml`

En cada push/PR a `main`: `assembleDebug`, `lintDebug`, `testDebugUnitTest` y sube el APK debug.

### `.github/workflows/build.yml`

En cada push a `main` genera un **APK de release firmado**. La firma se lee de los
secrets del repositorio.

Configura en **Settings → Secrets and variables → Actions**:

| Secret | Contenido |
|---|---|
| `KEYSTORE_BASE64` | El keystore `.jks` codificado en base64 |
| `KEYSTORE_PASSWORD` | Password del keystore |
| `KEY_ALIAS` | Alias de la clave |
| `KEY_PASSWORD` | Password de la clave |

Para generar el base64 en Linux/macOS:

```bash
base64 -w0 release-keystore.jks > keystore.b64
```

En PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-keystore.jks")) | Set-Content keystore.b64
```

El workflow escribe un `keystore.properties` temporal (ignorado por git) y Gradle lo usa
para firmar. Nunca se sube el keystore ni sus passwords al repositorio.

## Notas

- El proyecto usa `compileSdk 36` porque Media3 1.11.1 lo requiere.
- Las APIs de Cast de Media3 están anotadas con `@UnstableApi`; el opt-in se hace con
  `@androidx.annotation.OptIn` en las clases que las usan.
