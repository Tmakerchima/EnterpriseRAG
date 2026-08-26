[CmdletBinding()]
param(
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Step {
    param([string]$Name, [string]$Directory, [scriptblock]$Command)
    Write-Host "`n==> $Name" -ForegroundColor Cyan
    Push-Location (Join-Path $projectRoot $Directory)
    try {
        & $Command
        if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

Invoke-Step 'Backend tests' 'backend' { mvn -B test }

Invoke-Step 'Evaluation tests and lint' 'evaluation' {
    if (-not $SkipInstall) {
        python -m pip install -e '.[dev]'
        if ($LASTEXITCODE -ne 0) { throw 'Evaluation dependency installation failed' }
    }
    python -m pytest
    if ($LASTEXITCODE -ne 0) { throw 'Evaluation tests failed' }
    python -m ruff check src tests
}

Invoke-Step 'Synthetic evaluation contract' 'evaluation' {
    python -m enterprise_rag_eval smoke --out reports/local-smoke
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic smoke run failed' }
    python -m enterprise_rag_eval report --run reports/local-smoke --frontend-out reports/local-smoke/frontend-latest.json
}

Invoke-Step 'Frontend production build' 'frontend' {
    if (-not $SkipInstall) {
        npm ci
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed' }
    }
    npm run build
}

if (Get-Command docker -ErrorAction SilentlyContinue) {
    Invoke-Step 'Docker Compose validation' '.' { docker compose config --quiet }
} else {
    Write-Warning 'Docker is not installed; PostgreSQL integration and Compose validation were skipped.'
}

Write-Host "`nAll local checks passed." -ForegroundColor Green
