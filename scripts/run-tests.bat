@echo off
rem v9.3 本地测试入口（P2-10 审计整改）：
rem 1) DDL 先导出到 pims-server\target\baseline-schema.sql（未跟踪，失败不污染仓库）
rem 2) 测试全绿后才拷贝刷新 git 跟踪的 db\baseline-schema.sql（空库自举 --init-db 用）
cd /d %~dp0..\pims-server
if exist target\baseline-schema.sql del target\baseline-schema.sql
call mvn test -B
if errorlevel 1 (echo. & echo [FAIL] 测试未通过，禁止发版；db 基线保持不变 & exit /b 1)
if not exist target\baseline-schema.sql (echo. & echo [WARN] 本次未生成基线 DDL，跳过刷新 & exit /b 0)
copy /y target\baseline-schema.sql ..\db\baseline-schema.sql >nul
echo. & echo [OK] 全部测试通过，db\baseline-schema.sql 已刷新
