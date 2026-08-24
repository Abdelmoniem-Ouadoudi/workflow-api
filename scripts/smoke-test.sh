#!/usr/bin/env bash
# Exercises every endpoint through the gateway and prints PASS/FAIL per case.
#
# Since M2 this runs against the gateway on 8090, not work-service on 8081, because that is what
# a real caller talks to: routing, the token check and the circuit breaker are all on that path.
# Everything but the two /auth doors carries a bearer token.
BASE=http://localhost:8090
pass=0; fail=0

J="Content-Type: application/json"
TOKEN=""

# check <name> <expected-status> <curl args...>
# The token is added here rather than at every call site, so one line covers every request.
check() {
  local name="$1"; local want="$2"; shift 2
  local body status auth=()
  [ -n "$TOKEN" ] && auth=(-H "Authorization: Bearer $TOKEN")
  body=$(curl -s -w '\n%{http_code}' "${auth[@]}" "$@")
  status=$(echo "$body" | tail -1)
  LAST=$(echo "$body" | sed '$d')
  if [ "$status" = "$want" ]; then
    printf 'PASS  %-3s  %s\n' "$status" "$name"; pass=$((pass+1))
  else
    printf 'FAIL  got %s want %s  %s\n      %s\n' "$status" "$want" "$name" "$(echo "$LAST" | head -c 200)"; fail=$((fail+1))
  fi
}

# Runs without the bearer header even when a token is held, to prove a door is really open
# or really shut.
check_anon() {
  local name="$1"; local want="$2"; shift 2
  local saved="$TOKEN"; TOKEN=""
  check "$name" "$want" "$@"
  TOKEN="$saved"
}

# expect_field <name> <json field> <expected value> - asserts on the body of the last check.
expect_field() {
  local name="$1"; local field="$2"; local want="$3"
  local got
  got=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin).get('$field'))")
  if [ "$got" = "$want" ]; then
    printf 'PASS  --   %s\n' "$name"; pass=$((pass+1))
  else
    printf 'FAIL  got %s want %s  %s\n' "$got" "$want" "$name"; fail=$((fail+1))
  fi
}

id_of()    { echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin)['id'])"; }
token_of() { echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin)['token'])"; }
uid_of()   { echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin)['userId'])"; }

echo "########## THE DOOR IS SHUT ##########"
# M2's acceptance test: an anonymous request is refused at the edge and never reaches a service.
check_anon "anonymous /projects -> 401" 401 $BASE/projects
check_anon "anonymous /issues -> 401" 401 $BASE/issues
check_anon "garbage token -> 401" 401 $BASE/projects -H "Authorization: Bearer not.a.token"
check_anon "health is open" 200 $BASE/actuator/health

echo "########## AUTH ##########"
A="admin$RANDOM"
check_anon "register admin" 201 -X POST $BASE/auth/register -H "$J" -d "{\"username\":\"$A\",\"email\":\"$A@dev.ma\",\"password\":\"password123\",\"role\":\"ADMIN\"}"
TOKEN=$(token_of); ADMIN_ID=$(uid_of)
check_anon "register duplicate username -> 422" 422 -X POST $BASE/auth/register -H "$J" -d "{\"username\":\"$A\",\"email\":\"other$A@dev.ma\",\"password\":\"password123\",\"role\":\"ADMIN\"}"
check_anon "register short password -> 400" 400 -X POST $BASE/auth/register -H "$J" -d '{"username":"shorty","email":"s@dev.ma","password":"abc","role":"DEVELOPER"}'
check_anon "register bad email -> 400" 400 -X POST $BASE/auth/register -H "$J" -d '{"username":"bademail","email":"nope","password":"password123","role":"DEVELOPER"}'
check_anon "register unknown role -> 400" 400 -X POST $BASE/auth/register -H "$J" -d '{"username":"wizard","email":"w@dev.ma","password":"password123","role":"WIZARD"}'
check_anon "login" 200 -X POST $BASE/auth/login -H "$J" -d "{\"username\":\"$A\",\"password\":\"password123\"}"
check_anon "wrong password -> 401" 401 -X POST $BASE/auth/login -H "$J" -d "{\"username\":\"$A\",\"password\":\"wrongpassword\"}"
check_anon "unknown username -> 401" 401 -X POST $BASE/auth/login -H "$J" -d '{"username":"nobody","password":"password123"}'
check "who am i" 200 $BASE/auth/me
check_anon "who am i, no token -> 401" 401 $BASE/auth/me

