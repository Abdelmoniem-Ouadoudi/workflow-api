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

# expect_field_set <name> <json field> - asserts the field is present and not empty, without
# pinning its value. Used where the answer legitimately depends on which classifier is running.
expect_field_set() {
  local name="$1"; local field="$2"
  local got
  got=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin).get('$field') or '')")
  if [ -n "$got" ]; then
    printf 'PASS  --   %s (%s)\n' "$name" "$got"; pass=$((pass+1))
  else
    printf 'FAIL  %s was empty  %s\n' "$field" "$name"; fail=$((fail+1))
  fi
}

# expect_field_one_of <name> <json field> <value> [value...]
expect_field_one_of() {
  local name="$1"; local field="$2"; shift 2
  local got
  got=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin).get('$field'))")
  for want in "$@"; do
    if [ "$got" = "$want" ]; then
      printf 'PASS  --   %s (%s)\n' "$name" "$got"; pass=$((pass+1)); return
    fi
  done
  printf 'FAIL  got %s want one of [%s]  %s\n' "$got" "$*" "$name"; fail=$((fail+1))
}

# The list assertions below exist because scoping is a claim about what is NOT in a response,
# and a status code cannot express that. "You do not see this project" and "this project does not
# exist" are the same 200 with a different body.

# expect_field_in_list <name> <field> <value> - some element of the last array has field = value.
expect_field_in_list() {
  local name="$1"; local field="$2"; local want="$3"
  if echo "$LAST" | python -c "import sys,json;sys.exit(0 if any(str(r.get('$field'))=='$want' for r in json.load(sys.stdin)) else 1)"; then
    printf 'PASS  --   %s\n' "$name"; pass=$((pass+1))
  else
    printf 'FAIL  no element with %s=%s  %s\n' "$field" "$want" "$name"; fail=$((fail+1))
  fi
}

expect_present_in_list() { expect_field_in_list "$@"; }

# expect_absent_from_list <name> <field> <value> - no element has it. This is the scoping check.
expect_absent_from_list() {
  local name="$1"; local field="$2"; local want="$3"
  if echo "$LAST" | python -c "import sys,json;sys.exit(0 if any(str(r.get('$field'))=='$want' for r in json.load(sys.stdin)) else 1)"; then
    printf 'FAIL  %s=%s was visible  %s\n' "$field" "$want" "$name"; fail=$((fail+1))
  else
    printf 'PASS  --   %s\n' "$name"; pass=$((pass+1))
  fi
}

# expect_field_changed <name> <field> <old value> - the field is set and is not the old value.
expect_field_changed() {
  local name="$1"; local field="$2"; local old="$3"
  local got
  got=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin).get('$field') or '')")
  if [ -n "$got" ] && [ "$got" != "$old" ]; then
    printf 'PASS  --   %s\n' "$name"; pass=$((pass+1))
  else
    printf 'FAIL  %s is still %s  %s\n' "$field" "$got" "$name"; fail=$((fail+1))
  fi
}

