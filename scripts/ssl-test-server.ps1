<#
.SYNOPSIS
    Stands up a throwaway PostgreSQL with TLS enabled, for the SSL and channel binding tests.

.DESCRIPTION
    The TLS-gated tests need a server with `ssl = on` and a certificate to bind to, which the
    everyday development database on 5432 generally is not. This puts one on a port of its own,
    entirely inside `.ssl-test/` at the repository root, so nothing about an existing install is
    touched and `remove` really does remove all of it.

    It is meant to be run for as long as the tests take and then thrown away - `remove` when you
    are done, so there is no second server sitting in the background.

    On Linux and macOS the same server is easier to get from Docker; the recipe CI uses is in
    `.github/workflows/tests.yml`, job `test-ssl`.

.PARAMETER Action
    start   Create the instance if needed, then start it and report how to run the tests.
    stop    Stop the server, keeping the data directory and certificates for next time.
    remove  Stop the server and delete `.ssl-test/` outright.
    status  Report whether the instance exists and whether it is listening.

.PARAMETER Port
    Port to listen on. Defaults to 5433, to stay clear of an ordinary install on 5432.

.PARAMETER Password
    Password for the `postgres` role. Defaults to the one the tests use.

.EXAMPLE
    .\scripts\ssl-test-server.ps1 start

.EXAMPLE
    .\scripts\ssl-test-server.ps1 remove
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'remove', 'status')]
    [string]$Action = 'start',

    [int]$Port = 5433,

    [string]$Password = '1234'
)

$ErrorActionPreference = 'Stop'

$root     = Split-Path -Parent $PSScriptRoot
$base     = Join-Path $root '.ssl-test'
$dataDir  = Join-Path $base 'data'
$certDir  = Join-Path $base 'certs'
$logFile  = Join-Path $base 'server.log'

function Find-PostgresBin {
    $onPath = Get-Command initdb -ErrorAction SilentlyContinue
    if ($onPath) { return Split-Path $onPath.Source }

    $installs = Get-ChildItem 'C:\Program Files\PostgreSQL' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^\d+$' } |
        Sort-Object { [int]$_.Name } -Descending

    foreach ($install in $installs) {
        $bin = Join-Path $install.FullName 'bin'
        if (Test-Path (Join-Path $bin 'initdb.exe')) { return $bin }
    }

    throw "No PostgreSQL installation found. Put initdb on PATH, or install PostgreSQL 18 or newer."
}

function Find-OpenSsl {
    $onPath = Get-Command openssl -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $candidates = @(
        'C:\Program Files\Git\usr\bin\openssl.exe',
        'C:\Program Files\Git\mingw64\bin\openssl.exe',
        'C:\Program Files\OpenSSL-Win64\bin\openssl.exe'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }

    throw "openssl not found. It ships with Git for Windows; put it on PATH or install OpenSSL."
}

function Test-Listening {
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $connect = $client.BeginConnect('localhost', $Port, $null, $null)
        $ok = $connect.AsyncWaitHandle.WaitOne(500)
        if ($ok) { $client.EndConnect($connect) }
        $client.Close()
        return $ok
    } catch {
        return $false
    }
}

function New-Certificates {
    $openssl = Find-OpenSsl
    New-Item -ItemType Directory -Force -Path $certDir | Out-Null

    Write-Host "Generating certificates with $openssl"

    # A CA of its own, so verify-ca and verify-full have something to verify against.
    & $openssl req -new -x509 -days 3650 -nodes `
        -out (Join-Path $certDir 'ca.crt') -keyout (Join-Path $certDir 'ca.key') `
        -subj '/CN=octavius-test-ca' 2>$null

    # CN=localhost is what verify-full checks the hostname against.
    & $openssl req -new -nodes `
        -out (Join-Path $certDir 'server.csr') -keyout (Join-Path $certDir 'server.key') `
        -subj '/CN=localhost' 2>$null
    & $openssl x509 -req -days 3650 -in (Join-Path $certDir 'server.csr') `
        -CA (Join-Path $certDir 'ca.crt') -CAkey (Join-Path $certDir 'ca.key') -CAcreateserial `
        -out (Join-Path $certDir 'server.crt') 2>$null

    # A client certificate too, so the client-auth test has one rather than skipping.
    & $openssl req -new -nodes `
        -out (Join-Path $certDir 'client.csr') -keyout (Join-Path $certDir 'client.key.tmp') `
        -subj '/CN=postgres' 2>$null
    & $openssl x509 -req -days 3650 -in (Join-Path $certDir 'client.csr') `
        -CA (Join-Path $certDir 'ca.crt') -CAkey (Join-Path $certDir 'ca.key') -CAcreateserial `
        -out (Join-Path $certDir 'client.crt') 2>$null
    # The JVM reads PKCS8 only, which is not what openssl writes by default.
    & $openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt `
        -in (Join-Path $certDir 'client.key.tmp') -out (Join-Path $certDir 'client.key') 2>$null

    Remove-Item (Join-Path $certDir 'client.key.tmp') -Force -ErrorAction SilentlyContinue

    if (-not (Test-Path (Join-Path $certDir 'server.crt'))) {
        throw "Certificate generation failed - no server.crt was produced."
    }
}

function New-Instance {
    param([string]$Bin)

    New-Item -ItemType Directory -Force -Path $base | Out-Null

    $version = (& (Join-Path $Bin 'initdb.exe') --version) -replace '[^\d.]', ''
    $major = [int]($version -split '\.')[0]
    if ($major -lt 18) {
        Write-Warning "Found PostgreSQL $version. Octavius requires 18 or newer, so most tests will fail."
    }

    New-Certificates

    $pwFile = Join-Path $base 'pwfile.txt'
    Set-Content -Path $pwFile -Value $Password -NoNewline

    Write-Host "Creating data directory at $dataDir"
    & (Join-Path $Bin 'initdb.exe') -D $dataDir -U postgres --auth=scram-sha-256 --pwfile=$pwFile -E UTF8 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "initdb failed with exit code $LASTEXITCODE." }

    Remove-Item $pwFile -Force

    # PostgreSQL wants the key beside its own files; on Windows it skips the permission check
    # that would otherwise reject a world-readable one.
    Copy-Item (Join-Path $certDir 'server.crt'), (Join-Path $certDir 'server.key'), (Join-Path $certDir 'ca.crt') `
        -Destination $dataDir

    Add-Content -Path (Join-Path $dataDir 'postgresql.conf') -Value @"

# --- added by scripts/ssl-test-server.ps1 ---
port = $Port
listen_addresses = 'localhost'
ssl = on
ssl_cert_file = 'server.crt'
ssl_key_file = 'server.key'
ssl_ca_file = 'ca.crt'
"@
}

