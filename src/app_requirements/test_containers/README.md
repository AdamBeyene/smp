# SMPP Simulator Docker Test Environment

## Quick Start

```bash
# Run from project root
cd C:\Workspace\tools\smp
src\app_requirements\test_containers\build-and-run.bat
```

## What Gets Created

### SMPP Simulator Container (smpp-simulator-1)
- **Web UI**: http://localhost:8021
- **App Port**: 9002 (mapped from internal 9001)
- **SMSC Ports**: 2775-2780
- **Config**: Uses LOCAL_Docker environment
- **IP**: 172.25.0.10

## Configuration Files

### Container Config Location
`src/main/resources/com/telemessage/simulators/LOCAL_Docker/`
- smpps.xml (SMPP connections)
- https.xml (HTTP connections)
- conf.properties (General config)
- smpps_massmess.xml (Mass messaging config)

## Testing

### Send test SMPP message
```bash
curl -X POST "http://localhost:8021/sim/smpp/send" -d "id=13" -d "src=1234" -d "dst=5678" -d "text=Test Message"
```

### View messages
```bash
curl "http://localhost:8021/messages"
```

## Management Commands

### View logs
```bash
docker-compose -f src\app_requirements\test_containers\docker-compose.yml logs -f simulator-1
```

### Check status
```bash
docker-compose -f src\app_requirements\test_containers\docker-compose.yml ps
```

### Stop container
```bash
docker-compose -f src\app_requirements\test_containers\docker-compose.yml down
```

### Restart container
```bash
docker-compose -f src\app_requirements\test_containers\docker-compose.yml restart
```

## How It Works

1. **Build Phase**: `build-and-run.bat` runs `mvn clean package` on your PC using your Maven settings.xml
2. **Docker Build**: Dockerfile copies pre-built JAR from `target/` (no Maven in container)
3. **Config Loading**:
   - Configs mounted at `/app/LOCAL_Docker`
   - Application tries classpath first, falls back to filesystem
   - SimFileManager resolves `LOCAL_Docker/smpps.xml` → `/app/LOCAL_Docker/smpps.xml`
4. **Networking**: Container on bridge network with fixed IP

## Troubleshooting

### Container won't start
- Check logs: `docker logs smpp-simulator-1`
- Verify JAR was built: Check `target/smppsim-21.0.jar` exists
- Verify configs exist in `src/main/resources/com/telemessage/simulators/LOCAL_Docker/`

### Config not loading
- Confirm environment variable: `SIM_ENV_CONFIGURATIONS_ENV_CURRENT=LOCAL_Docker`
- Check volume mount in logs
- Verify files exist: `docker exec smpp-simulator-1 ls -la /app/LOCAL_Docker`

### Port conflicts
- Ensure ports 8021, 9002, 2775-2780 are available on host
- Stop conflicting services or change port mappings in docker-compose.yml

## Build Performance

- **Maven build on host**: ~2 minutes (depends on your PC)
- **Docker build**: ~10 seconds (just copies JAR, no compilation)
- **Container startup**: ~30 seconds (health check waits for Spring Boot)

## Access Points

- **Web UI**: http://localhost:8021
- **App Port**: 9002
- **SMPP Ports**: 2775, 2776, 2777, 2778, 2779, 2780