# id_where <field> <value> - the id of the first element of the last array whose field matches.
# The approval queue is a list and the account just registered has to be found in it by name.
id_where() {
  echo "$LAST" | python -c "import sys,json;print(next(r['id'] for r in json.load(sys.stdin) if r['$1']=='$2'))"
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
# The bootstrap administrator, seeded by a migration. Registering one is no longer possible:
# RegisterRequest has no role field since M5, so every new account is a PENDING DEVELOPER and
# somebody who is already an administrator has to say otherwise. This account is where that
# chain has to start, and it is the only row in `account` nobody approved.
check_anon "login as the seeded admin" 200 -X POST $BASE/auth/login -H "$J" -d '{"username":"admin","password":"admin12345"}'
TOKEN=$(token_of); ADMIN_ID=$(uid_of)
check "who am i" 200 $BASE/auth/me
expect_field "the seeded account really is an admin" role ADMIN
check_anon "who am i, no token -> 401" 401 $BASE/auth/me

A="dev$RANDOM"
# 202, not 201: a row was created, but what the person asked for - an account they can use -
# does not exist yet.
check_anon "register -> 202 accepted" 202 -X POST $BASE/auth/register -H "$J" -d "{\"username\":\"$A\",\"email\":\"$A@dev.ma\",\"password\":\"password123\"}"
expect_field "the new account is PENDING" status PENDING
# The hole M5 closed, proven rather than asserted: before this, that role field was honoured and
# anybody on the internet could make themselves an administrator with one request.
check_anon "a role in the body is ignored" 202 -X POST $BASE/auth/register -H "$J" -d "{\"username\":\"sneaky$A\",\"email\":\"sneaky$A@dev.ma\",\"password\":\"password123\",\"role\":\"ADMIN\"}"
check_anon "an unapproved account cannot log in -> 403" 403 -X POST $BASE/auth/login -H "$J" -d "{\"username\":\"$A\",\"password\":\"password123\"}"
check_anon "register duplicate username -> 422" 422 -X POST $BASE/auth/register -H "$J" -d "{\"username\":\"$A\",\"email\":\"other$A@dev.ma\",\"password\":\"password123\"}"
check_anon "register short password -> 400" 400 -X POST $BASE/auth/register -H "$J" -d '{"username":"shorty","email":"s@dev.ma","password":"abc"}'
check_anon "register bad email -> 400" 400 -X POST $BASE/auth/register -H "$J" -d '{"username":"bademail","email":"nope","password":"password123"}'
check_anon "wrong password -> 401" 401 -X POST $BASE/auth/login -H "$J" -d '{"username":"admin","password":"wrongpassword"}'
check_anon "unknown username -> 401" 401 -X POST $BASE/auth/login -H "$J" -d '{"username":"nobody","password":"password123"}'

echo "########## APPROVAL ##########"
check "the pending queue" 200 "$BASE/admin/accounts?status=PENDING"
ACC_ID=$(id_where username "$A")
check "the whole list, enriched with emails from work-service" 200 $BASE/admin/accounts
check "approve as a developer" 200 -X POST $BASE/admin/accounts/$ACC_ID/approve -H "$J" -d '{"role":"DEVELOPER"}'
expect_field "approving activates the account" status ACTIVE
check "approving twice -> 422" 422 -X POST $BASE/admin/accounts/$ACC_ID/approve -H "$J" -d '{"role":"ADMIN"}'
check "approve an account that does not exist -> 404" 404 -X POST $BASE/admin/accounts/999999/approve -H "$J" -d '{"role":"DEVELOPER"}'
check "approve with no role -> 400" 400 -X POST $BASE/admin/accounts/$ACC_ID/approve -H "$J" -d '{}'
check_anon "the approved account can now log in" 200 -X POST $BASE/auth/login -H "$J" -d "{\"username\":\"$A\",\"password\":\"password123\"}"
DEV_TOKEN=$(token_of); DEV_ID=$(uid_of)

echo "########## USERS ##########"
# POST /users is service-only: registration is the one way a person is created, so a profile can
# never exist without a login behind it.
check "create user directly -> 403" 403 -X POST $BASE/users -H "$J" -d '{"username":"sneaky","email":"s@dev.ma","role":"ADMIN"}'
check "list users" 200 $BASE/users
check "get user" 200 $BASE/users/$DEV_ID
# The role is no longer changeable here, and this proves it. Before M5 this endpoint wrote
# app_user.role while tokens went on being minted from account.role in the other database, so a
# promoted person carried their old role for as long as the account existed and nothing said so.
check "update user" 200 -X PUT $BASE/users/$DEV_ID -H "$J" -d "{\"username\":\"$A\",\"email\":\"$A@dev.ma\",\"role\":\"ADMIN\"}"
expect_field "the role in the body was ignored" role DEVELOPER
check "get missing user -> 404" 404 $BASE/users/999999

echo "########## ROLE RULES ##########"
# The role claim is enforced at the service, not merely carried around.
SAVED=$TOKEN; TOKEN=$DEV_TOKEN
check "developer cannot deactivate -> 403" 403 -X DELETE $BASE/users/$DEV_ID
check "developer cannot read the user list -> 403" 403 $BASE/users
check "developer cannot see the approval queue -> 403" 403 $BASE/admin/accounts
check "developer cannot approve anybody -> 403" 403 -X POST $BASE/admin/accounts/$ACC_ID/approve -H "$J" -d '{"role":"ADMIN"}'
# A DEVELOPER joins projects; they do not start them. This is the only thing MANAGER means.
check "developer cannot create a project -> 403" 403 -X POST $BASE/projects -H "$J" -d '{"key":"NOPE","name":"not allowed"}'
TOKEN=$SAVED

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

echo "########## MEMBERSHIP ##########"
# Whoever creates a project runs it. Nobody appoints the first project manager, because there is
# nobody on the project yet to do the appointing.
check "the creator is on the project" 200 $BASE/projects/$PID/members
expect_field_in_list "the creator is its project manager" role PROJECT_MANAGER
check "the join code, for the project manager only" 200 $BASE/projects/$PID/join-code
JOIN_CODE=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin)['joinCode'])")

