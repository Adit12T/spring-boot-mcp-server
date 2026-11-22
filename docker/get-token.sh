#!/usr/bin/env sh
# Prints an access token from the local Keycloak in docker-compose.yml.
#   ./docker/get-token.sh engineering            edi:read logs:read source:read
#   ./docker/get-token.sh orders                 orders:read
#   ./docker/get-token.sh orders payments:refund orders:read + payments:refund
set -eu
case "${1:-engineering}" in
  engineering) id=mcp-engineering-agent; secret=engineering-agent-dev-secret ;;
  orders)      id=mcp-orders-agent;      secret=orders-agent-dev-secret ;;
  *) echo "usage: $0 engineering|orders [extra-scope]" >&2; exit 2 ;;
esac
extra=""
[ -n "${2:-}" ] && extra="--data-urlencode scope=$2"
# shellcheck disable=SC2086
token=$(curl -fsS "${KEYCLOAK_URL:-http://localhost:8081}/realms/mcp-realm/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id="$id" -d client_secret="$secret" $extra \
  | sed -n 's/.*"access_token" *: *"\([^"]*\)".*/\1/p')
[ -n "$token" ] || { echo "No access token returned; is Keycloak up (docker compose ps)?" >&2; exit 1; }
echo "$token"
