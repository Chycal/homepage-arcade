@echo off
rem 一键启动脚本 (Windows)
rem 前置: 已安装 JDK 11+ 与 MariaDB/MySQL
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
  echo [错误] 未找到 java，请先安装 JDK 11+: https://adoptium.net/
  pause
  exit /b 1
)

if not exist application-local.yml (
  echo [提示] 未找到 application-local.yml，正在从模板创建...
  copy application-local.example.yml application-local.yml >nul
  echo [必须] 请编辑 application-local.yml 填入数据库连接与管理员账号，然后重新运行本脚本
  pause
  exit /b 0
)

set JAR=
for %%f in (homepage-arcade-*.jar) do set JAR=%%f
if "%JAR%"=="" (
  echo [错误] 未找到 homepage-arcade-*.jar
  pause
  exit /b 1
)

echo 启动 %JAR% ^(http://localhost:8080^) ...
java -jar "%JAR%"
pause
