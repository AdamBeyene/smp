@echo off
setlocal enabledelayedexpansion

echo ========================================
echo SMPP Simulator - Docker Test Environment
echo ========================================
echo.

REM Navigate to project root (3 levels up from test_containers)
cd /d "%~dp0..\..\..\"
set PROJECT_ROOT=%CD%

echo Project Root: %PROJECT_ROOT%
echo.

REM ========================================
REM Step 1: Check if old containers exist
REM ========================================
echo Checking for existing containers...
docker ps -a --filter "name=smpp-simulator" --format "{{.Names}}" > temp_containers.txt

set CONTAINERS_EXIST=0
for /f %%i in (temp_containers.txt) do set CONTAINERS_EXIST=1
del temp_containers.txt

if %CONTAINERS_EXIST%==1 (
    echo.
    echo Found existing SMPP simulator containers.
    set /p KILL_CONTAINERS="Do you want to stop and remove them? (Y/N, default=Y): "
    if "!KILL_CONTAINERS!"=="" set KILL_CONTAINERS=Y

    if /i "!KILL_CONTAINERS!"=="Y" (
        echo.
        echo Stopping and removing old containers...
        docker-compose -f src\app_requirements\test_containers\docker-compose.yml down -v
        echo Old containers removed.
    ) else (
        echo.
        echo Keeping existing containers. They may conflict with new deployment.
    )
) else (
    echo No existing containers found.
)

echo.
echo ========================================
REM Step 2: Ask about running tests
REM ========================================
set /p RUN_TESTS="Run tests during build? (Y/N, default=N): "
if "!RUN_TESTS!"=="" set RUN_TESTS=N

if /i "!RUN_TESTS!"=="Y" (
    set MVN_SKIP_TESTS=
    echo Tests will be executed.
) else (
    set MVN_SKIP_TESTS=-DskipTests
    echo Tests will be skipped.
)

echo.
echo ========================================
REM Step 3: Build with Maven
REM ========================================
echo Building project with Maven...
echo Command: mvn clean validate compile package !MVN_SKIP_TESTS!
echo.

call mvn clean validate compile package !MVN_SKIP_TESTS!

if errorlevel 1 (
    echo.
    echo ========================================
    echo ERROR: Maven build failed!
    echo ========================================
    pause
    exit /b 1
)

echo.
echo ========================================
echo Maven build completed successfully!
echo ========================================
echo.

REM ========================================
REM Step 4: Build and start Docker containers
REM ========================================
echo Building and starting Docker containers...
echo.

cd src\app_requirements\test_containers

REM Build Docker images
echo Building Docker images...
docker-compose build

if errorlevel 1 (
    echo.
    echo ========================================
    echo ERROR: Docker build failed!
    echo ========================================
    cd "%PROJECT_ROOT%"
    pause
    exit /b 1
)

echo.
echo Starting containers...
docker-compose up -d

if errorlevel 1 (
    echo.
    echo ========================================
    echo ERROR: Failed to start containers!
    echo ========================================
    cd "%PROJECT_ROOT%"
    pause
    exit /b 1
)

cd "%PROJECT_ROOT%"

echo.
echo ========================================
echo SUCCESS! Containers are starting...
echo ========================================
echo.
echo Container 1 (simulator-1):
echo   - Web UI: http://localhost:8020
echo   - SMSC Ports: 2775, 2776, 2777
echo   - Acts as: SMSC (receives from Container 2) + ESME (sends to Container 2)
echo.
echo Container 2 (simulator-2):
echo   - Web UI: http://localhost:8021
echo   - SMSC Ports: 2778, 2779, 2780
echo   - Acts as: SMSC (receives from Container 1) + ESME (sends to Container 1)
echo.
echo ========================================
echo Waiting for containers to be healthy...
echo ========================================
echo.

REM Wait for containers to be healthy
timeout /t 5 /nobreak >nul

:HEALTH_CHECK_LOOP
set SIMULATOR1_HEALTHY=0
set SIMULATOR2_HEALTHY=0

REM Check simulator-1 health
docker inspect --format="{{.State.Health.Status}}" smpp-simulator-1 2>nul | findstr /i "healthy" >nul
if !errorlevel!==0 set SIMULATOR1_HEALTHY=1

REM Check simulator-2 health
docker inspect --format="{{.State.Health.Status}}" smpp-simulator-2 2>nul | findstr /i "healthy" >nul
if !errorlevel!==0 set SIMULATOR2_HEALTHY=1

if !SIMULATOR1_HEALTHY!==1 if !SIMULATOR2_HEALTHY!==1 (
    echo.
    echo ========================================
    echo Both containers are healthy and ready!
    echo ========================================
    goto :CONTAINERS_READY
)

echo Waiting for containers to become healthy... (Ctrl+C to stop waiting)
timeout /t 5 /nobreak >nul
goto :HEALTH_CHECK_LOOP

:CONTAINERS_READY
echo.
echo ========================================
echo Test Commands:
echo ========================================
echo.
echo View container logs:
echo   docker-compose -f src\app_requirements\test_containers\docker-compose.yml logs -f simulator-1
echo   docker-compose -f src\app_requirements\test_containers\docker-compose.yml logs -f simulator-2
echo.
echo Check container status:
echo   docker-compose -f src\app_requirements\test_containers\docker-compose.yml ps
echo.
echo Send test message from Container 1 to Container 2:
echo   curl -X POST "http://localhost:8020/sim/smpp/send" -d "id=100" -d "src=1234" -d "dst=5678" -d "text=Hello from Container 1"
echo.
echo Send test message from Container 2 to Container 1:
echo   curl -X POST "http://localhost:8021/sim/smpp/send" -d "id=200" -d "src=5678" -d "dst=1234" -d "text=Hello from Container 2"
echo.
echo View messages in Container 1:
echo   curl "http://localhost:8020/messages"
echo.
echo View messages in Container 2:
echo   curl "http://localhost:8021/messages"
echo.
echo Stop containers:
echo   docker-compose -f src\app_requirements\test_containers\docker-compose.yml down
echo.
echo ========================================

endlocal
pause
