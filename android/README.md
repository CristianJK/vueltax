# App Android

App en Kotlin con Jetpack Compose. Paquete `com.vueltax.app`, minSdk 26, targetSdk 37.

## Módulos y capas

- `domain/`: módulo Gradle de Kotlin puro (sin Android). Modelos, `AppConfig` y reglas de
  negocio, con pruebas unitarias.
- `app/`: módulo Android, con las capas en paquetes:
  - `ui/`: pantallas Compose, navegación y tema.
  - `data/`: Room cifrado con SQLCipher (`local`), cliente Retrofit de la API pública
    (`remote`) y sincronización con WorkManager (`sync`).
  - `capture/`: se añadirá con la captura de facturas.
  - `di/`: módulos de Hilt.

## Configuración

Copia `local.properties.example` como `local.properties` y ajusta `API_BASE_URL`. Sin ese
valor se usa `http://10.0.2.2:8080/` (el equipo anfitrión visto desde el emulador). En debug
solo se permite HTTP en claro hacia el host de esa URL; release exige HTTPS.

## Comandos

```
./gradlew assembleDebug        # compilar
./gradlew test                 # pruebas unitarias (domain y app)
./gradlew ktlintCheck detekt   # estilo y análisis estático
./gradlew ktlintFormat         # corregir el formato automáticamente
```

## Privacidad

- Los datos del usuario solo viven en la base de datos cifrada; la clave se genera en el
  dispositivo y se guarda cifrada con Android Keystore.
- Las copias de seguridad y la transferencia entre dispositivos están desactivadas.
- El cliente HTTP solo hace GET: `GetOnlyInterceptor` bloquea cualquier otro método y
  `PublicApiPrivacyTest` comprueba que la interfaz no declare envíos de datos.