SAVED=$TOKEN; TOKEN=$DEV_TOKEN
# The scoping, proven the only way that counts: not by an absent row in a list, but by asking
# for the thing directly, by id, with a valid token belonging to somebody else.
check "a non-member cannot read the project -> 403" 403 $BASE/projects/$PID
check "a non-member cannot read its members -> 403" 403 $BASE/projects/$PID/members
check "a non-member cannot see the join code -> 403" 403 $BASE/projects/$PID/join-code
check "a non-member cannot file an issue in it -> 403" 403 -X POST $BASE/issues -H "$J" -d "{\"projectId\":$PID,\"title\":\"not mine\",\"type\":\"BUG\",\"priority\":\"LOW\"}"
check "the project is not in their list" 200 $BASE/projects
expect_absent_from_list "the project is not in their list" id "$PID"

# Joining: the code buys the right to ask, not entry. A code sent by mail gets forwarded.
check "a wrong code finds nothing -> 404" 404 -X POST $BASE/projects/lookup -H "$J" -d '{"joinCode":"WRONGCODE123"}'
check "a valid code names the project" 200 -X POST $BASE/projects/lookup -H "$J" -d "{\"joinCode\":\"$JOIN_CODE\"}"
expect_field "the lookup found the right project" id "$PID"
check "asking to join" 201 -X POST $BASE/projects/$PID/join-requests -H "$J" -d "{\"joinCode\":\"$JOIN_CODE\"}"
expect_field "the request is pending, not accepted" status PENDING
check "asking twice -> 422" 422 -X POST $BASE/projects/$PID/join-requests -H "$J" -d "{\"joinCode\":\"$JOIN_CODE\"}"
check "still not a member until somebody says yes -> 403" 403 $BASE/projects/$PID
check "a member cannot answer their own request -> 403" 403 $BASE/projects/$PID/join-requests
TOKEN=$SAVED

check "the project manager sees the request" 200 $BASE/projects/$PID/join-requests
REQ_ID=$(id_where username "$A")
check "accepting it" 200 -X POST $BASE/projects/$PID/join-requests/$REQ_ID/approve
expect_field "the request is now approved" status APPROVED
check "accepting twice -> 422" 422 -X POST $BASE/projects/$PID/join-requests/$REQ_ID/approve

SAVED=$TOKEN; TOKEN=$DEV_TOKEN
check "now they can read the project" 200 $BASE/projects/$PID
check "and it is in their list" 200 $BASE/projects
expect_present_in_list "and it is in their list" id "$PID"
# On the project, but not running it. Joining does not make you the chef.
check "a member cannot manage the project's people -> 403" 403 -X DELETE $BASE/projects/$PID/members/$ADMIN_ID
check "a member cannot rename the project -> 403" 403 -X PUT $BASE/projects/$PID -H "$J" -d "{\"key\":\"$K\",\"name\":\"mine now\"}"
TOKEN=$SAVED

# The rule no constraint can express: it is a count over the rows that would remain afterwards.
check "the only project manager cannot stand down -> 422" 422 -X PUT $BASE/projects/$PID/members/$ADMIN_ID -H "$J" -d '{"role":"MEMBER"}'
check "the only project manager cannot be removed -> 422" 422 -X DELETE $BASE/projects/$PID/members/$ADMIN_ID
check "promoting somebody else" 200 -X PUT $BASE/projects/$PID/members/$DEV_ID -H "$J" -d '{"role":"PROJECT_MANAGER"}'
check "now the first one may stand down" 200 -X PUT $BASE/projects/$PID/members/$ADMIN_ID -H "$J" -d '{"role":"MEMBER"}'
check "and be put back" 200 -X PUT $BASE/projects/$PID/members/$ADMIN_ID -H "$J" -d '{"role":"PROJECT_MANAGER"}'
check "adding somebody already on it -> 422" 422 -X POST $BASE/projects/$PID/members -H "$J" -d "{\"userId\":$DEV_ID,\"role\":\"MEMBER\"}"

# Rotating stops the next person using an old code. It does not touch anybody already here.
check "replacing the join code" 200 -X POST $BASE/projects/$PID/join-code/rotate
expect_field_changed "the code really changed" joinCode "$JOIN_CODE"
check "the old code no longer finds anything -> 404" 404 -X POST $BASE/projects/lookup -H "$J" -d "{\"joinCode\":\"$JOIN_CODE\"}"
check "and the members are all still there" 200 $BASE/projects/$PID/members

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

