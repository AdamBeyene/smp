# Cloudhopper SMPP Migration Guide

**Project**: SMPP Simulator Modernization
**Target**: Cloudhopper SMPP (Fizzed Fork)
**Timeline**: 3-4 weeks
**Date**: November 17, 2025

---

## Quick Links

### Cloudhopper Resources
- **GitHub**: https://github.com/fizzed/cloudhopper-smpp
- **Maven**: `com.fizzed:ch-smpp:5.0.9` (stable)
- **Javadoc**: https://fizzed.com/oss/cloudhopper-smpp/apidocs/
- **Demos**: https://github.com/fizzed/cloudhopper-smpp/tree/master/src/demo/java
- **Tutorial**: https://juliuskrah.com/blog/2018/12/28/building-an-smpp-application-using-spring-boot/

### Current Implementation Files
- **Base**: [`SMPPConnection.java`](../../main/java/com/telemessage/simulators/smpp/SMPPConnection.java)
- **Managers**: [`ESMEConnManager.java`](../../main/java/com/telemessage/simulators/smpp/ESMEConnManager.java), [`SMSCConnManager.java`](../../main/java/com/telemessage/simulators/smpp/SMSCConnManager.java)
- **Implementations**: [`SMPPReceiver.java`](../../main/java/com/telemessage/simulators/smpp/SMPPReceiver.java), [`SMPPTransmitter.java`](../../main/java/com/telemessage/simulators/smpp/SMPPTransmitter.java), [`SMPPTransceiver.java`](../../main/java/com/telemessage/simulators/smpp/SMPPTransceiver.java)
- **Utils**: [`SimUtils.java`](../../main/java/com/telemessage/simulators/smpp/SimUtils.java)
- **Concatenation**: [`ConcatenationType.java`](../../main/java/com/telemessage/simulators/smpp/concatenation/ConcatenationType.java)
- **Config**: [`SMPPConnectionConf.java`](../../main/java/com/telemessage/simulators/smpp/conf/SMPPConnectionConf.java)
- **Cache**: [`MessagesCache.java`](../../main/java/com/telemessage/simulators/controllers/message/MessagesCache.java)

---

## Dependencies

### Add Cloudhopper
```xml
<!-- Stable version (Netty 3) -->
<dependency>
    <groupId>com.fizzed</groupId>
    <artifactId>ch-smpp</artifactId>
    <version>5.0.9</version>
</dependency>

<!-- OR Modern version (Netty 4) -->
<dependency>
    <groupId>com.fizzed</groupId>
    <artifactId>ch-smpp</artifactId>
    <version>6.0.0-netty4-beta-3</version>
</dependency>
```

### Keep Existing Charset Libraries
```xml
<!-- These remain unchanged -->
<dependency>
    <groupId>org.opensmpp</groupId>
    <artifactId>opensmpp-charset</artifactId>
    <version>3.0.1</version>
</dependency>
<dependency>
    <groupId>com.ibm.icu</groupId>
    <artifactId>icu4j</artifactId>
    <version>76.1</version>
</dependency>
<dependency>
    <groupId>net.freeutils</groupId>
    <artifactId>jcharset</artifactId>
    <version>2.1</version>
</dependency>
```

### Remove Logica SMPP
```xml
<!-- Remove this -->
<dependency>
    <groupId>com.logica</groupId>
    <artifactId>smpp</artifactId>
    <version>3.1.3</version>
</dependency>
```

---

## Feature Mapping

### 1. Connection Types

| Current | Cloudhopper | Code Reference |
|---------|-------------|----------------|
| ESME (client) | `SmppClient` + `bind()` | [`ESMEConnManager.java`](../../main/java/com/telemessage/simulators/smpp/ESMEConnManager.java) |
| SMSC (server) | `SmppServer` + `start()` | [`SMSCConnManager.java`](../../main/java/com/telemessage/simulators/smpp/SMSCConnManager.java) |
| Receiver | `SmppBindType.RECEIVER` | [`SMPPReceiver.java`](../../main/java/com/telemessage/simulators/smpp/SMPPReceiver.java) |
| Transmitter | `SmppBindType.TRANSMITTER` | [`SMPPTransmitter.java`](../../main/java/com/telemessage/simulators/smpp/SMPPTransmitter.java) |
| Transceiver | `SmppBindType.TRANSCEIVER` | [`SMPPTransceiver.java`](../../main/java/com/telemessage/simulators/smpp/SMPPTransceiver.java) |

### 2. Configuration

| Current XML | Cloudhopper API | Notes |
|-------------|-----------------|-------|
| `<host>` | `config.setHost()` | Direct |
| `<port>` | `config.setPort()` | Direct |
| `<systemId>` | `config.setSystemId()` | Direct |
| `<password>` | `config.setPassword()` | Direct |
| `<bindOption>` | `config.setType()` | Use enum |
| `<timeout>` | `config.setBindTimeout()` | Direct |
| `<threads>` | Netty executor | Different |
| `<encoding>` | Custom logic | Keep existing |
| `<concatenation>` | Custom logic | Keep existing |

**Config File**: [`smpps.xml`](../../main/resources/com/telemessage/simulators/CRND/smpps.xml)

### 3. Encoding Support

