# SMPP Simulator Docker Test Environment

## Quick Start

```bash
# Run from project root
cd C:\Workspace\tools\smp
src\app_requirements\test_containers\build-and-run.bat
```

## What Gets Created

### Container 1 (smpp-simulator-1)
- **Web UI**: http://localhost:8020
- **App Port**: 9001
- **SMSC Ports**: 2775, 2776, 2777 (acts as server, Container 2 connects here)
- **Config**: Uses LOCAL_Docker environment
- **IP**: 172.25.0.10

### Container 2 (smpp-simulator-2)
- **Web UI**: http://localhost:8021
- **App Port**: 9002 (mapped from internal 9001)
- **SMSC Ports**: 2778, 2779, 2780 (acts as server, Container 1 connects here)
- **Config**: Uses LOCAL_Docker2 environment
- **IP**: 172.25.0.11

## Configuration Files

### Container 1 Config Location
`src/main/resources/com/telemessage/simulators/LOCAL_Docker/`
- smpps.xml (6 connections: 3 SMSC server, 3 ESME client)
- https.xml
- conf.properties
- smpps_massmess.xml

### Container 2 Config Location
`src/main/resources/com/telemessage/simulators/LOCAL_Docker2/`
- smpps.xml (6 connections: 3 SMSC server, 3 ESME client)
- https.xml
- conf.properties
- smpps_massmess.xml

## SMPP Connection Architecture

### Container 1 → Container 2
- C1 ESME connects to C2 SMSC ports 2778, 2779, 2780
- Connection IDs: 100, 101, 102

### Container 2 → Container 1
- C2 ESME connects to C1 SMSC ports 2775, 2776, 2777
- Connection IDs: 200, 201, 202

**Total: 6 bidirectional SMPP connections**

## Testing

### Send message from Container 1 to Container 2
```bash
curl -X POST "http://localhost:8020/sim/smpp/send" -d "id=100" -d "src=1234" -d "dst=5678" -d "text=Hello from C1"
```

### Send message from Container 2 to Container 1
```bash
curl -X POST "http://localhost:8021/sim/smpp/send" -d "id=200" -d "src=5678" -d "dst=1234" -d "text=Hello from C2"
```

### View messages
```bash
curl "http://localhost:8020/messages"
curl "http://localhost:8021/messages"
```

## Management Commands

### View logs
```bash
docker-compose -f src\app_requirements\test_containers\docker-compose.yml logs -f simulator-1
docker-compose -f src\app_requirements\test_containers\docker-compose.yml logs -f simulator-2
```

### Check status
```bash
docker-compose -f src\app_requirements\test_containers\docker-compose.yml ps
```

### Stop containers
```bash
docker-compose -f src\app_requirements\test_containers\docker-compose.yml down
```

### Restart containers
```bash
docker-compose -f src\app_requirements\test_containers\docker-compose.yml restart
```

## How It Works

1. **Build Phase**: `build-and-run.bat` runs `mvn clean package` on your PC using your Maven settings.xml
2. **Docker Build**: Dockerfile copies pre-built JAR from `target/` (no Maven in container)
3. **Config Loading**:
   - Configs mounted at `/app/LOCAL_Docker` and `/app/LOCAL_Docker2`
   - Application tries classpath first, falls back to filesystem
   - SimFileManager resolves `LOCAL_Docker/smpps.xml` → `/app/LOCAL_Docker/smpps.xml`
4. **Networking**: Both containers on same bridge network, can communicate via hostnames

## Troubleshooting

### Container won't start
- Check logs: `docker logs smpp-simulator-1` or `docker logs smpp-simulator-2`
- Verify JAR was built: Check `target/smppsim-21.0.jar` exists
- Verify configs exist in `src/main/resources/com/telemessage/simulators/LOCAL_Docker/`

### Config not loading
- Confirm environment variable: `SIM_ENV_CONFIGURATIONS_ENV_CURRENT=LOCAL_Docker`
- Check volume mount in logs
- Verify files exist: `docker exec smpp-simulator-1 ls -la /app/LOCAL_Docker`

### Port conflicts
- Ensure ports 8020, 8021, 9001, 9002, 2775-2780 are available on host
- Stop conflicting services or change port mappings in docker-compose.yml

## Build Performance

- **Maven build on host**: ~2 minutes (depends on your PC)
- **Docker build**: ~10 seconds (just copies JAR, no compilation)
- **Container startup**: ~30 seconds (health check waits for Spring Boot)
