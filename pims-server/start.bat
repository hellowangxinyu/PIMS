@echo off
rem ============================================================
rem  PIMS 芃远综合管理系统 - Windows 启动脚本
rem  适用：机房 Windows Server 服务器（64G 内存等高配环境）
rem  说明：JVM 堆无需开大，SQLite 靠操作系统页缓存加速读取，
rem        64G 内存中 4G 给 JVM 即可，其余留给 OS 缓存整个库文件
rem  用法：双击运行，或配合「任务计划程序」设置开机自启
rem ============================================================
cd /d %~dp0

if not exist logs mkdir logs
if not exist data mkdir data

set JAVA_OPTS=-Xms512m -Xmx4g ^
 -XX:+UseG1GC ^
 -XX:MaxGCPauseMillis=200 ^
 -XX:+ExitOnOutOfMemoryError ^
 -Dfile.encoding=UTF-8 ^
 -Djava.io.tmpdir=%~dp0tmp

echo [%date% %time%] PIMS 启动中... >> logs\startup.log
java %JAVA_OPTS% -jar target\pims-server-1.0.0.jar >> logs\pims.log 2>&1

echo [%date% %time%] PIMS 已退出（错误码 %errorlevel%）>> logs\startup.log
pause
