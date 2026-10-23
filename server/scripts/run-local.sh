#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
umask 077
python3 - <<'PY'
from pathlib import Path
import secrets
import re
path = Path('.env')
text = path.read_text() if path.exists() else ''
values = re.findall(r'^\s*(?:export\s+)?JWT_SECRET\s*=\s*(.*?)\s*$', text, re.M)
if not values:
    with path.open('a') as output:
        output.write(('\n' if text and not text.endswith('\n') else '') + 'JWT_SECRET=' + secrets.token_hex(32) + '\n')
path.chmod(0o600)
PY
docker compose --env-file "$ROOT/.env" -f docker/docker-compose.yaml --profile app config --format json | python3 -c 'import json,sys; secret=json.load(sys.stdin)["services"]["app"]["environment"]["JWT_SECRET"]; sys.exit(0 if len(secret.encode()) >= 32 else "Resolved JWT_SECRET is too short. Update .env; existing values were not overwritten.")'
./gradlew installDist
docker compose --env-file "$ROOT/.env" -f docker/docker-compose.yaml --profile app up -d --build
printf '%s\n' 'Local services started. Wait for "Mini game API ready" in app logs.'
docker compose --env-file "$ROOT/.env" -f docker/docker-compose.yaml logs --tail 20 app
