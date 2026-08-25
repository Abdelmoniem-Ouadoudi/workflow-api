# Is duplicate detection actually working?
#
#   powershell -NoProfile -File scripts\check-similarity.ps1
#
# Files one ticket, waits for it to be embedded, then searches for it in words it does not use.
# Matching "500 error" to "500 error" would only prove that string search works; the point of an
# embedding is that "Login page crashes" and "Users cannot sign in" land near each other.
#
# Prints the score for each phrasing so the threshold can be judged rather than trusted. The
# configured threshold is in classification-service's application.properties, where the numbers
# behind it are written down.

$base = 'http://localhost:8090'
$curl = "$env:SystemRoot\System32\curl.exe"
if (-not (Test-Path $curl)) { $curl = 'curl.exe' }

# Bodies go through a temp file: passing JSON inline to a native exe from PowerShell strips the
# double quotes on the way, and the server answers MALFORMED_REQUEST to a body that looked fine.
$bodyFile = [System.IO.Path]::Combine($env:TEMP, 'check-similarity.json')

# How many elements a JSON array actually has.
#
# Windows PowerShell 5.1 passes a parsed array through the pipeline as ONE object rather than
# unrolling it, so both @(...) and Measure-Object report 1 for an empty array and 1 for a
# ten-element one. That turned "no duplicates found" into "one match at 0% alike" - a wrong answer
# that looked like a threshold problem. Counting off the assigned variable is the version-proof way.
function Get-JsonCount {
    param([string]$Json)
    if (-not $Json) { return 0 }
    try { $parsed = $Json | ConvertFrom-Json } catch { return 0 }
    if ($null -eq $parsed) { return 0 }
    if ($parsed -is [array]) { return $parsed.Count }
    return 1
}

function Send-Json {
    param([string]$Method, [string]$Url, [string]$Json, [string]$Token)
    [System.IO.File]::WriteAllText($bodyFile, $Json)
    $args = @('-s', '-X', $Method, $Url, '-H', 'Content-Type: application/json',
              '--data-binary', "@$bodyFile")
    if ($Token) { $args += @('-H', "Authorization: Bearer $Token") }
    & $curl @args
}

$login = Send-Json 'POST' "$base/auth/login" '{"username":"moni","password":"password123"}'
$token = $null
if ($login) { try { $token = ($login | ConvertFrom-Json).token } catch { } }
if (-not $token) {
    Write-Output 'Could not log in. Is the gateway up on 8090 and auth-service on 8082?'
    exit 1
}

# A throwaway project, so this never disturbs real data and never matches real tickets.
$key = 'SIM' + (Get-Random -Minimum 100 -Maximum 999)
$project = Send-Json 'POST' "$base/projects" "{`"key`":`"$key`",`"name`":`"Similarity check`"}" $token
$projectId = ($project | ConvertFrom-Json).id
$boards = & $curl -s "$base/boards?projectId=$projectId" -H "Authorization: Bearer $token"
$boardId = (($boards | ConvertFrom-Json))[0].id

$reference = 'Login page crashes with a 500 error'
$issueJson = @"
{"title":"$reference",
 "description":"Users cannot sign in. The server returns 500 every time they submit the form.",
 "type":"BUG","priority":"HIGH","projectId":$projectId,"boardId":$boardId}
"@
Send-Json 'POST' "$base/issues" $issueJson $token | Out-Null

Write-Output "Filed: `"$reference`" in project $key"
Write-Output 'Waiting for it to be embedded...'

# The embedding happens on the other side of the queue, so poll rather than guess a sleep.
$indexed = $false
for ($i = 1; $i -le 12; $i++) {
    Start-Sleep -Seconds 2
    $hit = & $curl -s -G "$base/similar" -H "Authorization: Bearer $token" `
        --data-urlencode "text=$reference" --data-urlencode "projectKey=$key"
    if ((Get-JsonCount $hit) -gt 0) { $indexed = $true; break }
}

if (-not $indexed) {
    Write-Output ''
    Write-Output 'The ticket was never embedded. Things to check, in order:'
    Write-Output '  - is classification-service up on 8083?'
    Write-Output '  - powershell -NoProfile -File scripts\check-dead-letters.ps1'
    Write-Output '  - docker exec workflow-db psql -U workflow -d vectordb -c "select count(*) from issue_vector;"'
    [System.IO.File]::Delete($bodyFile)
    exit 1
}

Write-Output ''
Write-Output 'Searching in words the ticket does not use:'
Write-Output ''

# The last two are the control. A detector that finds a duplicate for everything is not a detector.
$queries = @(
    'Login page fails with 500 for all users',
    'Login screen throws a 500 when signing in',
    'Sign-in screen returns a server error every time',
    'Users report the login is broken',
    'Add CSV export to the monthly reports page',
    'Repaint the bicycle shed a nicer shade of green'
)

foreach ($q in $queries) {
    $body = & $curl -s -G "$base/similar" -H "Authorization: Bearer $token" `
        --data-urlencode "text=$q" --data-urlencode "projectKey=$key"
    $score = 'no match'
    if ((Get-JsonCount $body) -gt 0) {
        $top = @($body | ConvertFrom-Json)[0]
        $score = "$([math]::Round($top.score * 100))% alike"
    }
    Write-Output ("  {0,-52} {1}" -f $q, $score)
}

[System.IO.File]::Delete($bodyFile)
Write-Output ''
Write-Output 'The last two should read "no match". If they do not, the threshold is too low.'
Write-Output "Clean up when done:  DELETE $base/projects/$projectId"
exit 0
