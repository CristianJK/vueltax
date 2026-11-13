#!/usr/bin/env bash
# Carga datos públicos iniciales por los endpoints de administración:
#   - las reglas de rules/<PAÍS>.json
#   - los puntos de devolución de ejemplo de deploy/seed/refund-points/*.json
# Es idempotente: lo que ya existe se deja como está.
#
# Uso (con la API arriba):  ./seed.sh
# Lee ADMIN_TOKEN y API_PORT de deploy/.env; API_URL permite apuntar a otra instancia.
set -euo pipefail

cd "$(dirname "$0")"
if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
fi

API_URL="${API_URL:-http://127.0.0.1:${API_PORT:-8080}}"
: "${ADMIN_TOKEN:?Define ADMIN_TOKEN en deploy/.env o en el entorno}"

response="$(mktemp)"
trap 'rm -f "$response"' EXIT

# POST de un archivo JSON; imprime el código HTTP. El token va por un descriptor de
# archivo para que no aparezca en la lista de procesos.
post() {
    curl -sS -o "$response" -w '%{http_code}' -X POST \
        -H @<(printf 'Authorization: Bearer %s' "$ADMIN_TOKEN") \
        -H 'Content-Type: application/json' \
        --data-binary @"$2" "$API_URL$1"
}

fail() {
    echo "  ERROR $1: $(cat "$response")" >&2
    exit 1
}

echo "Reglas -> $API_URL"
for file in ../rules/[A-Z][A-Z].json; do
    status="$(post /v1/admin/rules "$file")"
    case "$status" in
        201) echo "  $(basename "$file"): versión publicada" ;;
        200) echo "  $(basename "$file"): sin cambios" ;;
        *) fail "$status en $file" ;;
    esac
done

echo "Puntos de devolución de ejemplo"
for file in seed/refund-points/*.json; do
    status="$(post /v1/admin/refund-points "$file")"
    case "$status" in
        201) echo "  $(basename "$file"): creado" ;;
        409) echo "  $(basename "$file"): ya existía" ;;
        *) fail "$status en $file" ;;
    esac
done

echo "Seed completo."
