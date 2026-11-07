#!/usr/bin/env bash
set -euo pipefail
arena_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$arena_root"
command -v docker >/dev/null || { echo 'Start Docker, then run this script again.'; exit 1; }
command -v java >/dev/null || { echo 'Java 21 or newer is required.'; exit 1; }
command -v python3 >/dev/null || { echo 'Python 3 is required for the runtime installer.'; exit 1; }
# The web app, MongoDB, and judge all run on this PC; Render is independent.
arena_judge_data="${ARENA_PISTON_DATA_DIR:-$arena_root/../piston-data}"
mkdir -p "$arena_judge_data"
if ! docker image inspect cyber-algo-piston-demo:local >/dev/null 2>&1; then
  docker build -t cyber-algo-piston-demo:local -f infra/piston-demo/Dockerfile .
fi
if docker container inspect arena-piston-demo >/dev/null 2>&1; then
  docker start arena-piston-demo >/dev/null
else
  docker run -d --name arena-piston-demo --privileged \
    -p 127.0.0.1:2000:2000 -v "$arena_judge_data:/piston" \
    cyber-algo-piston-demo:local
fi
for arena_try in {1..30}; do
  if python3 -c 'import urllib.request; urllib.request.urlopen("http://127.0.0.1:2000/api/v2/runtimes",timeout=2)' 2>/dev/null; then break; fi
  sleep 1
done
python3 scripts/install-judge-runtimes.py
export PISTON_URL=http://127.0.0.1:2000
unset JUDGE0_URL JUDGE0_API_KEY
export MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27017}"
export MONGODB_DATABASE_NAME="${MONGODB_DATABASE_NAME:-arena_teacher_demo}"
export PORT="${PORT:-8085}"
export ARENA_ADMIN_USERNAME="${ARENA_ADMIN_USERNAME:-admin}"
if [[ -z "${ARENA_ADMIN_PASSWORD:-}" ]]; then
  read -r -s -p 'Choose the local demo admin password (12+ characters): ' ARENA_ADMIN_PASSWORD
  echo
  export ARENA_ADMIN_PASSWORD
fi
if [[ ${#ARENA_ADMIN_PASSWORD} -lt 12 ]]; then echo 'The password must be at least 12 characters.'; exit 1; fi
if [[ ! -f target/cyber-algo-arena-1.0.0.jar ]]; then ./mvnw -q -DskipTests package; fi
echo "Local teacher demo: http://localhost:$PORT"
echo 'Keep this terminal and Docker running. Press Ctrl+C to stop the web app.'
exec java -jar target/cyber-algo-arena-1.0.0.jar