echo "########## CLASSIFICATION ##########"
# Creating an issue must return immediately: the classifier answers into a queue seconds later,
# and the caller never waits for it.
check "create an issue to classify" 201 -X POST $BASE/issues -H "$J" -d "{\"title\":\"Login page crashes with a 500 error\",\"description\":\"Broken in production, urgent, everyone affected.\",\"type\":\"TASK\",\"priority\":\"LOW\",\"projectId\":$PID,\"boardId\":$BID}"
CLASSIFIED_ID=$(id_of)

# Poll rather than sleep a fixed time: the queue is usually quicker than a guess would be.
CLASSIFICATION_STATUS=000
for _ in 1 2 3 4 5 6 7 8 9 10; do
  CLASSIFICATION_STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $TOKEN" $BASE/issues/$CLASSIFIED_ID/classification)
  [ "$CLASSIFICATION_STATUS" = "200" ] && break
  sleep 2
done
if [ "$CLASSIFICATION_STATUS" = "200" ]; then
  printf 'PASS  200  a suggestion arrived over the queue\n'; pass=$((pass+1))
else
  printf 'FAIL  got %s want 200  no suggestion arrived (is classification-service up?)\n' "$CLASSIFICATION_STATUS"; fail=$((fail+1))
fi

check "read the suggestion" 200 $BASE/issues/$CLASSIFIED_ID/classification
# Not pinned to a value. It reads "stub-v1" with no API key and the model's own name with one, and
# both are correct - what matters is that every suggestion says who produced it, so a bad run can
# be traced to a model later.
expect_field_set "it says which model produced it" modelVersion
# Both outcomes are the feature working. The stub is deliberately unsure and waits for a person;
# a real model that clears the 0.85 threshold applies itself, which is the whole point of having
# a threshold. Pinning this to PENDING only passed because the stub was the only classifier.
expect_field_one_of "it recorded a review outcome" reviewStatus "PENDING" "AUTO_APPLIED"

# 204 and not 404 while the classifier has not answered: the chip polls this, and 404 would mean
# the URL is wrong rather than the answer not being ready.
check "create a second issue" 201 -X POST $BASE/issues -H "$J" -d "{\"title\":\"Second\",\"type\":\"TASK\",\"priority\":\"LOW\",\"projectId\":$PID,\"boardId\":$BID}"
SECOND_ID=$(id_of)
check "classification of an unknown issue -> 404" 404 $BASE/issues/999999/classification

check "accept the suggestion" 200 -X POST $BASE/issues/$CLASSIFIED_ID/classification/accept
expect_field "accepting records it as confirmed" reviewStatus "CONFIRMED"
# What the classifier actually suggested, whichever classifier is running. Asserting fixed values
# here would be asserting one model's judgement; the behaviour being tested is that accepting
# moves the suggestion onto the issue, and that is true of any suggestion.
SUGGESTED_TYPE=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin).get('suggestedType') or '')")
SUGGESTED_PRIORITY=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin).get('suggestedPriority') or '')")
check "the issue took the suggested values" 200 $BASE/issues/$CLASSIFIED_ID
[ -n "$SUGGESTED_TYPE" ] && expect_field "the suggested type was applied" type "$SUGGESTED_TYPE"
[ -n "$SUGGESTED_PRIORITY" ] && expect_field "the suggested priority was applied" priority "$SUGGESTED_PRIORITY"

check "a decision is made once -> 422" 422 -X POST $BASE/issues/$CLASSIFIED_ID/classification/accept
check "override the other one" 200 -X POST $BASE/issues/$SECOND_ID/classification/override
expect_field "overriding records the disagreement" reviewStatus "OVERRIDDEN"

