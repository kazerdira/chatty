# Unlock and Build Script for Android App
# This script aggressively closes all processes that might lock the build files

Write-Host "=== Chatty Android Build - File Unlock Script ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Stop Gradle daemons
Write-Host "Step 1: Stopping Gradle daemons..." -ForegroundColor Yellow
& .\gradlew.bat --stop 2>$null
Start-Sleep -Seconds 2

# Step 2: Close VS Code Java Language Server
Write-Host "Step 2: Closing VS Code Java processes..." -ForegroundColor Yellow
Get-Process | Where-Object {
    $_.ProcessName -eq "java" -and 
    $_.Path -like "*\.vscode\extensions\redhat.java*"
} | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# Step 3: Close Android Studio Java processes  
Write-Host "Step 3: Closing Android Studio Java processes..." -ForegroundColor Yellow
Get-Process | Where-Object {
    $_.ProcessName -eq "java" -and 
    $_.Path -like "*Android Studio*"
} | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# Step 4: Kill ALL remaining Java processes
Write-Host "Step 4: Killing all remaining Java/Gradle processes..." -ForegroundColor Yellow
Get-Process | Where-Object {$_.ProcessName -match "java|gradle"} | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# Step 5: Force delete build directory
Write-Host "Step 5: Removing build directory..." -ForegroundColor Yellow
if (Test-Path "androidApp\build") {
    Remove-Item -Path "androidApp\build" -Recurse -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    
    # If still exists, try harder
    if (Test-Path "androidApp\build") {
        Write-Host "   Build directory still exists, trying harder..." -ForegroundColor Yellow
        cmd /c "rd /s /q androidApp\build" 2>$null
        Start-Sleep -Seconds 2
    }
    
    if (-not (Test-Path "androidApp\build")) {
        Write-Host "   ✓ Build directory removed successfully" -ForegroundColor Green
    } else {
        Write-Host "   ✗ Warning: Some files may still be locked" -ForegroundColor Red
    }
} else {
    Write-Host "   Build directory doesn't exist" -ForegroundColor Gray
}

Write-Host ""
Write-Host "Step 6: Starting clean build (no daemon)..." -ForegroundColor Yellow
Write-Host ""

# Build with no daemon to avoid new file locks
& .\gradlew.bat :androidApp:assembleDebug --no-daemon --no-configuration-cache

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "==================================" -ForegroundColor Green
    Write-Host "✓ BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "==================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "APK location:" -ForegroundColor Cyan
    Write-Host "  androidApp\build\outputs\apk\debug\androidApp-debug.apk"
    Write-Host ""
    Write-Host "To install on emulator/device:" -ForegroundColor Cyan
    Write-Host "  adb install androidApp\build\outputs\apk\debug\androidApp-debug.apk"
} else {
    Write-Host ""
    Write-Host "==================================" -ForegroundColor Red
    Write-Host "✗ BUILD FAILED" -ForegroundColor Red
    Write-Host "==================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "If you still see file lock errors:" -ForegroundColor Yellow
    Write-Host "1. Close Android Studio completely" -ForegroundColor Yellow
    Write-Host "2. Close VS Code" -ForegroundColor Yellow
    Write-Host "3. Restart your computer (last resort)" -ForegroundColor Yellow
    Write-Host "4. Or use Android Studio to build (recommended)" -ForegroundColor Yellow
}
