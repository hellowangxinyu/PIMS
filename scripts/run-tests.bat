@echo off
rem v8.10.2 本地测试入口：先删基线（Hibernate create-target 追加写，不删会重复）再跑
cd /d %~dp0..\pims-server
if exist ..\db\baseline-schema.sql del ..\db\baseline-schema.sql
call mvn test -B
if errorlevel 1 (echo. & echo [FAIL] 测试未通过，禁止发版 & exit /b 1)
echo. & echo [OK] 全部测试通过
