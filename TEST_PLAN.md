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