# A second, lesser account: the role rules below need someone who is not an admin.
check_anon "register developer" 201 -X POST $BASE/auth/register -H "$J" -d "{\"username\":\"dev$A\",\"email\":\"dev$A@dev.ma\",\"password\":\"password123\",\"role\":\"DEVELOPER\"}"
DEV_TOKEN=$(token_of); DEV_ID=$(uid_of)

echo "########## USERS ##########"
# POST /users is service-only now: registration is the one way a person is created, so a profile
# can never exist without a login behind it.
check "create user directly -> 403" 403 -X POST $BASE/users -H "$J" -d '{"username":"sneaky","email":"s@dev.ma","role":"ADMIN"}'
check "list users" 200 $BASE/users
check "get user" 200 $BASE/users/$DEV_ID
check "update user" 200 -X PUT $BASE/users/$DEV_ID -H "$J" -d "{\"username\":\"dev$A\",\"email\":\"dev$A@dev.ma\",\"role\":\"MANAGER\"}"
check "get missing user -> 404" 404 $BASE/users/999999

echo "########## ROLE RULES ##########"
# The one rule that proves the role claim is enforced at the service, not just carried around.
SAVED=$TOKEN; TOKEN=$DEV_TOKEN
check "developer cannot deactivate -> 403" 403 -X DELETE $BASE/users/$DEV_ID
TOKEN=$SAVED
check "admin can deactivate" 200 -X DELETE $BASE/users/$DEV_ID

echo "########## PROJECTS ##########"
K="P$RANDOM"; K=${K:0:6}
check "create project" 201 -X POST $BASE/projects -H "$J" -d "{\"key\":\"$K\",\"name\":\"Smoke project\",\"description\":\"d\"}"
PID=$(id_of)
check "duplicate key -> 422" 422 -X POST $BASE/projects -H "$J" -d "{\"key\":\"$K\",\"name\":\"again\"}"
check "lowercase key -> 400" 400 -X POST $BASE/projects -H "$J" -d '{"key":"lower","name":"x"}'
check "blank name -> 400" 400 -X POST $BASE/projects -H "$J" -d "{\"key\":\"ZZ1\",\"name\":\"\"}"
check "list projects" 200 $BASE/projects
check "get project" 200 $BASE/projects/$PID
check "update project" 200 -X PUT $BASE/projects/$PID -H "$J" -d "{\"key\":\"$K\",\"name\":\"Renamed\"}"
check "bad id type -> 400" 400 $BASE/projects/abc

echo "########## BOARDS ##########"
check "boards of project (auto-created)" 200 "$BASE/boards?projectId=$PID"
BID=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin)[0]['id'])")
check "get board" 200 $BASE/boards/$BID
check "create second board" 201 -X POST $BASE/boards -H "$J" -d "{\"name\":\"Scrum board\",\"type\":\"SCRUM\",\"projectId\":$PID}"
B2=$(id_of)
check "board on unknown project -> 422" 422 -X POST $BASE/boards -H "$J" -d '{"name":"x","type":"KANBAN","projectId":999999}'
check "update board" 200 -X PUT $BASE/boards/$BID -H "$J" -d "{\"name\":\"Main\",\"type\":\"KANBAN\",\"projectId\":$PID}"
check "delete second board" 204 -X DELETE $BASE/boards/$B2
check "delete last board -> 422" 422 -X DELETE $BASE/boards/$BID
check "list all boards" 200 $BASE/boards

