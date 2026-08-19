@echo off
chcp 65001 >nul
title 脑健康平台 - 启动中...

echo.
echo ╔══════════════════════════════════════╗
echo ║   脑健康平台 一键启动 v2.0         ║
echo ╚══════════════════════════════════════╝
echo.

cd /d "%~dp0"

:: ── 1. Docker ──────────────────────────
echo [1/4] 启动 Docker 容器...
docker-compose up -d mysql redis 2>nul
echo   等待 MySQL 就绪...
timeout /t 8 /nobreak >nul

:: ── 2. 后端服务 ────────────────────────
echo [2/4] 启动后端微服务...
start "Auth-8001"    javaw -jar "auth-service\target\auth-service-1.0.0-SNAPSHOT.jar"
start "Subject-8002" javaw -jar "subject-service\target\subject-service-1.0.0-SNAPSHOT.jar"
start "Scale-8003"   javaw -jar "scale-service\target\scale-service-1.0.0-SNAPSHOT.jar"
start "Imaging-8004" javaw -jar "imaging-service\target\imaging-service-1.0.0-SNAPSHOT.jar"
start "Genetics-8005" javaw -jar "genetics-service\target\genetics-service-1.0.0-SNAPSHOT.jar"
start "Lab-8006"     javaw -jar "lab-service\target\lab-service-1.0.0-SNAPSHOT.jar"
start "Search-8007"  javaw -jar "search-service\target\search-service-1.0.0-SNAPSHOT.jar"
start "Export-8008"  javaw -jar "export-service\target\export-service-1.0.0-SNAPSHOT.jar"
start "Audit-8009"   javaw -jar "audit-service\target\audit-service-1.0.0-SNAPSHOT.jar"
start "ADNI-8010"    javaw -jar "adni-service\target\adni-service-1.0.0-SNAPSHOT.jar"
start "Gateway-8080" javaw -jar "gateway\target\gateway-1.0.0-SNAPSHOT.jar"
echo   等待服务启动 (约30秒)...
timeout /t 30 /nobreak >nul

:: ── 3. 前端 ────────────────────────────
echo [3/4] 启动前端开发服务器...
start "Frontend-5173" cmd /c "cd /d brain-health-web && npx vite --host"
echo   等待前端就绪...
timeout /t 8 /nobreak >nul

:: ── 4. 隧道 ────────────────────────────
echo [4/4] 启动 Cloudflare 隧道...
set TUNNEL_CMD="%LOCALAPPDATA%\Microsoft\WinGet\Packages\Cloudflare.cloudflared_Microsoft.Winget.Source_8wekyb3d8bbwe\cloudflared.exe"
start "Tunnel" cmd /c "%TUNNEL_CMD% tunnel --url http://localhost:5173"

echo.
echo ╔══════════════════════════════════════╗
echo ║  ✅ 系统启动完成！                 ║
echo ║                                    ║
echo ║  本地: http://localhost:5173       ║
echo ║  隧道: 查看 Tunnel 窗口中的链接    ║
echo ║                                    ║
echo ║  登录: admin / Admin@2024          ║
echo ╚══════════════════════════════════════╝
pause