function Start-Instance {
    param([string]$Bin)

    if (Test-Listening) {
        Write-Host "Already listening on port $Port."
        return
    }

    Write-Host "Starting server on port $Port"
    Start-Process -FilePath (Join-Path $Bin 'pg_ctl.exe') `
        -ArgumentList @('-D', $dataDir, '-l', $logFile, 'start') `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $base 'pg_ctl.out') `
        -RedirectStandardError (Join-Path $base 'pg_ctl.err')

    $ready = $false
    foreach ($attempt in 1..30) {
        Start-Sleep -Seconds 1
        & (Join-Path $Bin 'pg_isready.exe') -h localhost -p $Port -q 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    }

    if (-not $ready) {
        throw "Server did not become ready. See $logFile."
    }

    $env:PGPASSWORD = $Password
    $exists = & (Join-Path $Bin 'psql.exe') -h localhost -p $Port -U postgres -d postgres -tAc `
        "SELECT 1 FROM pg_database WHERE datname = 'octavius_test'"
    if (-not $exists) {
        & (Join-Path $Bin 'createdb.exe') -h localhost -p $Port -U postgres octavius_test
        Write-Host "Created database octavius_test."
    }
}

function Write-TestInstructions {
    Write-Host ""
    Write-Host "Ready. To run the TLS-gated tests against it:" -ForegroundColor Green
    Write-Host ""
    Write-Host "  `$env:TEST_SSL = `"true`""
    Write-Host "  `$env:SSL_ROOT_CERT = `"$(Join-Path $certDir 'ca.crt')`""
    Write-Host "  `$env:SSL_CERT = `"$(Join-Path $certDir 'client.crt')`""
    Write-Host "  `$env:SSL_KEY = `"$(Join-Path $certDir 'client.key')`""
    Write-Host "  .\gradlew.bat :driver:test --tests `"*SslIntegrationTest*`" --tests `"*ChannelBindingIntegrationTest*`""
    Write-Host ""
    Write-Host "And when you are done, so it is not left running:"
    Write-Host "  .\scripts\ssl-test-server.ps1 remove"
    Write-Host ""
}

function Stop-Instance {
    param([string]$Bin)

    if (-not (Test-Path (Join-Path $dataDir 'postmaster.pid'))) {
        Write-Host "Not running."
        return
    }

    & (Join-Path $Bin 'pg_ctl.exe') -D $dataDir -m fast stop | Out-Null
    Write-Host "Stopped."
}

$bin = Find-PostgresBin

switch ($Action) {
    'start' {
        if (-not (Test-Path $dataDir)) { New-Instance -Bin $bin }
        Start-Instance -Bin $bin
        Write-TestInstructions
    }

    'stop' {
        if (-not (Test-Path $dataDir)) { Write-Host "Nothing to stop - no instance at $base."; break }
        Stop-Instance -Bin $bin
    }

    'remove' {
        if (Test-Path $dataDir) { Stop-Instance -Bin $bin }
        if (Test-Path $base) {
            Remove-Item $base -Recurse -Force
            Write-Host "Removed $base."
        } else {
            Write-Host "Nothing to remove."
        }
    }

    'status' {
        if (-not (Test-Path $dataDir)) {
            Write-Host "No instance at $base."
        } elseif (Test-Listening) {
            Write-Host "Listening on port $Port. Data directory: $dataDir"
        } else {
            Write-Host "Instance exists at $dataDir but is not listening on port $Port."
        }
    }
}
