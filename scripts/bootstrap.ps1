$ErrorActionPreference = "Stop"
$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
uv run python (Join-Path $ScriptDirectory "bootstrap.py") @args
