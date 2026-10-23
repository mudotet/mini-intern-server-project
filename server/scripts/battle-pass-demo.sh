#!/usr/bin/env bash
set -euo pipefail
# Run against the local Docker demo, which explicitly enables Quest grant simulation.
python3 - "${1:-http://localhost:8080}" <<'PY'
import json
import sys
import uuid
import urllib.request
import urllib.error

base = sys.argv[1].rstrip('/')
token = None

def call(path, body=None):
    headers = {'Content-Type': 'application/json', 'X-Request-Id': str(uuid.uuid4())}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(base + path,
        data=None if body is None else json.dumps(body).encode(), headers=headers,
        method='GET' if body is None else 'POST')
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f'API {path}: HTTP {error.code} {error.read().decode()}') from None

login = call('/api/001002', {})
token = login['access_token']
view = call('/api/info/battle-pass')
pass_id = view['battle_pass_id']
assert len(view['nodes']) == 10, 'Demo expects the ten-node catalog'
print('Battle Pass:', pass_id)

def grant(exp):
    return call('/api/004004', {'battle_pass_id': pass_id, 'quest_id': 'quest_demo',
        'grant_id': str(uuid.uuid4()), 'exp_bp': str(exp)})

def claim(node):
    result = call('/api/004003', {'battle_pass_id': pass_id, 'node_id': node})
    assert len(result['reward']) == 1, 'A node must grant exactly one reward'
    print(node + ':', json.dumps(result['reward']))
    return result

print('First Quest:', json.dumps(grant(400)['progress']))
carried = grant(110)['progress']
assert carried['total_exp_bp'] == '510' and carried['exp_into_next_node'] == '10'
print('Second Quest:', json.dumps(carried))
claim('node_1')
assert grant(4490)['progress']['total_exp_bp'] == '5000'
call('/api/004002', {'battle_pass_id': pass_id})
for node in range(2, 11):
    claim('node_' + str(node))
final = call('/api/info/battle-pass')
assert final['progress']['completed'] and len(final['progress']['claimed_node_ids']) == 10
assert len(final['owned_heroes']) == 4
print('Final progress:', json.dumps(final['progress']))
print('Owned Heroes:', json.dumps(final['owned_heroes']))
print('Resources:', json.dumps(final['resources']))
print('Ten-node Battle Pass demo passed. Credentials were not printed.')
PY
