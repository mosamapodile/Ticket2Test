$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Path '.build/classes' -Force | Out-Null
$files = @(Get-ChildItem -Path 'src/main/java' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -d '.build/classes' $files
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
& java -cp ".build/classes;src/main/resources" za.co.ticket2test.web.QualityStudio
