# Connectivity Test Plan

## NAT Traversal Scenarios

| Scenario | Objective | Expected Result |
| :--- | :--- | :--- |
| **Local WiFi** | Test direct P2P connection | Call connects quickly, candidates logged as `host`. |
| **Mobile Data (4G/5G)** | Test STUN/NAT traversal | Call connects, candidates logged as `srflx`. |
| **Cross-Network** | Test different WiFi/Carrier NATs | Call connects via STUN or TURN. |
| **Restricted Network** | Test TURN Relay fallback | Simulate firewall (port blocking), verify audio relays via `relay` candidates. |

## Robustness Tests

### 1. Network Handover
- **Action**: Start a call on WiFi, then turn off WiFi to force switch to Mobile Data.
- **Expectation**: `onConnectionStateChange` detects `DISCONNECTED`, triggers `restartIce()`, and call recovers within 5-10 seconds.

### 2. Packet Loss Simulation
- **Action**: Use network conditioning tool to simulate 10% packet loss.
- **Expectation**: WebRTC jitter buffer and Forward Error Correction (FEC) maintain audio quality. Check logs for RTT/Loss stats.

### 3. Signaling Latency
- **Action**: Log time from `call_request` to `CONNECTED`.
- **Expectation**: Total setup time < 3 seconds on stable 4G.

### 4. TURN Cluster & TLS Fallback Test
- **Action**: Place an Android device behind a strict symmetrical NAT or corporate firewall blocking all UDP outbound traffic and standard ports.
- **Expectation**: WebRTC fails to establish direct P2P (`host`) or STUN (`srflx`) connections, automatically falls back to TCP/TLS `turns:turn.anticlonevoice.com:5349?transport=tcp`, logs `relay` candidate stats, and successfully connects the secure call.

### 5. Manual ICE Restart Validation
- **Action**: Mid-call, simulate a sudden IP address change or network drop on one device.
- **Expectation**: `onConnectionStateChange` handles the `DISCONNECTED` or `FAILED` state robustly, logs `Attempting ICE Restart`, sends an `ice_restart` signaling message, triggers renegotiation with `IceRestart=true`, and transparently restores call connectivity without termination.
