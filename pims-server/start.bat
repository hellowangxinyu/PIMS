@echo off
rem ============================================================
rem  PIMS 芃远综合管理系统 - Windows 启动脚本
rem  适用：机房 Windows Server 服务器 / 本地开发机
rem  说明：v12.0 起数据库为 PostgreSQL。本地便携版在 D:\pgsql16（start-pg.bat 拉起）；
rem        pg_dump 需可用——本地开发把 D:\pgsql16\bin 加进 PATH，生产 PGDG 自带。
rem        JVM 堆无需开大，4G 足矣。
rem  用法：双击运行，或配合「任务计划程序」设置开机自启
rem ============================================================
cd /d %~dp0

rem 本地便携版 PG：未注册服务则拉起（已运行则秒过）；生产服务器无此目录、自动跳过
if exist "D:\pgsql16\bin\pg_ctl.exe" (
    call start-pg.bat
)

rem pg_dump 供备份服务调用
if exist "D:\pgsql16\bin" set PATH=D:\pgsql16\bin;%PATH%

if not exist logs mkdir logs

set JAVA_OPTS=-Xms512m -Xmx4g ^
 -XX:+UseG1GC ^
 -XX:MaxGCPauseMillis=200 ^
 -XX:+ExitOnOutOfMemoryError ^
 -Dfile.encoding=UTF-8 ^
 -Duser.timezone=Asia/Shanghai ^
 -Djava.io.tmpdir=%~dp0tmp

echo [%date% %time%] PIMS 启动中... >> logs\startup.log
java %JAVA_OPTS% -jar target\pims-server-1.0.0.jar >> logs\pims.log 2>&1

echo [%date% %time%] PIMS 已退出（错误码 %errorlevel%）>> logs\startup.log
pause
