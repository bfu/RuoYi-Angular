<#
    sync-upstream.ps1 - sync backend code from upstream RuoYi-Vue into this repo.

    NOTE: this file is intentionally ASCII-only so it can be parsed by
    Windows PowerShell 5.1 whatever the console code page is.

    Why a custom script:
      * this repo has a squashed history (39 commits from "Initial commit"), so it
        shares NO ancestor with https://github.com/yangzongzhuan/RuoYi-Vue and
        `git pull upstream master` is impossible;
      * upstream stores its files with CRLF while this repo stores LF
        (core.autocrlf=true), so every blob sha differs - all comparisons and
        merges below are done on CRLF-normalised bytes.

    Vendor 3-way sync for every changed backend file:
        base   = last synced upstream snapshot (branch vendor/ruoyi-vue)
        ours   = local HEAD
        theirs = newest upstream commit
    Only backend paths are touched: inforstack-ng (Angular) and ruoyi-vue3
    (reference Vue frontend) are never touched.

    Usage:
        powershell -NoProfile -ExecutionPolicy Bypass -File scripts/sync-upstream.ps1
        ... -File scripts/sync-upstream.ps1 -Commit        # commit after sync
        ... -File scripts/sync-upstream.ps1 -Branch master -Upstream <url>

    Exit codes: 0 ok / 2 conflicts need manual review / 3 dirty worktree.
#>

param(
    [string]$Upstream = 'https://github.com/yangzongzhuan/RuoYi-Vue.git',
    [string]$Branch   = 'master',
    [switch]$Commit
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$baseRef = 'refs/heads/vendor/ruoyi-vue'
$newRef  = 'refs/sync/upstream-new'

# Backend only (db scripts + generator included). Frontend dirs excluded.
$syncPaths = @(
    'pom.xml',
    'sql/',
    'ruoyi-admin/',
    'ruoyi-common/',
    'ruoyi-framework/',
    'ruoyi-system/',
    'ruoyi-quartz/',
    'ruoyi-generator/'
)

function Get-Blob([string]$rev, [string]$path) {
    $sha = & git rev-parse --verify --quiet "$($rev):$path" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($sha)) { return $null }
    return $sha.Trim()
}

function Test-Binary([byte[]]$bytes) {
    $n = [Math]::Min($bytes.Length, 8000)
    for ($i = 0; $i -lt $n; $i++) { if ($bytes[$i] -eq 0) { return $true } }
    return $false
}

function ConvertTo-Lf([byte[]]$bytes) {
    $out = New-Object System.Collections.Generic.List[byte]
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        if ($bytes[$i] -eq 13 -and $i + 1 -lt $bytes.Length -and $bytes[$i + 1] -eq 10) { continue }
        $out.Add($bytes[$i])
    }
    return $out.ToArray()
}

function ConvertTo-Crlf([byte[]]$bytes) {
    $out = New-Object System.Collections.Generic.List[byte]
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        $c = $bytes[$i]
        if ($c -eq 10 -and ($i -eq 0 -or $bytes[$i - 1] -ne 13)) { $out.Add(13) }
        $out.Add($c)
    }
    return $out.ToArray()
}

