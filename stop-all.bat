@echo off
title 脑健康平台 - 关闭中...
echo 正在关闭所有服务...

:: Kill Java services
taskkill /F /FI "WINDOWTITLE eq Auth-8001*" 2>nul
taskkill /F /FI "WINDOWTITLE eq Subject-8002*" 2>nul
taskkill /F /FI "WINDOWTITLE eq Scale-8003*" 2>nul
taskkill /F /FI "WINDOWTITLE eq Audit-8009*" 2>nul
taskkill /F /FI "WINDOWTITLE eq Gateway-8080*" 2>nul
taskkill /F /FI "WINDOWTITLE eq Frontend*" 2>nul
taskkill /F /FI "WINDOWTITLE eq Tunnel*" 2>nul

:: Kill cloudflared
taskkill /F /IM cloudflared.exe 2>nul

:: Kill Java fallback
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8001 :8002 :8003 :8009"') do taskkill /F /PID %%a 2>nul

echo 全部服务已关闭
pause
