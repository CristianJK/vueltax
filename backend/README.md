# Backend

API REST con Ktor + PostgreSQL que sirve **solo datos públicos de referencia**: reglas de
devolución por país, puntos de devolución y tasas de cambio. No tiene tablas ni endpoints
de usuarios, viajeros, facturas ni vuelos (ver la regla de privacidad en
[CLAUDE.md](../CLAUDE.md)); `SchemaPrivacyTest` lo comprueba.

## Endpoints públicos

Solo `GET`, sin autenticación. Llevan `Cache-Control` y `ETag`; con `If-None-Match`
responden `304`.

| Endpoint | Respuesta |
| --- | --- |
| `GET /v1/countries` | Países soportados y la versión vigente de sus reglas |
| `GET /v1/rules/{country}` | Última versión del rule set (el JSON de `rules/`) |
| `GET /v1/rules/{country}/version` | `{"country":"CO","version":1}`, para saber si hay que actualizar |
| `GET /v1/refund-points?country=CO` | Puntos de devolución; `country` es opcional |
| `GET /v1/rates?base=USD&date=YYYY-MM-DD` | Tasas de la fecha o, si no hay, las más recientes de los 7 días anteriores; `date` es opcional (hoy) |
| `GET /health` | `200` si la API llega a la base de datos, `503` si no |

Cada tasa indica su fuente (`frankfurter` o `trm-datos.gov.co`) y la fecha en que se publicó.

## Endpoints de administración

Requieren `Authorization: Bearer $ADMIN_TOKEN`. En el despliegue con Docker, Caddy no los
publica: se usan desde el servidor por `http://127.0.0.1:8080`.

| Endpoint | Uso |
| --- | --- |
| `POST /v1/admin/rules` | Publica un rule set; el cuerpo es un `rules/<PAÍS>.json`. Se valida contra `rules/schema.json` (`422` si no cumple). Las versiones son inmutables: repetir una idéntica da `200`; cambiarla sin subir `version` da `409` |
| `POST /v1/admin/refund-points` | Crea un punto de devolución (`409` si ya existe ese país + aeropuerto + terminal) |
| `PUT /v1/admin/refund-points/{id}` | Reemplaza un punto de devolución |
| `DELETE /v1/admin/refund-points/{id}` | Elimina un punto de devolución |
| `POST /v1/admin/rates/refresh` | Descarga las tasas ahora, sin esperar al job diario |

## Tasas de cambio

Un job descarga las tasas al arrancar y cada día a `RATES_JOB_TIME_UTC`:

- **Frankfurter v2** (`api.frankfurter.dev`) para las bases de `RATES_BASES`.
- **TRM de Colombia** (USD→COP) desde datos.gov.co, dataset `32sa-8pi3`.

Cada fuente se reintenta hasta 4 veces con espera creciente; si una falla, las demás se
guardan igual. Solo se guardan las tasas vigentes: no se descarga el histórico anterior al
primer arranque.

## Configuración

| Variable | Por defecto | |
| --- | --- | --- |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | — | Obligatorias. URL JDBC, p. ej. `jdbc:postgresql://localhost:5432/vueltax` |
| `ADMIN_TOKEN` | — | Obligatoria, mínimo 32 caracteres |
| `PORT` | `8080` | |
| `TRUST_FORWARDED_HEADERS` | `false` | `true` solo detrás de un proxy propio; da la IP real al rate limit |
| `CORS_ALLOWED_ORIGINS` | vacío | Orígenes separados por coma; vacío = CORS cerrado |
| `RATE_LIMIT_PER_MINUTE` / `ADMIN_RATE_LIMIT_PER_MINUTE` | `120` / `30` | Por IP |
| `RATES_JOB_ENABLED` | `true` | |
| `RATES_JOB_TIME_UTC` | `23:30` | |
| `RATES_BASES` | `USD,EUR` | |
| `RATES_QUOTES` | COP, PEN, MXN y monedas de origen habituales | |

Los logs solo registran método, ruta (sin query string) y estado: ni IP, ni cabeceras, ni
cuerpos.

## Desarrollo

```
./gradlew test     # requiere Docker en marcha (Testcontainers levanta PostgreSQL)
./gradlew run      # con las variables obligatorias definidas y un PostgreSQL accesible
```

Las migraciones (Flyway, `src/main/resources/db/migration`) se aplican al arrancar.
Despliegue con Docker Compose: ver [deploy/](../deploy).
