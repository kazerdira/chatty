#!/bin/bash

# ================================
# Chatty App - Quick Fix Migration Script
# ================================
# This script shows the exact files to replace
# Run from project root directory

echo "🚀 Chatty App - Fix Migration"
echo "=============================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}⚠️  IMPORTANT: Backup your project before running these commands!${NC}"
echo ""
echo "Run: git commit -m 'Before applying fixes'"
echo ""

read -p "Have you backed up your project? (y/n) " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    exit 1
fi

echo ""
echo -e "${GREEN}Phase 1: Shared Module Updates${NC}"
echo "================================"

# TokenManager
echo "1. Updating TokenManager interface..."
echo "   Replace: shared/src/commonMain/kotlin/com/chatty/data/local/TokenManager.kt"
echo "   Source:  /home/claude/shared/TokenManager.kt"

# TokenManagerImpl
echo ""
echo "2. Updating TokenManager Android implementation..."
echo "   Replace: shared/src/androidMain/kotlin/com/chatty/data/local/TokenManagerImpl.android.kt"
echo "   Source:  /home/claude/shared/TokenManagerImpl.android.kt"

# ChatApiClient
echo ""
echo "3. Updating ChatApiClient..."
echo "   Replace: shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt"
echo "   Source:  /home/claude/shared/ChatApiClient_FIXED.kt"

# UserRepository
echo ""
echo "4. Updating UserRepository interface..."
echo "   Replace: shared/src/commonMain/kotlin/com/chatty/domain/repository/UserRepository.kt"
echo "   Source:  /home/claude/shared/UserRepository_FIXED.kt"

# UserRepositoryImpl
echo ""
echo "5. Updating UserRepository implementation..."
echo "   Replace: shared/src/commonMain/kotlin/com/chatty/data/repository/UserRepositoryImpl.kt"
echo "   Source:  /home/claude/shared/UserRepositoryImpl_FIXED.kt"

# MessageRepositoryImpl
echo ""
echo "6. Updating MessageRepository implementation..."
echo "   Replace: shared/src/commonMain/kotlin/com/chatty/data/repository/MessageRepositoryImpl.kt"
echo "   Source:  /home/claude/shared/MessageRepositoryImpl_FIXED.kt"

echo ""
echo -e "${GREEN}Phase 2: Android App Updates${NC}"
echo "============================="

# AppModule
echo "7. Updating Dependency Injection..."
echo "   Replace: androidApp/src/main/kotlin/com/chatty/android/di/AppModule.kt"
echo "   Source:  /home/claude/android/AppModule_FIXED.kt"

# LoginViewModel
echo ""
echo "8. Updating LoginViewModel..."
echo "   Replace: androidApp/src/main/kotlin/com/chatty/android/ui/auth/LoginViewModel.kt"
echo "   Source:  /home/claude/android/LoginViewModel_FIXED.kt"

# ChatRoomViewModel
echo ""
echo "9. Updating ChatRoomViewModel..."
echo "   Replace: androidApp/src/main/kotlin/com/chatty/android/ui/chat/ChatRoomViewModel.kt"
echo "   Source:  /home/claude/android/ChatRoomViewModel_FIXED.kt"

# UserSearchViewModel
echo ""
echo "10. Updating UserSearchViewModel..."
echo "    Replace: androidApp/src/main/kotlin/com/chatty/android/ui/chat/UserSearchViewModel.kt"
echo "    Source:  /home/claude/android/UserSearchViewModel_FIXED.kt"

# ChatRoomScreen
echo ""
echo "11. Updating ChatRoomScreen..."
echo "    Replace: androidApp/src/main/kotlin/com/chatty/android/ui/chat/ChatRoomScreen.kt"
echo "    Source:  /home/claude/android/ChatRoomScreen_FIXED.kt"

# UserSearchScreen
echo ""
echo "12. Updating UserSearchScreen..."
echo "    Replace: androidApp/src/main/kotlin/com/chatty/android/ui/chat/UserSearchScreen.kt"
echo "    Source:  /home/claude/android/UserSearchScreen_FIXED.kt"

echo ""
echo -e "${GREEN}Phase 3: Server Updates${NC}"
echo "======================="

echo "13. Updating Server Application.kt..."
echo "    File: server/src/main/kotlin/com/chatty/server/Application.kt"
echo ""
echo "    Manual changes required:"
echo "    a) Add /users/me endpoint in userRoutes function"
echo "    b) Replace webSocketRoute function"
echo ""
echo "    See: /home/claude/server_fixes.kt for exact code"

echo ""
echo -e "${GREEN}Phase 4: Build & Test${NC}"
echo "====================="

echo ""
echo "After applying all changes, run:"
echo ""
echo "  cd [project-root]"
echo "  ./gradlew clean"
echo "  ./gradlew build"
echo ""

echo -e "${YELLOW}Testing Checklist:${NC}"
echo "  □ Start server"
echo "  □ Run Android app"  
echo "  □ Login with test user"
echo "  □ Check logs for 'WebSocket authenticated'"
echo "  □ Send message - should appear on right"
echo "  □ Search users - should wait 500ms"
echo "  □ Create chat room"
echo ""

echo -e "${GREEN}✅ Migration guide complete!${NC}"
echo ""
echo "Full documentation: /mnt/user-data/outputs/COMPLETE_FIX_GUIDE.md"
echo ""

# ================================
# QUICK COPY COMMANDS (if files are accessible)
# ================================

cat << 'EOF'

Quick Copy Commands (if using Linux/Mac):
==========================================

# Shared Module
cp /home/claude/shared/TokenManager.kt shared/src/commonMain/kotlin/com/chatty/data/local/TokenManager.kt
cp /home/claude/shared/TokenManagerImpl.android.kt shared/src/androidMain/kotlin/com/chatty/data/local/TokenManagerImpl.android.kt
cp /home/claude/shared/ChatApiClient_FIXED.kt shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt
cp /home/claude/shared/UserRepository_FIXED.kt shared/src/commonMain/kotlin/com/chatty/domain/repository/UserRepository.kt
cp /home/claude/shared/UserRepositoryImpl_FIXED.kt shared/src/commonMain/kotlin/com/chatty/data/repository/UserRepositoryImpl.kt
cp /home/claude/shared/MessageRepositoryImpl_FIXED.kt shared/src/commonMain/kotlin/com/chatty/data/repository/MessageRepositoryImpl.kt

# Android App
cp /home/claude/android/AppModule_FIXED.kt androidApp/src/main/kotlin/com/chatty/android/di/AppModule.kt
cp /home/claude/android/LoginViewModel_FIXED.kt androidApp/src/main/kotlin/com/chatty/android/ui/auth/LoginViewModel.kt
cp /home/claude/android/ChatRoomViewModel_FIXED.kt androidApp/src/main/kotlin/com/chatty/android/ui/chat/ChatRoomViewModel.kt
cp /home/claude/android/UserSearchViewModel_FIXED.kt androidApp/src/main/kotlin/com/chatty/android/ui/chat/UserSearchViewModel.kt
cp /home/claude/android/ChatRoomScreen_FIXED.kt androidApp/src/main/kotlin/com/chatty/android/ui/chat/ChatRoomScreen.kt
cp /home/claude/android/UserSearchScreen_FIXED.kt androidApp/src/main/kotlin/com/chatty/android/ui/chat/UserSearchScreen.kt

# Server (manual edits required - see server_fixes.kt)
# Edit: server/src/main/kotlin/com/chatty/server/Application.kt

EOF

echo ""
echo "Note: Adjust paths based on your project structure"
echo ""
