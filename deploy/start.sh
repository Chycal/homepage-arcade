#!/usr/bin/env bash
# 一键启动脚本 (Linux / macOS)
# 前置: 已安装 JDK 11+ 与 MariaDB/MySQL
set -e
cd "$(dirname "$0")"

if ! command -v java >/dev/null 2>&1; then
  echo "[错误] 未找到 java，请先安装 JDK 11+: https://adoptium.net/"
  exit 1
fi

if [ ! -f application-local.yml ]; then
  echo "[提示] 未找到 application-local.yml，正在从模板创建..."
  cp application-local.example.yml application-local.yml
  echo "[必须] 请编辑 application-local.yml 填入数据库连接与管理员账号，然后重新运行本脚本"
  exit 1
fi

JAR=$(ls homepage-arcade-*.jar 2>/dev/null | head -n 1)
if [ -z "$JAR" ]; then
  echo "[错误] 未找到 homepage-arcade-*.jar"
  exit 1
fi

echo "启动 $JAR (http://localhost:8080) ..."
exec java -jar "$JAR"
