@echo off
rem v8.9 本地测试入口：发版前先跑（与 GitHub Actions CI 同一套）
cd /d %~dp0..\pims-server
call mvn test -B
if errorlevel 1 (echo. & echo [FAIL] 测试未通过，禁止发版 & exit /b 1)
echo. & echo [OK] 全部测试通过
