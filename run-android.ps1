# Chatty Android App Runner
# This script helps you build and run the Android app

Write-Host "🚀 Chatty Android App Runner" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Check if Android SDK exists
$sdkPath = "C:\Users\boure_rr1habg\AppData\Local\Android\Sdk"
if (Test-Path $sdkPath) {
    Write-Host "✅ Android SDK found" -ForegroundColor Green
} else {
    Write-Host "❌ Android SDK not found at $sdkPath" -ForegroundColor Red
    Write-Host "   Please install Android Studio first" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Step 1: Stopping all Gradle daemons..." -ForegroundColor Yellow
.\gradlew.bat --stop | Out-Null
Start-Sleep -Seconds 2
Write-Host "✅ Gradle daemons stopped" -ForegroundColor Green

Write-Host ""
Write-Host "Step 2: Cleaning build directory..." -ForegroundColor Yellow
if (Test-Path "androidApp\build") {
    # Try to remove multiple times
    $attempts = 0
    $maxAttempts = 3
    $removed = $false
    
    while ($attempts -lt $maxAttempts -and -not $removed) {
        try {
            Remove-Item -Path "androidApp\build" -Recurse -Force -ErrorAction Stop
            $removed = $true
            Write-Host "✅ Build directory cleaned" -ForegroundColor Green
        } catch {
            $attempts++
            if ($attempts -lt $maxAttempts) {
                Write-Host "   Retry $attempts/$maxAttempts..." -ForegroundColor Yellow
                Start-Sleep -Seconds 2
            } else {
                Write-Host "⚠️  Could not clean build directory (files locked)" -ForegroundColor Yellow
                Write-Host "   This might be OK - continuing anyway..." -ForegroundColor Yellow
            }
        }
    }
} else {
    Write-Host "✅ Build directory already clean" -ForegroundColor Green
}

Write-Host ""
Write-Host "Step 3: Building Android app..." -ForegroundColor Yellow
Write-Host "   (This may take 1-2 minutes on first run)" -ForegroundColor Gray
Write-Host ""

$buildResult = & .\gradlew.bat :androidApp:assembleDebug 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "🎉 BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Your APK is ready at:" -ForegroundColor Cyan
    Write-Host "   androidApp\build\outputs\apk\debug\androidApp-debug.apk" -ForegroundColor White
    Write-Host ""
    
    # Check if device is connected
    Write-Host "Step 4: Checking for connected devices..." -ForegroundColor Yellow
    $adbPath = "$sdkPath\platform-tools\adb.exe"
    
    if (Test-Path $adbPath) {
        $devices = & $adbPath devices | Select-String -Pattern "device$"
        
        if ($devices) {
            Write-Host "✅ Device(s) found!" -ForegroundColor Green
            Write-Host ""
            Write-Host "Do you want to install the app now? (Y/N)" -ForegroundColor Cyan
            $response = Read-Host
            
            if ($response -eq 'Y' -or $response -eq 'y') {
                Write-Host ""
                Write-Host "Installing app..." -ForegroundColor Yellow
                & .\gradlew.bat :androidApp:installDebug
                
                if ($LASTEXITCODE -eq 0) {
                    Write-Host ""
                    Write-Host "🎉 APP INSTALLED!" -ForegroundColor Green
                    Write-Host "   Open 'Chatty' from your device/emulator" -ForegroundColor Cyan
                } else {
                    Write-Host ""
                    Write-Host "❌ Installation failed" -ForegroundColor Red
                }
            }
        } else {
            Write-Host "⚠️  No devices connected" -ForegroundColor Yellow
            Write-Host ""
            Write-Host "To install the app:" -ForegroundColor Cyan
            Write-Host "   1. Start an Android emulator from Android Studio, OR" -ForegroundColor White
            Write-Host "   2. Connect your Android phone with USB debugging enabled" -ForegroundColor White
            Write-Host "   3. Run this script again" -ForegroundColor White
        }
    }
    
    Write-Host ""
    Write-Host "Manual Installation:" -ForegroundColor Cyan
    Write-Host "   You can also drag the APK file to your emulator window" -ForegroundColor White
    
} else {
    Write-Host ""
    Write-Host "❌ BUILD FAILED" -ForegroundColor Red
    Write-Host ""
    Write-Host "Common solutions:" -ForegroundColor Yellow
    Write-Host "   1. Close Android Studio and VS Code completely" -ForegroundColor White
    Write-Host "   2. Run this script again" -ForegroundColor White
    Write-Host "   3. If still failing, restart your computer" -ForegroundColor White
    Write-Host ""
    Write-Host "OR: Just open the project in Android Studio and click Run ▶️" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Last 20 lines of build output:" -ForegroundColor Yellow
    $buildResult | Select-Object -Last 20
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
