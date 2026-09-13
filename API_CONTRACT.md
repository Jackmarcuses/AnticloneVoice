# API Contract (v1)

## Base URL
`https://api.anti-clonevoice.com/v1`

## Authentication
- `POST /api/v1/signup`: Create a new user profile.
    - Request: `{"username": "string", "password": "string", "email": "string"}`
    - Response: `{"message": "User created successfully"}`
- `POST /api/v1/login`: Authenticate and receive a JWT.
    - Request: `{"username": "string", "password": "string"}`
    - Response: `{"token": "JWT_TOKEN", "userId": "string"}`

## Profile
- `GET /api/v1/profile`: Get current user profile (Requires JWT).
    - Response: `{"username": "string", "email": "string", "avatarUrl": "string"}`

## Signaling (gRPC)
- `service Signaling`:
    - `rpc Connect(stream Signal) returns (stream Signal)`: Bidirectional stream for WebRTC negotiation (SDP/ICE).
    - `rpc Handshake(HandshakeRequest) returns (HandshakeResponse)`: Initial call setup.

## Contacts
- `GET /api/v1/contacts`: Get user's contact list (Requires JWT).
    - Response: `[{"userId": "string", "username": "string", "isOnline": boolean}]`
- `POST /api/v1/contacts`: Add a contact (Requires JWT).
    - Request: `{"contactId": "string"}`
    - Response: `{"message": "Contact added"}`
- `DELETE /api/v1/contacts/{contactId}`: Remove a contact (Requires JWT).
    - Response: `{"message": "Contact removed"}`

## Presence (WebSocket)
- `ws://api.anti-clonevoice.com/presence/{userId}`
    - Client connects to announce online status.
    - Server sends presence updates to friends: `{"userId": "string", "status": "online|offline"}`

## Signaling (WebSocket)
- `ws://api.anti-clonevoice.com/api/v1/call/signal/{userId}`
    - Used for WebRTC signaling (SDP and ICE candidates).
    - Message: `{"type": "string", "senderId": "string", "receiverId": "string", "data": "string"}`
    - Types: `offer`, `answer`, `candidate`, `call_request`, `call_response`.

## Voice Security SDK Interface
- `VoiceSecuritySDK.verify(audioStream: Flow<AudioBuffer>): Flow<SecurityScore>`
    - `SecurityScore`: `(confidence: Float, isClone: Boolean, timestamp: Long)`