**Keep All Current Logic**:
- [`SimUtils.determineEncoding()`](../../main/java/com/telemessage/simulators/smpp/SimUtils.java#L456)
- [`SimUtils.prepareDataCodingAndEnc()`](../../main/java/com/telemessage/simulators/smpp/SimUtils.java#L196)
- [`SimUtils.getMessageTextForCaching()`](../../main/java/com/telemessage/simulators/smpp/SimUtils.java#L326)

**Supported**: GSM7, UCS2, UTF-8, ISO-8859-1/5/8, Cp1252, etc.

### 4. Concatenation

**Keep All Current Logic**:
- [`ConcatenationType.java`](../../main/java/com/telemessage/simulators/smpp/concatenation/ConcatenationType.java) - UDHI, SAR, PAYLOAD, TEXT_BASE
- [`SimUtils.extractConcatMessageContent()`](../../main/java/com/telemessage/simulators/smpp/SimUtils.java#L490)

**Adapt to Cloudhopper PDUs**

### 5. Message Caching

**No Changes**:
- [`MessagesCache.java`](../../main/java/com/telemessage/simulators/controllers/message/MessagesCache.java)
- [`MessagesObject.java`](../../main/java/com/telemessage/simulators/controllers/message/MessagesObject.java)

**Update**: Caching calls to use Cloudhopper PDUs

---

## Migration Checklist

### Phase 1: Foundation (Week 1)
- [ ] Add Cloudhopper dependency to `pom.xml`
- [ ] Create `CloudhopperSessionHandler` class
- [ ] Rewrite `ESMEConnManager` using `SmppClient`
- [ ] Rewrite `SMSCConnManager` using `SmppServer`
- [ ] Update `ConnectionManagerFactory`
- [ ] Basic send/receive test
- [ ] Unit tests for connection management

### Phase 2: Features (Week 2)
- [ ] Adapt encoding logic to Cloudhopper PDUs
- [ ] Port UDHI concatenation
- [ ] Port SAR concatenation
- [ ] Port PAYLOAD support
- [ ] Port TEXT_BASE concatenation
- [ ] Implement auto-reconnection
- [ ] Implement enquire_link monitoring
- [ ] Port optional parameters (TLVs)
- [ ] Update DR handling

### Phase 3: Integration (Week 3)
- [ ] Update `SMPPReceiver` for Cloudhopper
- [ ] Update `SMPPTransmitter` for Cloudhopper
- [ ] Update `SMPPTransceiver` for Cloudhopper
- [ ] Integrate with `MessagesCache`
- [ ] Integrate with `ErrorTracker`
- [ ] Update REST APIs
- [ ] Update web UI
- [ ] MCP metrics integration

### Phase 4: Testing (Week 4)
- [ ] Unit tests (>80% coverage)
- [ ] Integration tests
- [ ] Test all encodings
- [ ] Test all concatenation types
- [ ] Test all operators
- [ ] Performance benchmarks
- [ ] Load testing
- [ ] Stress testing

---

## Code Examples

### Client (ESME)
```java
SmppClient client = new DefaultSmppClient();
SmppSessionConfiguration config = new SmppSessionConfiguration();
config.setType(SmppBindType.TRANSCEIVER);
config.setHost("localhost");
config.setPort(2775);
config.setSystemId("tmtest");
config.setPassword("1234");

SmppSession session = client.bind(config, new MySessionHandler());
```

### Server (SMSC)
```java
SmppServerConfiguration config = new SmppServerConfiguration();
config.setPort(2775);
SmppServer server = new DefaultSmppServer(config, new MyServerHandler());
server.start();
```

### Send Message
```java
SubmitSm submit = new SubmitSm();
submit.setSourceAddress(new Address((byte)1, (byte)1, "1234"));
submit.setDestAddress(new Address((byte)1, (byte)1, "5678"));
submit.setShortMessage("Test".getBytes());
SubmitSmResp resp = session.submit(submit, 10000);
```

### Receive Message
```java
public class MySessionHandler extends DefaultSmppSessionHandler {
    @Override
    public PduResponse firePduRequestReceived(PduRequest req) {
        if (req instanceof DeliverSm) {
            DeliverSm deliver = (DeliverSm) req;
            // Process message
        }
        return req.createResponse();
    }
}
```

---

## Testing Strategy

### Unit Tests
- Connection management
- Encoding/decoding
- Concatenation assembly
- Configuration loading

### Integration Tests
- Full send/receive flow
- All concatenation types
- All encodings
- DR handling

### Performance Tests
- Throughput benchmarks
- Latency measurements
- Resource utilization
- Connection capacity

### Operator Tests
- Test with all configured operators
- Validate compatibility
- Check encoding handling
- Verify concatenation

---

## Rollback Plan

1. Keep Logica dependency during migration
2. Use feature flags per connection
3. Run parallel (Logica + Cloudhopper)
4. Quick switch back if issues
5. Tag stable version before migration

---

## Success Criteria

✅ All features working  
✅ All tests passing  
✅ All operators connecting  
✅ All encodings correct  
✅ Performance equal/better  
✅ No regressions  
✅ Documentation complete

---

**Status**: Ready for implementation  
**Next**: Begin Phase 1
