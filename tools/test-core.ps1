$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force core/build/manual,artifacts | Out-Null
$sources = Get-ChildItem core/src/main/java,core/src/test/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 17 -Xlint:all -d core/build/manual $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -ea -cp core/build/manual dev.xenoah.hud.core.CoreTests
exit $LASTEXITCODE
