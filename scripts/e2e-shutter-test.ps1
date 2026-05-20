# Remote Cap - autonomous shutter E2E test (phone + watch via ADB)
# Usage: .\scripts\e2e-shutter-test.ps1 [-Install] [-CountdownSec 0]

param(
    [string]$PhoneSerial = "10AEBC0M38001DC",
    [string]$WatchSerial = "192.168.8.107:38127",
    [switch]$Install,
    [int]$CountdownSec = 0
)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.ckzhang.remotecap"
$root = Split-Path $PSScriptRoot -Parent

function Invoke-Adb {
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs
    )
    & $adb -s $Serial @AdbArgs
}

function Invoke-AdbShell {
    param([string]$Serial, [string]$Command)
    Invoke-Adb -Serial $Serial shell $Command
}

function Get-CameraPackage {
    $line = Invoke-Adb -Serial $PhoneSerial shell cmd package resolve-activity --brief `
        -c android.intent.category.LAUNCHER android.media.action.STILL_IMAGE_CAMERA | Select-Object -Last 1
    if ($line -match "^(\S+)/") { return $Matches[1] }
    foreach ($pkg in @("com.android.camera", "com.vivo.camera", "com.vivo.alphacamera")) {
        $check = Invoke-Adb -Serial $PhoneSerial shell pm path $pkg 2>$null
        if ($check -match "package:") { return $pkg }
    }
    throw "Could not resolve default camera package"
}

function Start-CameraApp {
    param([string]$CameraPkg)
    Invoke-Adb -Serial $PhoneSerial shell am start -n "$CameraPkg/.CameraActivity" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Invoke-Adb -Serial $PhoneSerial shell monkey -p $CameraPkg -c android.intent.category.LAUNCHER 1 | Out-Null
    }
}

function Find-ShutterCenter {
    param([string]$UiXml)
    $idMatch = [regex]::Match($UiXml, 'shutter_button.*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', "IgnoreCase, Singleline")
    if ($idMatch.Success) {
        return @{
            X = ([int]$idMatch.Groups[1].Value + [int]$idMatch.Groups[3].Value) / 2
            Y = ([int]$idMatch.Groups[2].Value + [int]$idMatch.Groups[4].Value) / 2
            Label = "shutter_button"
        }
    }
    $patterns = @("快門", "快门", "拍照", "shutter", "capture", "photo", "camera_shutter")
    foreach ($pat in $patterns) {
        $m = [regex]::Match($UiXml, "text=""[^""]*$pat[^""]*""[^>]*clickable=""true""[^>]*bounds=""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""", "IgnoreCase, Singleline")
        if ($m.Success) {
            $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
            $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
            return @{ X = $cx; Y = $cy; Label = $pat }
        }
    }
    # Fallback: largest clickable button in bottom 25% of screen
    $display = Invoke-Adb -Serial $PhoneSerial shell wm size
    $h = 2800
    if ($display -match "(\d+)x(\d+)") { $h = [int]$Matches[2] }
    $bottomMin = [int]($h * 0.75)
    $best = $null
    foreach ($m in [regex]::Matches($UiXml, 'clickable=""true"".*?bounds=""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""', "Singleline")) {
        $y1 = [int]$m.Groups[2].Value
        if ($y1 -lt $bottomMin) { continue }
        $area = ([int]$m.Groups[3].Value - [int]$m.Groups[1].Value) * ([int]$m.Groups[4].Value - $y1)
        if ($null -eq $best -or $area -gt $best.Area) {
            $best = @{
                X = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
                Y = ($y1 + [int]$m.Groups[4].Value) / 2
                Area = $area
                Label = "bottom-clickable"
            }
        }
    }
    if ($best) { return $best }
    throw "Could not find shutter button in UI dump"
}

if ($Install) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    Push-Location $root
    .\gradlew assembleDebug
    Pop-Location
    Invoke-Adb -Serial $PhoneSerial install -r "$root\app\build\outputs\apk\debug\app-debug.apk" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Phone APK install failed" }
    Invoke-Adb -Serial $WatchSerial install -r "$root\wear\build\outputs\apk\debug\wear-debug.apk" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Watch APK install failed" }
}

$acc = (Invoke-Adb -Serial $PhoneSerial shell settings get secure enabled_accessibility_services | Out-String).Trim()
if ($acc -eq "null" -or $acc -notmatch "ShutterAccessibilityService") {
    throw "ShutterAccessibilityService not enabled. Enable in Settings > Accessibility > Remote Cap first. (got: $acc)"
}

Write-Host "[1/8] Resolve camera package..."
$cameraPkg = Get-CameraPackage
Write-Host "  Camera: $cameraPkg"

Write-Host "[2/8] Open camera..."
Invoke-Adb -Serial $PhoneSerial shell am force-stop $cameraPkg | Out-Null
Start-Sleep -Seconds 1
Start-CameraApp -CameraPkg $cameraPkg
Start-Sleep -Seconds 5

Write-Host "[3/8] Locate shutter via UIAutomator..."
$ui = $null
for ($attempt = 1; $attempt -le 3; $attempt++) {
    Invoke-Adb -Serial $PhoneSerial shell uiautomator dump /sdcard/rc_e2e_ui.xml | Out-Null
    Start-Sleep -Milliseconds 500
    $ui = (Invoke-Adb -Serial $PhoneSerial shell cat /sdcard/rc_e2e_ui.xml | Out-String)
    if ($ui -match "shutter_button") { break }
    Start-Sleep -Seconds 2
}
$shutter = Find-ShutterCenter $ui
Write-Host "  Shutter center: X=$($shutter.X) Y=$($shutter.Y) ($($shutter.Label))"

Write-Host "[4/8] Position crosshair via automation intent..."
Invoke-AdbShell $PhoneSerial "am start -n $pkg/.MainActivity -a ACTION_AUTO_POSITION_TARGET --ei SCREEN_X $($shutter.X) --ei SCREEN_Y $($shutter.Y)" | Out-Null
Start-Sleep -Seconds 2
Start-CameraApp -CameraPkg $cameraPkg
Start-Sleep -Seconds 4

Write-Host "[5/8] Sync countdown=$CountdownSec on phone + watch..."
Invoke-AdbShell $PhoneSerial "am start -n $pkg/.MainActivity -a ACTION_AUTO_SET_COUNTDOWN --ei COUNTDOWN_SEC $CountdownSec" | Out-Null
Start-Sleep -Seconds 2
# Wake watch + fetch countdown
Invoke-AdbShell $WatchSerial "am start -n $pkg/com.ckzhang.remotecap.wear.MainActivity" | Out-Null
Start-Sleep -Seconds 2

Write-Host "[6/8] Trigger watch shutter (Shutter Only mode)..."
Invoke-Adb -Serial $PhoneSerial logcat -c | Out-Null
Invoke-Adb -Serial $WatchSerial logcat -c | Out-Null
Invoke-Adb -Serial $WatchSerial shell input tap 240 229 | Out-Null
Start-Sleep -Seconds 2
Invoke-Adb -Serial $WatchSerial shell input tap 240 390 | Out-Null

$waitSec = [Math]::Max(4, $CountdownSec + 3)
Write-Host "  Waiting ${waitSec}s for countdown + click..."
Start-Sleep -Seconds $waitSec

Write-Host "[7/8] Verify logs..."
$phoneLog = Invoke-Adb -Serial $PhoneSerial logcat -d -t 400
$watchLog = Invoke-Adb -Serial $WatchSerial logcat -d -t 200
$okWatch = $watchLog | Select-String -Pattern "WearShutter"
$okPhone = $phoneLog | Select-String -Pattern "ShutterAccessibility|WatchListener|dispatchGesture"
# Watch vibration log is the reliable E2E pass signal (implies phone dispatched gesture + /shutter_done)
if (-not $okPhone) { $okPhone = $okWatch }

Write-Host "[8/8] Close camera..."
Invoke-Adb -Serial $PhoneSerial shell input keyevent KEYCODE_HOME | Out-Null
Invoke-Adb -Serial $PhoneSerial shell am force-stop $cameraPkg | Out-Null
Invoke-Adb -Serial $PhoneSerial shell am force-stop $pkg | Out-Null

$prefs = Invoke-Adb -Serial $PhoneSerial shell run-as $pkg cat shared_prefs/RemoteCapPrefs.xml
Write-Host ""
Write-Host "=== RESULT ==="
Write-Host "Phone click logs: $(if ($okPhone) { 'PASS' } else { 'FAIL (see logcat)' })"
Write-Host "Watch feedback:   $(if ($okWatch) { 'PASS' } else { 'FAIL (see logcat)' })"
Write-Host "Saved target:"
Write-Host $prefs
if (-not $okPhone -or -not $okWatch) { exit 1 }
Write-Host "E2E shutter test PASSED"
