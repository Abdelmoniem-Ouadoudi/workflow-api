#!/usr/bin/env bash
# Counts the CORS headers a browser would receive. curl does not enforce CORS, so a duplicated
# Access-Control-Allow-Origin passes every ordinary test and still breaks every request in a
# browser. Run this after changing anything about CORS, the gateway routes, or a security config.
BASE=http://localhost:8090
ORIGIN=http://localhost:5173

TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"moni","password":"password123"}' \
  | python -c "import sys,json;print(json.load(sys.stdin).get('token',''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "Could not log in. Is the gateway on 8090 and auth-service on 8082 up?"
  exit 1
fi

fail=0
count() {
  local label="$1"; shift
  local n
  n=$(curl -s -o /dev/null -D - -H "Origin: $ORIGIN" "$@" | grep -ci '^Access-Control-Allow-Origin:')
  if [ "$n" = "1" ]; then
    printf 'OK    1 header   %s\n' "$label"
  else
    printf 'BROKEN %s headers  %s   <- a browser refuses this\n' "$n" "$label"; fail=1
  fi
}

count "GET /projects"            $BASE/projects -H "Authorization: Bearer $TOKEN"
count "GET /issues"              $BASE/issues   -H "Authorization: Bearer $TOKEN"
count "GET /boards"              $BASE/boards   -H "Authorization: Bearer $TOKEN"
count "GET /users"               $BASE/users    -H "Authorization: Bearer $TOKEN"
count "GET /auth/me"             $BASE/auth/me  -H "Authorization: Bearer $TOKEN"
count "preflight for /projects"  $BASE/projects -X OPTIONS \
  -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: authorization"
count "401 with no token"        $BASE/projects

echo
[ "$fail" -eq 0 ] && echo "CORS is clean: exactly one Access-Control-Allow-Origin everywhere." \
                  || echo "Duplicated CORS header. The browser will report the server as unreachable."
exit $fail