echo "########## SIMILARITY ##########"
# The issue created above ("Login page crashes with a 500 error") is already embedded. Search for
# it in words it does not share: matching "500 error" to "500 error" would prove string search
# works, not that embeddings do. This phrasing measured 0.54 against that ticket, comfortably over
# the 0.45 threshold and far above the <0.20 that unrelated tickets score.
SIM_STATUS=0
for _ in 1 2 3 4 5 6 7 8 9 10; do
  SIM=$(curl -s -G "$BASE/similar" -H "Authorization: Bearer $TOKEN" \
    --data-urlencode "text=Sign-in screen returns a server error every time" \
    --data-urlencode "projectKey=$K")
  SIM_STATUS=$(echo "$SIM" | python -c "import sys,json;print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)
  [ "$SIM_STATUS" != "0" ] && break
  sleep 2
done
if [ "$SIM_STATUS" != "0" ]; then
  printf 'PASS  --   a near-duplicate was found in different words\n'; pass=$((pass+1))
else
  printf 'FAIL  no near-duplicate found (is the vector store populated?)\n'; fail=$((fail+1))
fi

# A detector that always finds something is not a detector.
check "unrelated text finds nothing" 200 -G "$BASE/similar" --data-urlencode "text=Repaint the bicycle shed a nicer shade of green" --data-urlencode "projectKey=$K"
UNRELATED=$(echo "$LAST" | python -c "import sys,json;print(len(json.load(sys.stdin)))")
if [ "$UNRELATED" = "0" ]; then
  printf 'PASS  --   unrelated text really returns nothing\n'; pass=$((pass+1))
else
  printf 'FAIL  got %s matches for unrelated text\n' "$UNRELATED"; fail=$((fail+1))
fi

# A duplicate in somebody else's project is not a duplicate.
check "another project finds nothing" 200 -G "$BASE/similar" --data-urlencode "text=Sign-in screen returns a server error every time" --data-urlencode "projectKey=NOSUCHKEY"
SCOPED=$(echo "$LAST" | python -c "import sys,json;print(len(json.load(sys.stdin)))")
if [ "$SCOPED" = "0" ]; then
  printf 'PASS  --   the search is scoped to one project\n'; pass=$((pass+1))
else
  printf 'FAIL  another project returned %s matches\n' "$SCOPED"; fail=$((fail+1))
fi

check_anon "similarity needs a token -> 401" 401 -G "$BASE/similar" --data-urlencode "text=anything at all here"

# Deleting a PROJECT cascades to its issues in the database without IssueService ever running, so
# no issue.deleted is published for any of them. project.deleted exists for exactly that, and this
# case is here because the bug it fixes was invisible: the vectors simply stayed behind.
CK="CAS$RANDOM"; CK=${CK:0:6}
check "a project to delete" 201 -X POST $BASE/projects -H "$J" -d "{\"key\":\"$CK\",\"name\":\"Cascade\"}"
CPID=$(id_of)
check "its board" 200 "$BASE/boards?projectId=$CPID"
CBID=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin)[0]['id'])")
check "an issue in it" 201 -X POST $BASE/issues -H "$J" -d "{\"title\":\"Cascade test ticket about the login failing badly\",\"description\":\"Something is broken.\",\"type\":\"BUG\",\"priority\":\"HIGH\",\"projectId\":$CPID,\"boardId\":$CBID}"
# Wait for it to be embedded, so the check below is measuring the deletion and not a race.
for _ in 1 2 3 4 5 6 7 8 9 10; do
  FOUND=$(curl -s -G "$BASE/similar" -H "Authorization: Bearer $TOKEN" \
    --data-urlencode "text=Cascade test ticket about the login failing badly" \
    --data-urlencode "projectKey=$CK" \
    | python -c "import sys,json;print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)
  [ "$FOUND" != "0" ] && break
  sleep 2
done
check "delete the whole project" 204 -X DELETE $BASE/projects/$CPID
sleep 4
GONE=$(curl -s -G "$BASE/similar" -H "Authorization: Bearer $TOKEN" \
  --data-urlencode "text=Cascade test ticket about the login failing badly" \
  --data-urlencode "projectKey=$CK" \
  | python -c "import sys,json;print(len(json.load(sys.stdin)))" 2>/dev/null || echo -1)
if [ "$GONE" = "0" ]; then
  printf 'PASS  --   deleting a project takes its vectors with it\n'; pass=$((pass+1))
else
  printf 'FAIL  %s vectors survived the project being deleted\n' "$GONE"; fail=$((fail+1))
fi
# Rejected at the door rather than embedded and answered with noise.
check "a two-letter search -> 400" 400 -G "$BASE/similar" --data-urlencode "text=ab" --data-urlencode "projectKey=$K"
check "no text at all -> 400" 400 "$BASE/similar?projectKey=$K"

echo "########## DASHBOARD ##########"
check_anon "the dashboard needs a token -> 401" 401 $BASE/dashboard
check "the dashboard loads" 200 $BASE/dashboard
# Asserts the shape, not a number: the counts depend on whatever is in the database when this runs.
# A missing key would come back as the string "None" and fail loudly, which is the point.
for field in totalIssues classifiedIssues awaitingReview byType byPriority byStatus byTeam byEffort; do
  present=$(echo "$LAST" | python -c "import sys,json;print('yes' if '$field' in json.load(sys.stdin) else 'no')")
  if [ "$present" = "yes" ]; then
    printf 'PASS  --   the dashboard reports %s\n' "$field"; pass=$((pass+1))
  else
    printf 'FAIL  the dashboard is missing %s\n' "$field"; fail=$((fail+1))
  fi
done
# The issues created above are real, so this cannot be zero.
POSITIVE=$(echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin)['totalIssues'] > 0)")
if [ "$POSITIVE" = "True" ]; then
  printf 'PASS  --   it counts real issues\n'; pass=$((pass+1))
