# Auto Reply — App de respuestas automáticas con IA

Lee las notificaciones de **cualquier app** (WhatsApp, Instagram, Telegram, SMS, etc.) y responde automáticamente usando IA, a través del botón de "Responder rápido" que ya trae cada notificación.

## Opción A — Compilar 100% desde el celular (sin computadora)

El proyecto ya incluye un archivo (`.github/workflows/build.yml`) que le pide a GitHub que compile el APK por vos, en la nube. Solo necesitás subir el código.

1. Desde el navegador del celular, entrá a **github.com** y creá una cuenta gratis (si no tenés).
2. Tocá el **+** arriba a la derecha → **New repository**. Poné un nombre, por ejemplo `auto-reply-app`, dejalo en **Private**, y creá el repositorio (sin agregar README).
3. En la pantalla del repositorio recién creado, buscá el link **"uploading an existing file"** (o andá a la pestaña del repo → botón **Add file → Upload files**).
4. Ahora hay que subir **todos** los archivos y carpetas de `AutoReplyApp` manteniendo la misma estructura de carpetas. Esto es lo único un poco incómodo desde el celular:
   - Descomprimí el .zip con una app de archivos (ej. "Archivador ZIP" o la que traiga tu celular).
   - En GitHub, "Upload files" permite arrastrar/seleccionar varios archivos a la vez, pero **no carpetas completas** desde el navegador móvil fácilmente. La forma más simple: instalá la app oficial **"GitHub"** desde Play Store, iniciá sesión, y usá **Termux + git** (ver Opción B) o subí carpeta por carpeta desde la web (Upload files respeta la ruta si arrastrás una carpeta entera desde un explorador de archivos que lo permita, como Files by Google en Android).
5. Una vez subido todo, andá a la pestaña **Actions** del repositorio. Debería aparecer un workflow "Build APK" corriendo solo (tarda 3-5 minutos).
6. Cuando termine (tilde verde ✅), entrá a esa ejecución → abajo vas a ver **Artifacts** → `AutoReplyApp-debug` → descargalo. Es un .zip con el .apk adentro.
7. Descomprimilo, tocá el .apk desde el explorador de archivos del celular, y Android te va a pedir permiso para "instalar apps de orígenes desconocidos" — lo aceptás, y listo, se instala.

**Nota:** subir carpetas completas desde el navegador del celular es la parte más molesta. Si se complica, la Opción B es más prolija.

## Opción B — Termux + git (recomendado si el paso 4 se complica)

1. Instalá **Termux** desde F-Droid (no está en Play Store) o GitHub releases de Termux.
2. Instalá git: `pkg install git`
3. Cloná un repositorio vacío que hayas creado en GitHub, copiá los archivos del proyecto adentro (podés transferirlos por cable, Drive, o Termux puede acceder a la carpeta de Descargas con `termux-setup-storage`), y luego:
   ```
   git add .
   git commit -m "primera version"
   git push
   ```
4. Seguí desde el paso 5 de la Opción A (pestaña Actions → descargar APK).

## Opción C — Con computadora (Android Studio)

1. Abrí Android Studio → **Open** → seleccioná la carpeta `AutoReplyApp`.
2. Esperá a que sincronice Gradle (puede tardar varios minutos la primera vez).
3. Conectá tu teléfono por USB con la depuración USB activada.
4. Hacé clic en el botón ▶ (Run) con tu teléfono seleccionado como dispositivo.

## Configuración en el teléfono (una sola vez)

1. Abrí la app "Auto Reply".
2. Pegá tu **API key de Anthropic** (la generás en console.anthropic.com → API Keys).
3. Escribí las instrucciones de cómo querés que responda (tono, qué decir, qué evitar).
4. Tocá "Guardar configuración".
5. Tocá **"1. Habilitar acceso a notificaciones"** → buscá "Auto Reply" en la lista → activalo.
6. Tocá **"2. Habilitar burbuja flotante"** → activá "Permitir mostrar sobre otras apps".
7. Activá el switch "Auto-respuesta activa".

## Importante: cómo funciona realmente

- Una vez que le das el permiso de notificaciones (paso 5), el servicio queda corriendo en segundo plano de forma **permanente**, no solo mientras la app está abierta. Esto es mejor que lo que pediste: no hace falta tener la app abierta en pantalla.
- Solo responde a notificaciones que traen la acción nativa de "Responder" (la inmensa mayoría de apps de mensajería la tienen). Si una notificación no la trae, se ignora — no hay forma de simular que "toca la pantalla" de otra app sin permisos mucho más invasivos y menos confiables.
- Para que Android no mate el servicio en segundo plano, andá a Ajustes → Batería → Auto Reply → **"Sin restricciones"** (el nombre exacto varía según el fabricante: Samsung, Xiaomi y Huawei son particularmente agresivos matando apps en segundo plano).

## Advertencia sobre el uso

Responde **sin que vos revises el mensaje antes de que se envíe**, a **todos** los mensajes entrantes de todas las apps con esa función. Te recomiendo:
- Probarlo primero con vos misma o un contacto de prueba.
- Escribir instrucciones bien específicas sobre qué NO debe contestar (ej: "si preguntan algo sobre un caso judicial puntual, no des detalles, solo decí que te van a contactar").
- Tener presente que puede responder mensajes de clientes, juzgados o contrapartes sin que lo veas hasta después.

## Estructura del proyecto

```
AutoReplyApp/
├── app/src/main/java/com/yami/autoreply/
│   ├── MainActivity.kt              → pantalla de configuración
│   ├── ReplyNotificationListener.kt → detecta y responde notificaciones
│   ├── ClaudeApiClient.kt           → llama a la API de Claude
│   ├── FloatingBubbleService.kt     → burbuja flotante de estado
│   └── SecurePrefs.kt               → guarda la API key cifrada
└── app/src/main/res/                → layouts e íconos
```
