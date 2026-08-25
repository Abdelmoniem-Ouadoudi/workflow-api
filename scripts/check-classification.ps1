# Is the classification pipeline alive?
#
# Creates a ticket through the gateway and polls until the suggestion comes back, printing what
# arrived and which model produced it. One command that answers the question you actually have
# after touching RabbitMQ, the classifier, or the Groq configuration.
#
#   powershell -NoProfile -File scripts\check-classification.ps1
#
# The bash twin is scripts/smoke-test.sh, which covers this and everything else. Note that "bash"
# in PowerShell resolves to the WSL stub; the one that runs it is
# "C:\Program Files\Git\bin\bash.exe".

$base = 'http://localhost:8090'
$curl = "$env:SystemRoot\System32\curl.exe"
if (-not (Test-Path $curl)) { $curl = 'curl.exe' }

# Bodies go through a temp file. Passing JSON inline to a native exe from PowerShell strips the
# double quotes on the way, and the server answers MALFORMED_REQUEST to a body that looked fine.
$bodyFile = [System.IO.Path]::Combine($env:TEMP, 'check-classification.json')

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
    Write-Output "Response was: $login"
    exit 1
}

# A throwaway project, so this never disturbs real data.
$key = 'CHK' + (Get-Random -Minimum 100 -Maximum 999)
$project = Send-Json 'POST' "$base/projects" "{`"key`":`"$key`",`"name`":`"Pipeline check`"}" $token
$projectId = ($project | ConvertFrom-Json).id
$boards = & $curl -s "$base/boards?projectId=$projectId" -H "Authorization: Bearer $token"
$boardId = (($boards | ConvertFrom-Json))[0].id

$issueJson = @"
{"title":"Checkout crashes with a 500 error",
 "description":"Production is broken, urgent, everyone is affected. It fails every time.",
 "type":"TASK","priority":"LOW","projectId":$projectId,"boardId":$boardId}
"@

$started = Get-Date
$issue = Send-Json 'POST' "$base/issues" $issueJson $token
$createdMs = [int]((Get-Date) - $started).TotalMilliseconds
$issueId = ($issue | ConvertFrom-Json).id

if (-not $issueId) {
    Write-Output "Could not create an issue. Response was: $issue"
    [System.IO.File]::Delete($bodyFile)
    exit 1
}

Write-Output "Issue $issueId created in ${createdMs}ms  <- the caller does not wait for the AI"
Write-Output 'Waiting for the suggestion...'

$found = $null
for ($i = 1; $i -le 15; $i++) {
    Start-Sleep -Seconds 2
    $body = & $curl -s "$base/issues/$issueId/classification" -H "Authorization: Bearer $token"
    if ($body) { try { $found = $body | ConvertFrom-Json } catch { } }
    if ($found) { break }
    Write-Output "  still waiting (${i}0s)..."
}

[System.IO.File]::Delete($bodyFile)

if (-not $found) {
    Write-Output ''
    Write-Output 'No suggestion after 30 seconds. Things to check, in order:'
    Write-Output '  - is classification-service up on 8083?'
    Write-Output '  - powershell -NoProfile -File scripts\check-dead-letters.ps1'
    Write-Output '  - http://localhost:15672  (workflow / workflow) - is issue.created.q draining?'
    exit 1
}

Write-Output ''
Write-Output "  model       $($found.modelVersion)"
Write-Output "  confidence  $([int]($found.confidence * 100))%"
Write-Output "  type        $($found.suggestedType)"
Write-Output "  priority    $($found.suggestedPriority)"
Write-Output "  team        $($found.suggestedTeam)"
Write-Output "  effort      $($found.effortHint)"
Write-Output "  review      $($found.reviewStatus)"
if ($found.missingInfo) {
    Write-Output "  missing     $($found.missingInfo -join '; ')"
}
Write-Output ''
if ($found.modelVersion -eq 'stub-v1') {
    Write-Output 'The pipeline works. This is the STUB: keyword rules, not a model.'
    Write-Output 'For the real thing set GROQ_API_KEY and CLASSIFICATION_PROVIDER=groq.'
} else {
    Write-Output "The pipeline works, answered by $($found.modelVersion)."
}
exit 0