echo "########## SPRINTS ##########"
check "create sprint" 201 -X POST $BASE/sprints -H "$J" -d "{\"name\":\"S1\",\"goal\":\"g\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-15\",\"boardId\":$BID}"
SID=$(id_of)
check "end before start -> 422" 422 -X POST $BASE/sprints -H "$J" -d "{\"name\":\"bad\",\"startDate\":\"2026-08-15\",\"endDate\":\"2026-08-01\",\"boardId\":$BID}"
check "sprint on unknown board -> 422" 422 -X POST $BASE/sprints -H "$J" -d '{"name":"x","boardId":999999}'
check "list sprints of board" 200 "$BASE/sprints?boardId=$BID"
check "get sprint" 200 $BASE/sprints/$SID
check "update sprint" 200 -X PUT $BASE/sprints/$SID -H "$J" -d "{\"name\":\"S1 renamed\",\"boardId\":$BID}"
check "start sprint" 200 -X POST $BASE/sprints/$SID/start
check "start again -> 422" 422 -X POST $BASE/sprints/$SID/start
check "create sprint 2" 201 -X POST $BASE/sprints -H "$J" -d "{\"name\":\"S2\",\"boardId\":$BID}"
S2=$(id_of)
check "second active sprint -> 422" 422 -X POST $BASE/sprints/$S2/start
check "delete active sprint -> 422" 422 -X DELETE $BASE/sprints/$SID
check "delete planned sprint" 204 -X DELETE $BASE/sprints/$S2

echo "########## ISSUES ##########"
# No reporterId is sent any more: the server takes it from the token.
check "create issue" 201 -X POST $BASE/issues -H "$J" -d "{\"title\":\"First\",\"type\":\"BUG\",\"priority\":\"HIGH\",\"projectId\":$PID,\"boardId\":$BID,\"sprintId\":$SID}"
IID=$(id_of)
expect_field "reporter came from the token, not the body" reporterId "$ADMIN_ID"
# Sending someone else's id must change nothing: the field is read-only on the wire.
check "forged reporterId is ignored" 201 -X POST $BASE/issues -H "$J" -d "{\"title\":\"Forged\",\"type\":\"BUG\",\"priority\":\"LOW\",\"projectId\":$PID,\"boardId\":$BID,\"reporterId\":999999}"
expect_field "forged reporterId had no effect" reporterId "$ADMIN_ID"
I3=$(id_of)
check "issue key increments" 201 -X POST $BASE/issues -H "$J" -d "{\"title\":\"Second\",\"type\":\"TASK\",\"priority\":\"CRITICAL\",\"projectId\":$PID,\"boardId\":$BID}"
I2=$(id_of)
check "blank title -> 400" 400 -X POST $BASE/issues -H "$J" -d "{\"title\":\"\",\"type\":\"BUG\",\"priority\":\"LOW\",\"projectId\":$PID}"
check "unknown project -> 422" 422 -X POST $BASE/issues -H "$J" -d '{"title":"x","type":"BUG","priority":"LOW","projectId":999999}'
check "get issue" 200 $BASE/issues/$IID
check "filter by project" 200 "$BASE/issues?projectId=$PID"
check "filter by status" 200 "$BASE/issues?projectId=$PID&status=TO_DO"
check "filter by priority" 200 "$BASE/issues?priority=CRITICAL"
check "text search" 200 "$BASE/issues?q=first"
check "update issue" 200 -X PUT $BASE/issues/$IID -H "$J" -d "{\"title\":\"First edited\",\"type\":\"BUG\",\"priority\":\"MEDIUM\",\"projectId\":$PID,\"boardId\":$BID}"
check "assign issue" 200 -X PUT "$BASE/issues/$IID/assignee?userId=$ADMIN_ID"
check "unassign issue" 200 -X PUT "$BASE/issues/$IID/assignee"
check "illegal transition TO_DO->DONE -> 422" 422 -X PATCH $BASE/issues/$IID/status -H "$J" -d '{"status":"DONE"}'
check "legal transition TO_DO->IN_PROGRESS" 200 -X PATCH $BASE/issues/$IID/status -H "$J" -d '{"status":"IN_PROGRESS"}'
check "stale version -> 409" 409 -X PATCH $BASE/issues/$IID/status -H "$J" -d '{"status":"DONE","version":0}'
check "IN_PROGRESS->DONE" 200 -X PATCH $BASE/issues/$IID/status -H "$J" -d '{"status":"DONE"}'
check "missing issue -> 404" 404 $BASE/issues/999999

