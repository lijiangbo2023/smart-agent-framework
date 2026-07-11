#!/bin/bash
#
# smart-agent-framework 启动脚本
#
# 用法:
#   ./start.sh              前台启动 (local profile, 默认端口 8080)
#   ./start.sh -d           后台启动 (daemon 模式)
#   ./start.sh -p 9090      指定端口
#   ./start.sh -e prod      指定 profile
#   ./start.sh -d -p 9090   后台 + 自定义端口
#   ./start.sh -s            停止后台进程
#   ./start.sh -r            重启 (stop + start -d)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="smart-agent"
MODULE_DIR="${SCRIPT_DIR}/smart-agent-start"
JAR_NAME="smart-agent-start-1.0.0.jar"
JAR_PATH="${MODULE_DIR}/target/${JAR_NAME}"
LOG_DIR="${HOME}/${APP_NAME}/logs"
PID_FILE="${LOG_DIR}/${APP_NAME}.pid"

# ---------- 默认参数 ----------
DAEMON=false
PROFILE="local"
PORT=8080
ACTION="start"

# ---------- JVM 参数 ----------
JVM_OPTS="-server"
JVM_OPTS="${JVM_OPTS} -Xms512m -Xmx1024m"
JVM_OPTS="${JVM_OPTS} -XX:+UseG1GC"
JVM_OPTS="${JVM_OPTS} -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof"
JVM_OPTS="${JVM_OPTS} -Dfile.encoding=UTF-8"
JVM_OPTS="${JVM_OPTS} -Djava.security.egd=file:/dev/./urandom"

# ---------- 解析参数 ----------
while getopts "dp:e:srh" opt; do
    case $opt in
        d) DAEMON=true ;;
        p) PORT=$OPTARG ;;
        e) PROFILE=$OPTARG ;;
        s) ACTION="stop" ;;
        r) ACTION="restart" ;;
        h)
            echo "用法: $0 [-d] [-p port] [-e profile] [-s] [-r]"
            echo "  -d            后台运行 (daemon)"
            echo "  -p <port>     指定端口 (默认 8080)"
            echo "  -e <profile>  Spring profile (默认 local)"
            echo "  -s            停止后台进程"
            echo "  -r            重启 (stop + start -d)"
            echo "  -h            显示帮助"
            exit 0
            ;;
        *) echo "未知参数: -$OPTARG"; exit 1 ;;
    esac
done

# ---------- 检测 Java ----------
detect_java() {
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA="$JAVA_HOME/bin/java"
    elif command -v java &>/dev/null; then
        JAVA="java"
    else
        echo "[ERROR] 找不到 Java，请设置 JAVA_HOME 或安装 JDK 21+"
        exit 1
    fi

    JAVA_VERSION=$("$JAVA" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
    if [ -z "$JAVA_VERSION" ] || [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
        echo "[WARN] 当前 Java 版本可能低于 21，建议使用 JDK 21+（当前: $("$JAVA" -version 2>&1 | head -1))"
    fi
}

# ---------- 构建 ----------
build_if_needed() {
    if [ ! -f "$JAR_PATH" ]; then
        echo "[INFO] JAR 不存在，开始构建..."
        cd "$SCRIPT_DIR"
        mvn clean package -DskipTests -q
        echo "[INFO] 构建完成"
    fi
}

# ---------- 停止 ----------
stop_app() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            echo "[INFO] 停止进程 PID=$PID ..."
            kill "$PID"
            for i in $(seq 1 30); do
                if ! kill -0 "$PID" 2>/dev/null; then
                    echo "[INFO] 进程已停止"
                    rm -f "$PID_FILE"
                    return 0
                fi
                sleep 1
            done
            echo "[WARN] 进程未在 30s 内停止，强制终止..."
            kill -9 "$PID" 2>/dev/null
            rm -f "$PID_FILE"
        else
            echo "[INFO] PID=$PID 进程不存在，清理 PID 文件"
            rm -f "$PID_FILE"
        fi
    else
        echo "[INFO] 未找到 PID 文件，应用可能未在运行"
    fi
}

# ---------- 启动 ----------
start_app() {
    detect_java
    build_if_needed
    mkdir -p "$LOG_DIR"

    STARTUP_LOG="${LOG_DIR}/startup.log"

    echo "========================================"
    echo "  ${APP_NAME}"
    echo "========================================"
    echo "  Java:    $("$JAVA" -version 2>&1 | head -1)"
    echo "  Profile: ${PROFILE}"
    echo "  Port:    ${PORT}"
    echo "  PID 文件: ${PID_FILE}"
    echo "  日志目录: ${LOG_DIR}"
    echo "========================================"

    APP_OPTS="--spring.profiles.active=${PROFILE} --server.port=${PORT}"

    if [ "$DAEMON" = true ]; then
        echo "[INFO] 后台启动中..."
        nohup "$JAVA" $JVM_OPTS \
            -jar "$JAR_PATH" \
            $APP_OPTS \
            > "$STARTUP_LOG" 2>&1 &
        APP_PID=$!
        echo "$APP_PID" > "$PID_FILE"
        echo "[INFO] 启动成功，PID=$APP_PID"
        echo "[INFO] 查看日志: tail -f ${STARTUP_LOG}"
        echo "[INFO] 停止命令: $0 -s"

        sleep 3
        if kill -0 "$APP_PID" 2>/dev/null; then
            echo "[INFO] 进程运行正常"
        else
            echo "[ERROR] 进程启动失败，请查看日志: ${STARTUP_LOG}"
            exit 1
        fi
    else
        echo "[INFO] 前台启动 (Ctrl+C 停止)..."
        exec "$JAVA" $JVM_OPTS \
            -jar "$JAR_PATH" \
            $APP_OPTS
    fi
}

# ---------- 主流程 ----------
case $ACTION in
    start)
        start_app
        ;;
    stop)
        stop_app
        ;;
    restart)
        stop_app
        DAEMON=true
        start_app
        ;;
esac
