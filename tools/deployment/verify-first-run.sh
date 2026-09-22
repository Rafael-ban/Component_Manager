#!/usr/bin/env bash
set -Eeuo pipefail

image=${1:?usage: verify-first-run.sh IMAGE}
port=${PORT:-18788}
name="component-vault-first-run-${GITHUB_RUN_ID:-$$}"
data_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/component-vault-first-run-${GITHUB_RUN_ID:-$$}"
mkdir -p "$data_dir"
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

request() { curl --silent --show-error --max-time 5 "$@"; }
status() { curl --silent --output /dev/null --max-time 5 --write-out '%{http_code}' "$@"; }
wait_for_health() {
  for _ in $(seq 1 30); do
    if [ "$(status "http://127.0.0.1:${port}/health")" = 200 ]; then return 0; fi
    sleep 1
  done
  return 1
}
assert_status_any() {
  local actual="$1" expected
  shift
  for expected in "$@"; do [ "$actual" = "$expected" ] && return 0; done
  echo "unexpected HTTP status: $actual" >&2
  return 1
}

docker run -d --name "$name" -p "127.0.0.1:${port}:8787" -v "${data_dir}:/data" "$image" >/dev/null
wait_for_health
test "$(status "http://127.0.0.1:${port}/setup")" = 200
request "http://127.0.0.1:${port}/setup/status" | python -c 'import json,sys; assert json.load(sys.stdin)["configured"] is False'
test "$(status -X POST "http://127.0.0.1:${port}/auth/ping")" = 401
test "$(status -X POST -H 'Content-Type: application/json' -d '{"api_token":"ci-first-run-token","admin_web_origins":["http://localhost:8081"],"web_inventory_enabled":true}' "http://127.0.0.1:${port}/setup/config")" = 200
assert_status_any "$(status -X POST -H 'Content-Type: application/json' -d '{"api_token":"ci-first-run-token","admin_web_origins":["http://localhost:8081"],"web_inventory_enabled":true}' "http://127.0.0.1:${port}/setup/config")" 401 403 409
assert_status_any "$(status "http://127.0.0.1:${port}/setup/config")" 401 403 409
test "$(status -H 'Authorization: Bearer ci-first-run-token' "http://127.0.0.1:${port}/setup/config")" = 200
test "$(status "http://127.0.0.1:${port}/setup/logs")" = 401
test "$(status -H 'Authorization: Bearer ci-first-run-token' "http://127.0.0.1:${port}/setup/logs")" = 200
test -f "$data_dir/config.json"
test -f "$data_dir/logs/server.log"
python -c 'import json,sys; assert json.load(open(sys.argv[1], encoding="utf-8"))["API_TOKEN"] == "ci-first-run-token"' "$data_dir/config.json"
docker restart "$name" >/dev/null
wait_for_health
request "http://127.0.0.1:${port}/setup/status" | python -c 'import json,sys; assert json.load(sys.stdin)["configured"] is True'
request -H 'Authorization: Bearer ci-first-run-token' "http://127.0.0.1:${port}/setup/config" | python -c 'import json,sys; value=json.load(sys.stdin); assert "api_token" not in value; assert value["web_inventory_enabled"] is True; assert value["admin_web_origins"] == ["http://localhost:8081"]'
echo "Server first-run configuration and persistence: OK"
