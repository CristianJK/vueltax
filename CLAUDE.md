# Vueltax

Proyecto universitario. Monorepo con una app Android en Kotlin que ayuda a turistas
extranjeros a preparar la devolución de impuestos (IVA/IGV) en Latinoamérica, más un
backend Ktor + PostgreSQL que sirve únicamente datos públicos.

## Alcance del MVP

- Países: **Colombia** (IVA), **Perú** (IGV) y **México** (IVA).
- La app **ayuda a preparar** la solicitud de devolución (requisitos, elegibilidad,
  cálculo estimado, organización de facturas, puntos de devolución). **No tramita** la
  devolución ni se comunica con las autoridades tributarias.
- El backend publica datos de referencia: reglas por país, tasas, montos mínimos,
  puntos de devolución y versiones de esas reglas.

## Privacidad — regla NO NEGOCIABLE

**Ningún dato personal se envía al backend.** Esto incluye, sin excepción:

- Pasaporte o cualquier documento de identidad.
- Vuelos, itinerarios y fechas de viaje.
- Facturas, sus imágenes, montos o comercios.
- Nombre o cualquier otro dato que identifique al usuario.

Todo eso vive y se procesa **solo en el dispositivo**.

El backend expone únicamente:

- Endpoints `GET` de datos públicos (sin autenticación, sin parámetros personales).
- Endpoints de administración (alta/edición de datos públicos) protegidos por token.

Consecuencias prácticas:

- La app nunca hace `POST`/`PUT` con datos del usuario; solo descarga datos públicos.
- No se añaden analíticas, crash reporting ni SDKs de terceros que envíen datos
  personales fuera del dispositivo.
- Los parámetros de consulta y los logs del backend no deben contener datos personales.
- Si una funcionalidad parece requerir enviar datos personales, **no se implementa**:
  se rediseña para resolverse en el dispositivo.

## Estructura del repositorio

```
android/   App Android (Kotlin)
backend/   API Ktor + PostgreSQL (solo datos públicos)
rules/     Reglas de devolución por país (CO, PE, MX)
deploy/    Docker Compose y configuración de despliegue
docs/      Documentación del proyecto
```

`android/` y `backend/` son builds de Gradle independientes, cada uno con su propio
wrapper.

## Arquitectura

### Android

Capas con MVVM:

- **ui**: pantallas y ViewModels. Sin lógica de negocio.
- **domain**: modelos, casos de uso y reglas de cálculo/elegibilidad. **Kotlin puro,
  sin dependencias de Android**, cubierto con pruebas unitarias.
- **data**: repositorios, almacenamiento local y cliente HTTP de datos públicos.
- **capture**: captura de documentos y facturas (cámara, OCR), siempre en el dispositivo.

Dependencias: `ui → domain ← data`, `capture → domain`. El dominio no depende de
ninguna otra capa.

### Backend

Ktor + PostgreSQL. Solo datos públicos (ver regla de privacidad).

## Nombre de la app

El nombre es **"Vueltax"** y se define en **un solo lugar**:

- Recurso `app_name` en `strings.xml` (para Android/UI).
- Objeto `AppConfig` (para código Kotlin).

No se escribe el literal `"Vueltax"` en ningún otro punto del código.

## Convenciones

- Estilo oficial de Kotlin (`kotlin.code.style=official`).
- Commits pequeños y enfocados.
- Cada fase se entrega con sus pruebas.

## Comandos

> Los módulos Gradle aún no existen; estos son los comandos previstos y deben
> verificarse (y corregirse aquí) cuando se creen `android/` y `backend/`.

Android (desde `android/`):

```
./gradlew assembleDebug        # compilar
./gradlew test                 # pruebas unitarias
```

Backend (desde `backend/`):

```
./gradlew build                # compilar
./gradlew test                 # pruebas
./gradlew run                  # levantar la API en local
```

Reglas (desde `rules/`, requiere Node; verificado):

```
npm install                    # una vez
npm test                       # valida CO/PE/MX.json contra schema.json
```

Base de datos y despliegue local (desde `deploy/`):

```
docker compose up -d           # levantar PostgreSQL (y el backend, si aplica)
docker compose down            # detener
```

En Windows, usar `.\gradlew.bat` en lugar de `./gradlew`.
