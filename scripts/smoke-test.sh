#!/usr/bin/env bash
# Exercises every endpoint of work-service and prints PASS/FAIL per case.
BASE=http://localhost:8080
pass=0; fail=0

# check <name> <expected-status> <curl args...>
check() {
  local name="$1"; local want="$2"; shift 2
  local body status
  body=$(curl -s -w '\n%{http_code}' "$@")
  status=$(echo "$body" | tail -1)
  LAST=$(echo "$body" | sed '$d')
  if [ "$status" = "$want" ]; then
    printf 'PASS  %-3s  %s\n' "$status" "$name"; pass=$((pass+1))
  else
    printf 'FAIL  got %s want %s  %s\n      %s\n' "$status" "$want" "$name" "$(echo "$LAST" | head -c 200)"; fail=$((fail+1))
  fi
}
id_of() { echo "$LAST" | python -c "import sys,json;print(json.load(sys.stdin)['id'])"; }
J="Content-Type: application/json"

echo "########## USERS ##########"
U="u$RANDOM"
check "create user" 201 -X POST $BASE/users -H "$J" -d "{\"username\":\"$U\",\"email\":\"$U@dev.ma\",\"role\":\"DEVELOPER\"}"
USER_ID=$(id_of)
check "duplicate username -> 422" 422 -X POST $BASE/users -H "$J" -d "{\"username\":\"$U\",\"email\":\"other$U@dev.ma\",\"role\":\"ADMIN\"}"
check "invalid email -> 400" 400 -X POST $BASE/users -H "$J" -d '{"username":"validname","email":"nope","role":"ADMIN"}'
check "unknown role -> 400" 400 -X POST $BASE/users -H "$J" -d '{"username":"validname","email":"a@b.ma","role":"WIZARD"}'
check "list users" 200 $BASE/users
check "get user" 200 $BASE/users/$USER_ID
check "update user" 200 -X PUT $BASE/users/$USER_ID -H "$J" -d "{\"username\":\"$U\",\"email\":\"$U@dev.ma\",\"role\":\"MANAGER\"}"
check "get missing user -> 404" 404 $BASE/users/999999
check "deactivate user" 200 -X DELETE $BASE/users/$USER_ID

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
check "unknown url -> 404" 404 $BASE/nope

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
check "create issue" 201 -X POST $BASE/issues -H "$J" -d "{\"title\":\"First\",\"type\":\"BUG\",\"priority\":\"HIGH\",\"projectId\":$PID,\"reporterId\":$USER_ID,\"boardId\":$BID,\"sprintId\":$SID}"
IID=$(id_of)
check "issue key increments" 201 -X POST $BASE/issues -H "$J" -d "{\"title\":\"Second\",\"type\":\"TASK\",\"priority\":\"CRITICAL\",\"projectId\":$PID,\"reporterId\":$USER_ID,\"boardId\":$BID}"
I2=$(id_of)
check "blank title -> 400" 400 -X POST $BASE/issues -H "$J" -d "{\"title\":\"\",\"type\":\"BUG\",\"priority\":\"LOW\",\"projectId\":$PID,\"reporterId\":$USER_ID}"
check "unknown reporter -> 422" 422 -X POST $BASE/issues -H "$J" -d "{\"title\":\"x\",\"type\":\"BUG\",\"priority\":\"LOW\",\"projectId\":$PID,\"reporterId\":999999}"
check "unknown project -> 422" 422 -X POST $BASE/issues -H "$J" -d "{\"title\":\"x\",\"type\":\"BUG\",\"priority\":\"LOW\",\"projectId\":999999,\"reporterId\":$USER_ID}"
check "get issue" 200 $BASE/issues/$IID
check "filter by project" 200 "$BASE/issues?projectId=$PID"
check "filter by status" 200 "$BASE/issues?projectId=$PID&status=TO_DO"
check "filter by priority" 200 "$BASE/issues?priority=CRITICAL"
check "text search" 200 "$BASE/issues?q=first"
check "update issue" 200 -X PUT $BASE/issues/$IID -H "$J" -d "{\"title\":\"First edited\",\"type\":\"BUG\",\"priority\":\"MEDIUM\",\"projectId\":$PID,\"reporterId\":$USER_ID,\"boardId\":$BID}"
check "assign issue" 200 -X PUT "$BASE/issues/$IID/assignee?userId=$USER_ID"
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
check "create comment" 201 -X POST $BASE/issues/$IID/comments -H "$J" -d "{\"content\":\"hello\",\"authorId\":$USER_ID}"
CID=$(id_of)
check "blank content -> 400" 400 -X POST $BASE/issues/$IID/comments -H "$J" -d "{\"content\":\"\",\"authorId\":$USER_ID}"
check "unknown author -> 422" 422 -X POST $BASE/issues/$IID/comments -H "$J" -d '{"content":"x","authorId":999999}'
check "list comments" 200 $BASE/issues/$IID/comments
check "update comment" 200 -X PUT $BASE/issues/$IID/comments/$CID -H "$J" -d "{\"content\":\"edited\",\"authorId\":$USER_ID}"
check "comment on wrong issue -> 404" 404 -X PUT $BASE/issues/$I2/comments/$CID -H "$J" -d "{\"content\":\"x\",\"authorId\":$USER_ID}"
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
check "delete project" 204 -X DELETE $BASE/projects/$PID
check "project gone -> 404" 404 $BASE/projects/$PID
check "cascade: board gone -> 404" 404 $BASE/boards/$BID

echo "########## DOCS ##########"
check "openapi json" 200 $BASE/v3/api-docs
check "swagger ui" 200 $BASE/swagger-ui/index.html
check "actuator health" 200 $BASE/actuator/health

echo
echo "=================================="
echo "PASSED: $pass   FAILED: $fail"
echo "=================================="
[ "$fail" -eq 0 ]
