# App Android

App en Kotlin con Jetpack Compose. Paquete `com.vueltax.app`, minSdk 26, targetSdk 37.

## Módulos y capas

- `domain/`: módulo Gradle de Kotlin puro (sin Android). Modelos, `AppConfig` y reglas de
  negocio, con pruebas unitarias.
- `app/`: módulo Android, con las capas en paquetes:
  - `ui/`: pantallas Compose, navegación y tema.
  - `data/`: Room cifrado con SQLCipher (`local`), cliente Retrofit de la API pública
    (`remote`) y sincronización con WorkManager (`sync`).
  - `capture/`: captura de facturas con CameraX y ML Kit, siempre en el dispositivo:
    parsers de QR por país (`parser`), OCR (`ocr`), cámara (`camera`), fotos cifradas
    (`storage`) y borrador en revisión (`review`).
  - `di/`: módulos de Hilt.

## Motor de reglas (`domain/rules`)

- `RuleSet.parse(json)`: el mismo modelo que `rules/schema.json`.
- `evaluateTraveler`, `evaluatePurchase` y `estimateRefund`: elegibilidad del viajero, si
  una factura califica (con «te faltan X para el mínimo») y estimación de la devolución.
- Todo el dinero es `BigDecimal`. Los motivos traen un texto en español; la traducción a
  otros idiomas está pendiente.

## Formatos de QR de factura

| País | Especificación usada | Qué trae el QR |
| ---- | -------------------- | -------------- |
| CO | Anexo Técnico de Factura Electrónica de Venta v1.9 (Resolución DIAN 000165 de 2023), numeral 11.1.7 | Número, fecha, NIT, valor, IVA, total, CUFE |
| PE | Anexo C de la RS 113-2018/SUNAT, «Código de barras QR» | RUC, tipo, serie, número, IGV, total, fecha |
| MX | Anexo 20 de la RMF 2022 (DOF 13-01-2022), apartado D | UUID, RFC emisor, total; **no trae IVA ni fecha** |

El documento del comprador que traen los tres QR se descarta al leerlos.

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
- Las fotos de facturas se guardan cifradas (AES-GCM, clave en Android Keystore) en el
  almacenamiento interno, nunca en la galería.
- ML Kit lee códigos y texto con modelos incluidos en la app, sin enviar imágenes. Pendiente
  de revisar: las bibliotecas de ML Kit pueden enviar a Google métricas de uso propias (no
  el contenido); hay que confirmar si eso es aceptable para la regla de privacidad.
