@echo off
REM ============================================
REM Smart Agent Framework - Windows 启动脚本
REM ============================================
REM 前置条件:
REM   1. 复制 .env.example 为 .env 并填入真实值
REM   2. JDK 21+ 已安装，JAVA_HOME 已配置
REM   3. Maven 已安装
REM   4. MySQL 8.x 已运行
REM
REM 用法:
REM   start.bat             本地开发环境 (local profile)
REM   start.bat dev          日常环境
REM   start.bat staging      预发环境
REM   start.bat prod         生产环境
REM ============================================

setlocal enabledelayedexpansion

set PROFILE=%1
if "%PROFILE%"=="" set PROFILE=local

echo ========================================
echo   Smart Agent Framework
echo   Profile: %PROFILE%
echo ========================================

REM 检测 Java
if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME 未设置，请安装 JDK 21+
    exit /b 1
)
echo Java: %JAVA_HOME%

REM 构建（如果需要）
if not exist "smart-agent-start\target\smart-agent-start-1.0.0.jar" (
    echo [INFO] JAR 不存在，开始构建...
    call mvn clean package -DskipTests -q
    if errorlevel 1 (
        echo [ERROR] 构建失败
        exit /b 1
    )
    echo [INFO] 构建完成
)

REM 启动
echo [INFO] 启动应用 (profile=%PROFILE%)...
cd smart-agent-start
mvn spring-boot:run -Dspring-boot.run.profiles=%PROFILE%

endlocal
