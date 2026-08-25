# How many classification requests are parked, and put them back.
#
#   powershell -NoProfile -File scripts\check-dead-letters.ps1          # just count
#   powershell -NoProfile -File scripts\check-dead-letters.ps1 -Replay  # count, then replay
#
# A message lands in the dead-letter queue when it could not be classified: the Groq key was
# missing or wrong, the provider was down past its retries, or the ticket itself was unusable.
# The work is still worth doing once the cause is gone, which is what -Replay is for.
#
# Reads the count from the service rather than the RabbitMQ management UI on purpose: the UI's
# statistics lag by a few seconds, so it will happily show an empty queue that is not.

param([switch]$Replay)

$gateway = 'http://localhost:8090'
$classifier = 'http://localhost:8083'

$curl = "$env:SystemRoot\System32\curl.exe"
if (-not (Test-Path $curl)) { $curl = 'curl.exe' }

$bodyFile = [System.IO.Path]::Combine($env:TEMP, 'check-dead-letters.json')
[System.IO.File]::WriteAllText($bodyFile, '{"username":"moni","password":"password123"}')
$login = & $curl -s -X POST "$gateway/auth/login" -H 'Content-Type: application/json' `
    --data-binary "@$bodyFile"
[System.IO.File]::Delete($bodyFile)

$token = $null
if ($login) { try { $token = ($login | ConvertFrom-Json).token } catch { } }
if (-not $token) {
    Write-Output 'Could not log in. Is the gateway up on 8090 and auth-service on 8082?'
    exit 1
}

# Both endpoints are ADMIN-only: replaying puts real work back into the system.
$counted = & $curl -s "$classifier/admin/classification/dead-letters" -H "Authorization: Bearer $token"
$count = $null
if ($counted) { try { $count = ($counted | ConvertFrom-Json).deadLetters } catch { } }

if ($null -eq $count) {
    Write-Output "Could not read the dead-letter count. Is classification-service up on 8083?"
    Write-Output "Response was: $counted"
    exit 1
}

if ($count -eq 0) {
    Write-Output 'Nothing parked. Every ticket has been classified or is still in flight.'
    exit 0
}

Write-Output "$count classification request(s) parked in issue.created.dlq."

if (-not $Replay) {
    Write-Output ''
    Write-Output 'They are waiting, not lost. Fix the cause first — usually GROQ_API_KEY — then:'
    Write-Output '  powershell -NoProfile -File scripts\check-dead-letters.ps1 -Replay'
    exit 0
}

Write-Output 'Replaying...'
$result = & $curl -s -X POST "$classifier/admin/classification/replay" -H "Authorization: Bearer $token"
$parsed = $null
if ($result) { try { $parsed = $result | ConvertFrom-Json } catch { } }

if (-not $parsed) {
    Write-Output "Replay failed. Response was: $result"
    exit 1
}

Write-Output "  replayed  $($parsed.replayed)"
Write-Output "  remaining $($parsed.remaining)"
if ($parsed.remaining -gt 0) {
    # The service replays at most 100 at a time, so a full queue does not get dumped back onto a
    # provider that has only just recovered.
    Write-Output ''
    Write-Output 'Run it again to continue.'
}
exit 0
