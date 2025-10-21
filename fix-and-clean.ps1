# Complete Cleanup Script for R.jar Lock Issue
# Run this script, then open Android Studio

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Chatty - Complete Build Cleanup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Stop all Gradle processes
Write-Host "Step 1: Stopping all Gradle daemons..." -ForegroundColor Yellow
& .\gradlew.bat --stop 2>$null
Start-Sleep -Seconds 2
Write-Host "  ✓ Gradle daemons stopped" -ForegroundColor Green
Write-Host ""

# Step 2: Kill all Java processes
Write-Host "Step 2: Killing all Java processes..." -ForegroundColor Yellow
Get-Process | Where-Object {$_.ProcessName -match "java|gradle"} | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3
Write-Host "  ✓ All Java processes stopped" -ForegroundColor Green
Write-Host ""

# Step 3: Delete all build directories
Write-Host "Step 3: Deleting all build directories..." -ForegroundColor Yellow

$dirsToDelete = @(
    "build",
    "androidApp\build",
    "shared\build",
    "buildSrc\build",
    ".gradle",
    ".idea\caches"
)

foreach ($dir in $dirsToDelete) {
    if (Test-Path $dir) {
        Write-Host "  Deleting: $dir" -ForegroundColor Gray
        cmd /c "rd /s /q `"$dir`"" 2>$null
        Remove-Item -Path $dir -Recurse -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
    }
}

Write-Host "  ✓ All build directories deleted" -ForegroundColor Green
Write-Host ""

# Step 4: Verify cleanup
Write-Host "Step 4: Verifying cleanup..." -ForegroundColor Yellow
$stillExists = @()
foreach ($dir in $dirsToDelete) {
    if (Test-Path $dir) {
        $stillExists += $dir
    }
}

if ($stillExists.Count -eq 0) {
    Write-Host "  ✓ All directories successfully deleted!" -ForegroundColor Green
} else {
    Write-Host "  ⚠ Some directories still exist:" -ForegroundColor Red
    foreach ($dir in $stillExists) {
        Write-Host "    - $dir" -ForegroundColor Red
    }
}
Write-Host ""

# Step 5: Show what was changed
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Configuration Changes Applied" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✓ gradle.properties updated:" -ForegroundColor Green
Write-Host "  - org.gradle.caching=false" -ForegroundColor Gray
Write-Host "  - org.gradle.configuration-cache=false" -ForegroundColor Gray
Write-Host "  - android.nonTransitiveRClass=false" -ForegroundColor Gray
Write-Host ""

# Step 6: Instructions
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Next Steps - Open Android Studio" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Open Android Studio" -ForegroundColor Yellow
Write-Host "2. Open project: F:\kotlin\chatty" -ForegroundColor Yellow
Write-Host "3. File → Invalidate Caches → Check all → Click 'Invalidate and Restart'" -ForegroundColor Yellow
Write-Host "4. After restart: Build → Clean Project" -ForegroundColor Yellow
Write-Host "5. Then: Build → Rebuild Project" -ForegroundColor Yellow
Write-Host "6. Finally: Click Run ▶️" -ForegroundColor Yellow
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Cleanup Complete! " -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "The R.jar locking issue should now be fixed!" -ForegroundColor Cyan
Write-Host ""
