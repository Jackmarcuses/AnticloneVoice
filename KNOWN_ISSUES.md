# Known Issues

1. **P2P Connectivity**: Initial analysis suggests NAT traversal might fail in strict symmetric NAT environments without robust TURN relay configurations.
2. **Deepfake Analysis Latency**: Real-time neural processing in the `VoiceSecuritySDK` may introduce a latency buffer of 100-200ms, which needs optimization.
3. **Gradle Sync**: Recent folder restructuring requires a full Gradle sync to recognize the `AndroidApp` module correctly.
