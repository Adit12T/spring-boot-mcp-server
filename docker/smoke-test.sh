#!/usr/bin/env sh
# End-to-end check of the running stack (docker compose up -d --wait first):
# anonymous calls are rejected, the engineering token can call an EDI tool, the orders token cannot.
set -eu
MCP=${MCP_URL:-http://localhost:8080/mcp}
DIR=$(dirname "$0")
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
INIT='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1"}}}'
CALL='{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"compare_edi_environments","arguments":{"lineOfBusiness":"medical"}}}'

post() { # token, body, [session]
  curl -sS -D "$TMP/headers" -o "$TMP/body" -w '%{http_code}' -X POST "$MCP" \
    -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' ${3:+-H "Mcp-Session-Id: $3"} -d "$2"
}
session() { sed -n 's/^[Mm]cp-[Ss]ession-[Ii]d: *\([^[:space:]]*\).*/\1/p' "$TMP/headers"; }
call_tool() { # token -> prints tool result body
  code=$(post "$1" "$INIT"); [ "$code" = 200 ] || { echo "initialize returned $code" >&2; cat "$TMP/body" >&2; exit 1; }
  sid=$(session)
  post "$1" '{"jsonrpc":"2.0","method":"notifications/initialized"}' "$sid" >/dev/null
  post "$1" "$CALL" "$sid" >/dev/null
  cat "$TMP/body"
}

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$MCP" -H 'Content-Type: application/json' -d "$INIT")
[ "$code" = 401 ] && echo "PASS anonymous call rejected (401)" || { echo "FAIL anonymous call returned $code"; exit 1; }

eng=$("$DIR/get-token.sh" engineering)
call_tool "$eng" | grep -q 'differencesFound' && echo "PASS engineering token ran compare_edi_environments" \
  || { echo "FAIL engineering token could not run the EDI tool"; cat "$TMP/body"; exit 1; }

orders=$("$DIR/get-token.sh" orders)
if call_tool "$orders" | grep -q '"differencesFound"'; then
  echo "FAIL orders token was allowed to run the EDI tool"; exit 1
fi
echo "PASS orders token denied the EDI tool (missing edi:read)"
