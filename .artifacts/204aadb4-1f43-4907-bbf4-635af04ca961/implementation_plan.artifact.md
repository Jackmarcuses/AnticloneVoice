# Phase 4: Robust Connectivity Implementation Plan

Ensure call connectivity works reliably across different networks (NATs, Firewalls) using TURN servers and robust WebRTC logic.

## Proposed Changes

### Android App (:AndroidApp)

#### [MODIFY] [WebRtcClient.kt](file:///C:/Users/Sohel%20Sheikh/Downloads/Anti-clone%20voice/Anticlonevoice/AndroidApp/src/main/java/com/jackmarcus/anti_clonevoice/webrtc/WebRtcClient.kt)
- Add `iceServers` configuration support for TURN servers.
- Implement `restartIce()` method and trigger it on connection failures.
- Implement stats gathering using `pc.getStats()` to log RTT and packet loss.
- Enhance `PeerConnection.Observer` to log ICE candidate types (host, srflx, relay).

#### [MODIFY] [CallViewModel.kt](file:///C:/Users/Sohel%20Sheikh/Downloads/Anti-clone%20voice/Anticlonevoice/AndroidApp/src/main/java/com/jackmarcus/anti_clonevoice/ui/call/CallViewModel.kt)
- Handle "Failed" or "Disconnected" ICE connection states by triggering retries/restarts.

### Documentation

#### [MODIFY] [ARCHITECTURE.md](file:///C:/Users/Sohel%20Sheikh/Downloads/Anti-clone%20voice/Anticlonevoice/ARCHITECTURE.md)
- Add a new section for TURN Server Configuration (coturn).
- Document UDP/TCP/TLS fallback strategy.

#### [NEW] [TEST_PLAN.md](file:///C:/Users/Sohel%20Sheikh/Downloads/Anti-clone%20voice/Anticlonevoice/TEST_PLAN.md)
- Define test cases for NAT traversal:
    - Same WiFi (Direct/STUN)
    - Different WiFi networks (STUN/TURN)
    - Mobile Data to WiFi (TURN)
    - Restricted Firewall (TURN TLS/TCP)

## Verification Plan

### Automated Tests
- Integration test for signaling state transitions during ICE restart.

### Manual Verification
- Test calling between a phone on 5G and a PC on WiFi.
- Check logs in Android Studio for "Relay" candidate usage when TURN is enabled.
- Verify audio continues after a simulated network switch (e.g., toggling WiFi off during a call).
