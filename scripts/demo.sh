#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${1:-http://localhost:8080}"
BASE="${BASE%/}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
TOKEN=''
request() {
    local path="$1" body="$2" id="$3"
    set -- --fail-with-body --silent --show-error "$BASE$path" -H 'Content-Type: application/json' -H "X-Request-Id: $id" --data "$body"
    if [[ -n "$TOKEN" ]]; then set -- "$@" -H "Authorization: Bearer $TOKEN"; fi
    curl "$@"
}
new_id() { python3 -c 'import uuid; print(uuid.uuid4())'; }
request /api/001003 '{}' "$(new_id)" > "$TMP/init.json"
request /api/001002 '{}' "$(new_id)" > "$TMP/login.json"
TOKEN="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["access_token"])' "$TMP/login.json")"
request /api/002007 '{}' "$(new_id)" > "$TMP/player.json"
request /api/003004 '{}' "$(new_id)" > "$TMP/static.json"
request /api/003005 '{}' "$(new_id)" > "$TMP/daily.json"
python3 - "$TMP" <<'PY'
import json, sys
from pathlib import Path
root = Path(sys.argv[1])
for name in ('init', 'player', 'static', 'daily'):
    print(name + ': ' + json.dumps(json.loads((root / (name + '.json')).read_text())))
static = json.loads((root / 'static.json').read_text())
offer = next(slot['offer'] for slot in static['slots'] if slot['offer']['package_id'] == 'xp_gold')
(root / 'purchase.json').write_text(json.dumps({'id': offer['offer_id'], 'amount': 1}))
(root / 'purchase.txt').write_text('id: "' + offer['offer_id'] + '" amount: 1\n')
PY
ID="$(new_id)"
request /api/003002 "$(<"$TMP/purchase.json")" "$ID" > "$TMP/receipt.json"
python3 -m json.tool "$TMP/receipt.json"
protoc -I "$ROOT/src/main/proto" --encode=game.app.ShopPurchaseRequestProto "$ROOT/src/main/proto/game/app/shop/shop_purchase_request.proto" < "$TMP/purchase.txt" > "$TMP/purchase.bin"
curl --fail-with-body --silent --show-error "$BASE/api/003002" -H 'Content-Type: application/x-protobuf' -H "Authorization: Bearer $TOKEN" -H "X-Request-Id: $ID" --data-binary "@$TMP/purchase.bin" > "$TMP/receipt.bin"
protoc -I "$ROOT/src/main/proto" --decode=game.app.ShopPurchaseResponseProto "$ROOT/src/main/proto/game/app/shop/shop_purchase_response.proto" < "$TMP/receipt.bin"
for path in player resources packages session; do
    curl --fail-with-body --silent --show-error "$BASE/api/info/$path" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
done
if [[ "${DEMO_RESTART_LOCAL:-0}" == 1 ]]; then
    docker compose --env-file "$ROOT/.env" -f "$ROOT/docker/docker-compose.yaml" --profile app restart app
    for attempt in {1..30}; do
        if curl --fail --silent "$BASE/swagger/openapi.json" > /dev/null; then break; fi
        sleep 1
    done
    request /api/003002 "$(<"$TMP/purchase.json")" "$ID" > "$TMP/restarted.json"
    python3 - "$TMP/receipt.json" "$TMP/restarted.json" <<'PY'
import json, sys
assert json.load(open(sys.argv[1])) == json.load(open(sys.argv[2])), 'Receipt changed across restart'
print('Restart preserved the original purchase receipt and session.')
PY
fi
printf '%s\n' 'JSON purchase and Protobuf replay completed. Credentials were not printed.'
