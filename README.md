# Vueltax

App Android (Kotlin) que ayuda a turistas extranjeros a preparar la devolución de
impuestos (IVA/IGV) en Latinoamérica. MVP: Colombia, Perú y México. Proyecto
universitario.

## Estructura

| Carpeta    | Contenido                                        |
| ---------- | ------------------------------------------------ |
| `android/` | App Android (ui / domain / data / capture, MVVM) |
| `backend/` | API Ktor + PostgreSQL, solo datos públicos       |
| `rules/`   | Reglas de devolución por país                    |
| `deploy/`  | Docker Compose y despliegue                      |
| `docs/`    | Documentación                                    |

## Privacidad

Ningún dato personal (pasaporte, vuelos, facturas, nombre) sale del dispositivo. El
backend solo sirve datos públicos.

## Más información

Arquitectura, convenciones y comandos en [CLAUDE.md](CLAUDE.md).