else
  printf 'FAIL  totalIssues was zero with issues in the database\n'; fail=$((fail+1))
fi

echo "########## REINDEX ##########"
SAVED=$TOKEN; TOKEN=$DEV_TOKEN
check "a developer cannot reindex -> 403" 403 -X POST $BASE/admin/issues/reindex
TOKEN=$SAVED
check "an admin can reindex" 200 -X POST $BASE/admin/issues/reindex

echo "########## THE DEAD-LETTER QUEUE ##########"
# Replaying puts real work back into the system, so it is an ADMIN action on the classifier.
SAVED=$TOKEN; TOKEN=$DEV_TOKEN
check_anon "dead letters need a token -> 401" 401 http://localhost:8083/admin/classification/dead-letters
check "a developer cannot read them -> 403" 403 http://localhost:8083/admin/classification/dead-letters
check "a developer cannot replay -> 403" 403 -X POST http://localhost:8083/admin/classification/replay
TOKEN=$SAVED
check "an admin can count them" 200 http://localhost:8083/admin/classification/dead-letters
check "an admin can replay" 200 -X POST http://localhost:8083/admin/classification/replay

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

# Left until here because a deactivated person is still a member of the project above, and the
# membership cases needed them usable. Deactivation, never deletion: issue.reporter_id is
# ON DELETE RESTRICT so the record of who raised a ticket outlives the person's access.
check "admin can deactivate" 200 -X DELETE $BASE/users/$DEV_ID
expect_field "deactivation is a flag, not a delete" active False
check "the account can be switched off too" 200 -X POST $BASE/admin/accounts/$ACC_ID/disable
check_anon "a disabled account cannot log in -> 403" 403 -X POST $BASE/auth/login -H "$J" -d "{\"username\":\"$A\",\"password\":\"password123\"}"
check "and switched back on" 200 -X POST $BASE/admin/accounts/$ACC_ID/enable
check_anon "which lets them back in" 200 -X POST $BASE/auth/login -H "$J" -d "{\"username\":\"$A\",\"password\":\"password123\"}"

echo "########## CORS, AS A BROWSER WOULD SEE IT ##########"
# curl does not enforce CORS, so every case above passed while the app was unusable in a browser:
# work-service also set Access-Control-Allow-Origin, the gateway forwarded it, and the browser
# refused the duplicate. These count headers instead of trusting the status code.
cors_header_count() {
  local name="$1"; local path="$2"; local want="$3"; shift 3
  local n
  n=$(curl -s -o /dev/null -D - "$@" -H "Origin: http://localhost:5173" "$path" \
      | grep -ci '^Access-Control-Allow-Origin:')
  if [ "$n" = "$want" ]; then
    printf 'PASS  --   %s\n' "$name"; pass=$((pass+1))
  else
    printf 'FAIL  got %s want %s  %s\n' "$n" "$want" "$name"; fail=$((fail+1))
  fi
}

AUTH_HEADER="Authorization: Bearer $TOKEN"
cors_header_count "GET /projects sends one Allow-Origin, not two" "$BASE/projects" 1 -H "$AUTH_HEADER"
cors_header_count "GET /issues sends one Allow-Origin" "$BASE/issues" 1 -H "$AUTH_HEADER"
cors_header_count "POST /auth/login sends one Allow-Origin" "$BASE/auth/login" 1 \
  -X POST -H "$J" -d "{\"username\":\"$A\",\"password\":\"password123\"}"
cors_header_count "preflight sends one Allow-Origin" "$BASE/projects" 1 \
  -X OPTIONS -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: authorization"
# A 401 is still a response the browser has to read, so it needs the header too.
cors_header_count "a 401 is still readable by the browser" "$BASE/projects" 1

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