# Dump a blob to disk as raw bytes, normalised to LF unless it looks binary.
function Dump-Blob([string]$sha, [string]$file) {
    & cmd /c "git cat-file blob $sha > `"$file`""
    if ($LASTEXITCODE -ne 0) { throw "git cat-file blob failed: $sha" }
    $bytes = [System.IO.File]::ReadAllBytes($file)
    if (-not (Test-Binary $bytes)) { $bytes = ConvertTo-Lf $bytes }
    [System.IO.File]::WriteAllBytes($file, $bytes)
}

# Returns $null when the path is absent, otherwise @{ Hash = <content hash> }.
function Get-Entry([string]$rev, [string]$path, [string]$file) {
    $sha = Get-Blob $rev $path
    if ($null -eq $sha) { return $null }
    Dump-Blob $sha $file
    $bytes = [System.IO.File]::ReadAllBytes($file)
    return [PSCustomObject]@{
        Sha     = $sha
        Hash    = [Convert]::ToBase64String($bytes)
        Binary  = (Test-Binary $bytes)
    }
}

function Test-CrlfFile([string]$full) {
    if (-not (Test-Path $full)) { return $false }
    $bytes = [System.IO.File]::ReadAllBytes($full)
    $crlf = 0; $bareLf = 0
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        if ($bytes[$i] -eq 10) {
            if ($i -gt 0 -and $bytes[$i - 1] -eq 13) { $crlf++ } else { $bareLf++ }
        }
    }
    return ($crlf -gt 0 -and $bareLf -eq 0)
}

function Write-FileBytes([string]$path, [byte[]]$bytes) {
    $full = Join-Path $root $path
    $dir  = Split-Path -Parent $full
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    if (Test-CrlfFile $full) { $bytes = ConvertTo-Crlf $bytes }
    [System.IO.File]::WriteAllBytes($full, $bytes)
}

function Copy-TempToPath([string]$path, [string]$tmpFile) {
    Write-FileBytes $path ([System.IO.File]::ReadAllBytes($tmpFile))
}

# ---------- 1. fetch newest upstream snapshot (shallow: only latest tree) ----------
# Do NOT redirect stderr into the success stream here: with
# $ErrorActionPreference='Stop' that turns git's progress output into a
# terminating error on Windows PowerShell 5.1.
& git fetch --depth 1 --no-tags $Upstream "${Branch}:$newRef"
if ($LASTEXITCODE -ne 0) { throw "fetch failed: $Upstream ($Branch)" }

$newSha  = (& git rev-parse $newRef).Trim()
$baseSha = & git rev-parse --verify --quiet $baseRef 2>$null
if ($baseSha) { $baseSha = $baseSha.Trim() }

if (-not $baseSha) {
    & git update-ref $baseRef $newSha
    & git update-ref -d $newRef
    Write-Host "First run: recorded upstream $newSha as baseline branch vendor/ruoyi-vue. No file changed."
    exit 0
}

if ($baseSha -eq $newSha) {
    & git update-ref -d $newRef
    Write-Host "Upstream unchanged (baseline $baseSha). Nothing to sync."
    exit 0
}

# ---------- 2. diff base -> new, keep only backend paths ----------
$allChanged = @(& git diff --name-only $baseSha $newSha)
$targets = @()
foreach ($f in $allChanged) {
    if ([string]::IsNullOrWhiteSpace($f)) { continue }
    foreach ($pre in $syncPaths) {
        if ($f -eq $pre -or $f.StartsWith($pre)) { $targets += $f; break }
    }
}
$ignored = $allChanged.Count - $targets.Count

if ($targets.Count -eq 0) {
    & git update-ref $baseRef $newSha
    & git update-ref -d $newRef
    Write-Host "Upstream changed $($allChanged.Count) file(s), all outside the backend whitelist (frontend/docs). Baseline updated to $newSha."
    exit 0
}

# ---------- 3. per-file 3-way merge ----------
$dirty = @(& git status --porcelain | Where-Object { $_ -notmatch '^\?\?' })
if ($dirty.Count -gt 0) {
    Write-Host ''
    Write-Host 'ABORT: worktree has uncommitted changes. Commit or stash them first.' -ForegroundColor Yellow
    $dirty | Select-Object -First 20 | ForEach-Object { Write-Host "   $_" }
    & git update-ref -d $newRef
    exit 3
}

$applied = @(); $conflicts = @(); $skipped = @()
$tmp = Join-Path $env:TEMP ('ruoyi-sync-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null

try {
    foreach ($path in $targets) {
        $fO = Join-Path $tmp 'ours'
        $fB = Join-Path $tmp 'base'
        $fT = Join-Path $tmp 'theirs'
        $fM = Join-Path $tmp 'merged'

        $eB = Get-Entry $baseSha $path $fB   # upstream version at last sync
        $eO = Get-Entry 'HEAD'   $path $fO   # local version
        $eT = Get-Entry $newSha  $path $fT   # newest upstream version

        # deleted upstream
        if ($null -eq $eT) {
            if ($null -ne $eO -and $null -ne $eB -and $eO.Hash -eq $eB.Hash) {
                Remove-Item -Force (Join-Path $root $path)
                $applied += $path
            }
            elseif ($null -eq $eO) { $skipped += $path }
            else { $conflicts += $path }
            continue
        }

        # added upstream
        if ($null -eq $eB) {
            if ($null -eq $eO -and -not $eT.Binary) {
                Copy-TempToPath $path $fT
                $applied += $path
            }
            elseif ($null -ne $eO -and $eO.Hash -eq $eT.Hash) { $skipped += $path }
            else { $conflicts += $path }
            continue
        }

        # local untouched -> take upstream as-is
        if ($null -ne $eO -and $eO.Hash -eq $eB.Hash -and -not $eT.Binary) {
            Copy-TempToPath $path $fT
            $applied += $path
            continue
        }

        # upstream untouched (local-only change) -> keep local
        if ($eT.Hash -eq $eB.Hash) { $skipped += $path; continue }

        # both sides changed -> 3-way merge (text only)
        if ($null -eq $eO -or $eO.Binary -or $eB.Binary -or $eT.Binary) { $conflicts += $path; continue }

        & cmd /c "git merge-file -p `"$fO`" `"$fB`" `"$fT`" > `"$fM`""
        if ($LASTEXITCODE -eq 0 -and (Test-Path $fM)) {
            Copy-TempToPath $path $fM
            $applied += $path
        }
        else { $conflicts += $path }
    }
}
finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

# ---------- 4. report ----------
Write-Host ''
Write-Host "Baseline: $baseSha -> $newSha"
if ($ignored -gt 0) { Write-Host "Ignored $ignored file(s) outside the whitelist (frontend/docs)." }
Write-Host "Applied $($applied.Count) file(s):"
$applied | ForEach-Object { Write-Host "   [ok]   $_" }
if ($skipped.Count -gt 0) {
    Write-Host "Kept local version of $($skipped.Count) file(s):"
    $skipped | ForEach-Object { Write-Host "   [keep] $_" }
}

if ($conflicts.Count -gt 0) {
    Write-Host ''
    Write-Host "CONFLICT: $($conflicts.Count) file(s) changed on both sides, need manual review (not written, baseline kept at $baseSha):" -ForegroundColor Red
    $conflicts | ForEach-Object { Write-Host "   [conflict] $_" -ForegroundColor Red }
    & git update-ref -d $newRef
    exit 2
}

& git update-ref $baseRef $newSha
& git update-ref -d $newRef

if ($Commit -and $applied.Count -gt 0) {
    & git add -A -- $applied
    & git commit -m "chore(sync): sync backend from upstream RuoYi-Vue $($newSha.Substring(0,7))"
}

Write-Host ''
Write-Host 'Sync done. (nothing pushed)'
exit 0
