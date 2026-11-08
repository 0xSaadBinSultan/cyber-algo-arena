#!/usr/bin/env bash
# ==============================================================================
# Cyber-Algo Arena: Permanent Static Demo Judge Starter
# Uses your permanent static ngrok domain:
# https://unwashed-cartload-flattery.ngrok-free.dev
# ==============================================================================
set -euo pipefail

STATIC_DOMAIN="unwashed-cartload-flattery.ngrok-free.dev"
TOKEN="1NUx4x6l8fdW4WTDIBocuSJuRTX6Y4PQiNnyEMCqxzyNbhCUrw7sew"
ARENA_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=== 1. Starting local Piston execution engine (Docker) ==="
if ! docker image inspect cyber-algo-piston-demo:local >/dev/null 2>&1; then
    docker build -t cyber-algo-piston-demo:local -f "$ARENA_ROOT/infra/piston-demo/Dockerfile" "$ARENA_ROOT"
fi

if docker container inspect arena-piston-demo >/dev/null 2>&1; then
    docker start arena-piston-demo >/dev/null
else
    mkdir -p "$ARENA_ROOT/../piston-data"
    docker run -d --name arena-piston-demo --privileged \
        -p 127.0.0.1:2000:2000 -v "$ARENA_ROOT/../piston-data:/piston" \
        cyber-algo-piston-demo:local >/dev/null
fi

echo "Waiting for local Piston on port 2000..."
for i in {1..20}; do
    if curl -s http://127.0.0.1:2000/api/v2/runtimes >/dev/null 2>&1; then
        break
    fi
    sleep 1
done

echo "=== 2. Starting authenticated judge gateway on port 2001 ==="
pkill -f "judge-demo-gateway.py" >/dev/null 2>&1 || true
ARENA_JUDGE_TOKEN="$TOKEN" python3 "$ARENA_ROOT/scripts/judge-demo-gateway.py" > "$ARENA_ROOT/../judge-gateway.log" 2>&1 &
sleep 1

echo "=== 3. Starting ngrok static tunnel ($STATIC_DOMAIN) ==="
pkill -f "ngrok http 2001" >/dev/null 2>&1 || true
ngrok http 2001 --domain="$STATIC_DOMAIN" --log=stdout > "$ARENA_ROOT/../judge-ngrok.log" 2>&1 &
sleep 2

# Verify
echo "Verifying judge reachability..."
if curl -sS -H "Authorization: Bearer $TOKEN" "https://$STATIC_DOMAIN/api/v2/runtimes" | grep -q "10.2.0"; then
    echo "=================================================================="
    echo "✅ SUCCESS: Static CP Judge is LIVE and ready for your presentation!"
    echo "   Permanent URL: https://$STATIC_DOMAIN"
    echo "   API Key:       $TOKEN"
    echo ""
    echo "   Set this PISTON_URL on Render ONCE and you will never need"
    echo "   to change it again!"
    echo "=================================================================="
else
    echo "⚠️ Warning: Tunnel started, but verification timed out. Check ../judge-ngrok.log"
fi
