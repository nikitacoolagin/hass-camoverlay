$ErrorActionPreference = "Stop"
$root = "C:\Users\LocalAdmin\Documents\claude\pip"
$env:JAVA_HOME = "$root\tools\jdk\jdk-17.0.19+10"
$java = "$env:JAVA_HOME\bin\java.exe"
$javac = "$env:JAVA_HOME\bin\javac.exe"
$sdk = "$root\tools\sdk"
$bt = Get-ChildItem "$sdk\build-tools" | Sort-Object Name -Descending | Select-Object -First 1 | ForEach-Object { $_.FullName }
$androidJar = "$sdk\platforms\android-34\android.jar"
$aapt2 = "$bt\aapt2.exe"
$aapt = "$bt\aapt.exe"
$d8 = "$bt\d8.bat"
$zipalign = "$bt\zipalign.exe"
$apksigner = "$bt\apksigner.bat"

Write-Host "build-tools: $bt"
Write-Host "android.jar: $androidJar"

$app = "$root\app"
$out = "$root\build-phone"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path "$out\classes" | Out-Null

# 1. compile java
$src = Get-ChildItem "$app\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $androidJar -classpath $androidJar -d "$out\classes" $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
Write-Host "javac OK"

# 2. dex
$classFiles = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --min-api 17 --lib $androidJar --output $out $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
Write-Host "d8 OK -> $out\classes.dex"

# 3a. compile resources (launcher icons + TV banner)
& $aapt2 compile --dir "$app\res" -o "$out\res.flata"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
Write-Host "aapt2 compile OK"

# 3b. link manifest + resources into base apk -- PHONE manifest, target 33
& $aapt2 link -o "$out\base.apk" -I $androidJar "$out\res.flata" --manifest "$app\AndroidManifest.phone.xml" --min-sdk-version 17 --target-sdk-version 33
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }
Write-Host "aapt2 link OK"

# 4. add classes.dex into apk
Copy-Item "$out\classes.dex" "$out\classes.dex.tmp" -Force
Push-Location $out
& $aapt add "base.apk" "classes.dex" | Out-Null
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "aapt add failed" }
Write-Host "aapt add classes.dex OK"

# 5. zipalign
& $zipalign -f 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# 6. sign (release key; generate your own with keytool, see README)
& $apksigner sign --ks "$root\camoverlay-release.keystore" --ks-pass pass:camoverlay --ks-key-alias camoverlay --key-pass pass:camoverlay --min-sdk-version 17 --out "$out\camoverlay-phone.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
Write-Host "APK signed -> $out\camoverlay-phone.apk"
Write-Host ("SIZE: " + (Get-Item "$out\camoverlay-phone.apk").Length + " bytes")
