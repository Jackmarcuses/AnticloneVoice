# Walkthrough - Contacts & Presence Implementation

I have implemented real-time contacts management and presence tracking.

## Changes Made

### Backend (Ktor)
- **User Model**: Updated with a `contacts` list and presence state management.
- **REST Endpoints**:
    - `GET /api/v1/contacts`: Returns the user's contact list with online/offline status.
    - `POST /api/v1/contacts`: Allows adding a contact by ID.
    - `DELETE /api/v1/contacts/{id}`: Removes a contact.
- **WebSocket Presence**:
    - Implemented a WebSocket server at `ws://v1/presence/{userId}`.
    - Broadcasts "online/offline" status updates to all contacts of a user in real-time.
- **UserDatabase**: Enhanced to handle contact lists and online user tracking using `ConcurrentHashMap`.

### Android Client
- **Network Layer**:
    - `ContactsService`: Retrofit interface for contact management.
    - `PresenceManager`: Handles the WebSocket connection using OkHttp for real-time presence updates.
- **Repository**: `ContactsRepository` manages data flow for contacts and presence state.
- **UI (Compose)**:
    - **ContactsScreen**: A real-time list of contacts with status indicators (green dot for online).
    - **Add/Delete Flow**: Users can now manage their contact list directly from the app.
- **Navigation**: Wired the Contacts flow into the `MainApp` navigation.

### Documentation
- **API_CONTRACT.md**: Updated with JSON specifications for the new Contact and WebSocket presence APIs.
- **PROJECT_STATE.md**: Transitioned to Phase 2 status.

## How to Verify

### Real-Time Presence
1. Run the Backend (`./gradlew :Backend:run`).
2. Open two instances of the app (or use two different accounts on two emulators).
3. Add the second user as a contact for the first user.
4. Observe the status icon turn green when the second user opens the app and gray when they close it.

### Contact Management
1. Go to the Contacts screen.
2. Use the "Add Contact" button to add a user ID (e.g., from another account).
3. Verify the contact appears in the list.
4. Use the "Delete" action to remove the contact and verify it disappears.