echo "########## BOARD VIEW ##########"
check "board view" 200 $BASE/boards/$BID/view
check "board backlog" 200 $BASE/boards/$BID/backlog
check "view of missing board -> 404" 404 $BASE/boards/999999/view

echo "########## COMMENTS ##########"
# No authorId is sent: the server takes it from the token.
check "create comment" 201 -X POST $BASE/issues/$IID/comments -H "$J" -d '{"content":"hello"}'
CID=$(id_of)
check "blank content -> 400" 400 -X POST $BASE/issues/$IID/comments -H "$J" -d '{"content":""}'
check "list comments" 200 $BASE/issues/$IID/comments
check "update comment" 200 -X PUT $BASE/issues/$IID/comments/$CID -H "$J" -d '{"content":"edited"}'
check "comment on wrong issue -> 404" 404 -X PUT $BASE/issues/$I2/comments/$CID -H "$J" -d '{"content":"x"}'
check "comments of missing issue -> 404" 404 $BASE/issues/999999/comments

echo "########## ATTACHMENTS ##########"
check "create attachment" 201 -X POST $BASE/issues/$IID/attachments -H "$J" -d '{"fileName":"a.png","fileUrl":"/files/a.png","fileSize":1024}'
AID=$(id_of)
check "negative size -> 400" 400 -X POST $BASE/issues/$IID/attachments -H "$J" -d '{"fileName":"a.png","fileUrl":"/f","fileSize":-5}'
check "missing fileUrl -> 400" 400 -X POST $BASE/issues/$IID/attachments -H "$J" -d '{"fileName":"a.png","fileSize":10}'
check "list attachments" 200 $BASE/issues/$IID/attachments
check "attachment on wrong issue -> 404" 404 -X DELETE $BASE/issues/$I2/attachments/$AID
check "delete attachment" 204 -X DELETE $BASE/issues/$IID/attachments/$AID

echo "########## SPRINT COMPLETE + CASCADES ##########"
check "complete sprint" 200 -X POST $BASE/sprints/$SID/complete
check "complete again -> 422" 422 -X POST $BASE/sprints/$SID/complete
check "delete comment" 204 -X DELETE $BASE/issues/$IID/comments/$CID
check "delete issue" 204 -X DELETE $BASE/issues/$IID
check "delete issue 2" 204 -X DELETE $BASE/issues/$I2
check "delete issue 3" 204 -X DELETE $BASE/issues/$I3
check "delete project" 204 -X DELETE $BASE/projects/$PID
check "project gone -> 404" 404 $BASE/projects/$PID
check "cascade: board gone -> 404" 404 $BASE/boards/$BID

echo "########## THE EDGE ITSELF ##########"
# A path no route matches must still answer in the one envelope, not Spring's default error body.
check "unrouted path -> 404 in the ApiError shape" 404 $BASE/nope
expect_field "unrouted path carries a code" code "NOT_FOUND"
# An authenticated request for a resource that does not exist must say 404, not 401. This is the
# /error dispatch: the security filters run on it a second time and used to turn it into a 401.
check "authenticated 404 stays a 404" 404 $BASE/issues/999999

echo "########## DOCS AND HEALTH (direct on 8081) ##########"
# The contract stays readable without a token: it documents shapes, not data. Checked on
# work-service directly because the gateway routes by resource path and does not proxy the docs.
check_anon "openapi json" 200 http://localhost:8081/v3/api-docs
check_anon "swagger ui" 200 http://localhost:8081/swagger-ui/index.html
check_anon "work-service health" 200 http://localhost:8081/actuator/health
check_anon "auth-service health" 200 http://localhost:8082/actuator/health
# The service defends itself: the gateway is not the only lock on the door.
check_anon "work-service direct, no token -> 401" 401 http://localhost:8081/projects

echo
echo "=================================="
echo "PASSED: $pass   FAILED: $fail"
echo "=================================="
[ "$fail" -eq 0 ]
