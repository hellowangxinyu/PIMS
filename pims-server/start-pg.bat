@echo off
rem ============================================================
rem  PIMS 本地开发库 - PostgreSQL 16 启动脚本
rem  安装位置：D:\pgsql16（便携版，未注册系统服务）
rem  如需开机自启（管理员 PowerShell 执行一次）：
rem    sc.exe create pims-pg16 binPath= "\"D:\pgsql16\bin\pg_ctl.exe\" runservice -D \"D:\pgsql16\data\" -l \"D:\pgsql16\data\server.log\"" start= auto obj= "NT AUTHORITY\LocalSystem"
rem  库/用户：pims / Pims@2026pg（与生产一致），认证 trust（仅本机）
rem ============================================================
"D:\pgsql16\bin\pg_ctl.exe" -D "D:\pgsql16\data" status >nul 2>&1
if %errorlevel%==0 (
    echo [PG] 已在运行
) else (
    "D:\pgsql16\bin\pg_ctl.exe" -D "D:\pgsql16\data" -l "D:\pgsql16\data\server.log" start
)
