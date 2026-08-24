# Counts the CORS headers a browser would receive.
#
# curl does not enforce CORS, so a duplicated Access-Control-Allow-Origin passes every ordinary
# test and still breaks every request in a browser: the browser refuses the response and fetch
# fails as if the server were down. Run this after changing CORS, a gateway route, or a security
# config.
#
#   powershell -NoProfile -File scripts\check-cors.ps1
#
# Uses curl.exe rather than Invoke-WebRequest for two Windows PowerShell 5.1 reasons: 5.1 has no
# -SkipHttpErrorCheck, so a 401 would throw instead of being measured, and its header collection
# merges repeated headers into one string, which is precisely the thing being counted here.
#
# The bash twin is scripts/check-cors.sh. Note that "bash" in PowerShell resolves to the WSL stub;
# the one that runs it is "C:\Program Files\Git\bin\bash.exe".

$base = 'http://localhost:8090'
$origin = 'http://localhost:5173'

$curl = "$env:SystemRoot\System32\curl.exe"
if (-not (Test-Path $curl)) { $curl = 'curl.exe' }

# The body goes through a temp file. Passing JSON inline to a native exe from PowerShell strips
# the double quotes on the way, and the server answers MALFORMED_REQUEST to a body that looked
# perfectly good in the script.
#
# Written and deleted through .NET rather than Set-Content / Remove-Item. This machine's TEMP is
# an 8.3 short path containing a "~", and PowerShell's file provider reads that as the
# home-directory shortcut no matter what, -LiteralPath included. [System.IO.File] takes the string
# as a string.
$bodyFile = [System.IO.Path]::Combine($env:TEMP, 'check-cors-login.json')
[System.IO.File]::WriteAllText($bodyFile, '{"username":"moni","password":"password123"}')

$loginJson = & $curl -s -X POST "$base/auth/login" `
    -H 'Content-Type: application/json' `
    --data-binary "@$bodyFile"

[System.IO.File]::Delete($bodyFile)

$token = $null
if ($loginJson) {
    try { $token = ($loginJson | ConvertFrom-Json).token } catch { }
}

if (-not $token) {
    Write-Output 'Could not log in. Is the gateway up on 8090 and auth-service on 8082?'
    Write-Output "Response was: $loginJson"
    exit 1
}

$failed = 0
$unreachable = 0

function Count-CorsHeader {
    param([string]$Label, [string]$Url, [string[]]$ExtraArgs = @())

    # -D - writes the response headers to stdout; the body goes to NUL.
    $args = @('-s', '-o', 'NUL', '-D', '-', '-H', "Origin: $origin") + $ExtraArgs + @($Url)
    $headers = & $curl @args

    if (-not $headers) {
        Write-Output ("UNREACHABLE      {0}" -f $Label)
        $script:failed = 1
        $script:unreachable = 1
        return
    }

    $count = ([string[]]$headers | Select-String -Pattern '^Access-Control-Allow-Origin:' -CaseSensitive:$false).Count

    if ($count -eq 1) {
        Write-Output ("OK    1 header   {0}" -f $Label)
    } else {
        Write-Output ("BROKEN {0} headers  {1}   <- a browser refuses this" -f $count, $Label)
        $script:failed = 1
    }
}

$auth = @('-H', "Authorization: Bearer $token")

Count-CorsHeader 'GET /projects'           "$base/projects" $auth
Count-CorsHeader 'GET /issues'             "$base/issues"   $auth
Count-CorsHeader 'GET /boards'             "$base/boards"   $auth
Count-CorsHeader 'GET /users'              "$base/users"    $auth
Count-CorsHeader 'GET /auth/me'            "$base/auth/me"  $auth
Count-CorsHeader 'preflight for /projects' "$base/projects" @(
    '-X', 'OPTIONS',
    '-H', 'Access-Control-Request-Method: GET',
    '-H', 'Access-Control-Request-Headers: authorization')
# An error response is still something the browser must be allowed to read.
Count-CorsHeader '401 with no token'       "$base/projects"

Write-Output ''
if ($failed -eq 0) {
    Write-Output 'CORS is clean: exactly one Access-Control-Allow-Origin everywhere.'
} elseif ($unreachable -eq 1) {
    Write-Output 'Something did not answer. Check the gateway on 8090 and work-service on 8081.'
} else {
    Write-Output 'Duplicated CORS header. The browser will report the server as unreachable.'
}
exit $failed
