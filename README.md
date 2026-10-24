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
├── PlaybackService.kt   # MediaSessionService dueño del CastPlayer
├── LocalMediaServer.kt  # servidor HTTP (NanoHTTPD) que publica el MP4 en la LAN
└── HttpRange.kt         # parser de cabecera Range (con tests en src/test)
app/src/main/res/raw/
└── sample_video.mp4     # ⚠️ reemplazar por un MP4 real
```

## Video local y Cast

El archivo `app/src/main/res/raw/sample_video.mp4` es un **placeholder vacío**.
Reemplázalo por un MP4 real con el mismo nombre (o ajusta `VIDEO_NAME` en `LocalMediaServer`).

Al arrancar, `LocalMediaServer` copia el video a la caché y lo publica por HTTP:

```
http://<ip-de-tu-wifi>:8090/sample_video.mp4
```

Soporta `Range` (seek), `HEAD`/`GET` y responde `video/mp4`. Se usa **el mismo URL** para la
reproducción local y para el Chromecast, así que al elegir un dispositivo Cast el video se ve
en la TV.

Requisitos:

- Teléfono y Chromecast en la **misma red Wi-Fi**.
- Puerto **8090** libre (si está ocupado, se usa uno efímero automáticamente).
- `android:usesCleartextTraffic="true"` en el manifest, porque el video se sirve por HTTP local.
- La app debe seguir viva mientras se reproduce (el servicio la mantiene en primer plano).

Si no hay red local disponible, el servidor cae a `127.0.0.1` y la reproducción local sigue
funcionando; solo el envío al Chromecast requiere LAN.


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
