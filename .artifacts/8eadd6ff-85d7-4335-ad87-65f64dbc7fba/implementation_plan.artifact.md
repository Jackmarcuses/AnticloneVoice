# Fix Login and Signup Issues

The goal is to resolve the login issues by fixing the backend configuration and adding missing signup functionality to the Android app.

## User Review Required

> [!IMPORTANT]
> The backend uses an in-memory database (`ConcurrentHashMap`). This means all users created via Signup will be lost every time the backend server is restarted.

## Proposed Changes

### Backend

#### [MODIFY] [Application.kt](file:///C:/Users/Sohel Sheikh/Downloads/Anti-clone voice/Anticlonevoice/Backend/src/main/kotlin/com/jackmarcus/backend/Application.kt)
- Change host from `192.168.1.7` to `0.0.0.0` to allow connections from all network interfaces (including emulators).

#### [MODIFY] [FirebaseAdmin.kt](file:///C:/Users/Sohel Sheikh/Downloads/Anti-clone voice/Anticlonevoice/Backend/src/main/kotlin/com/jackmarcus/backend/plugins/FirebaseAdmin.kt)
- Update the filename check to include `service-account.json.json` which is the current name in the project.

---

### Android App

#### [MODIFY] [SignupScreen.kt](file:///C:/Users/Sohel Sheikh/Downloads/Anti-clone voice/Anticlonevoice/AndroidApp/src/main/java/com/jackmarcus/anti_clonevoice/ui/auth/SignupScreen.kt)
- Add text fields for `Username`, `Email`, and `Password`.
- Add a "Signup" button that calls `viewModel.signup`.

#### [MODIFY] [NetworkClient.kt](file:///C:/Users/Sohel Sheikh/Downloads/Anti-clone voice/Anticlonevoice/AndroidApp/src/main/java/com/jackmarcus/anti_clonevoice/data/remote/NetworkClient.kt)
- Provide a fallback for `BASE_URL` to use `10.0.2.2` if running on an emulator, or make it configurable. For now, I'll stick to a more flexible approach or stick to the user's IP if they prefer, but `0.0.0.0` on the backend helps.

## Verification Plan

### Manual Verification
1. Start the backend using the `:Backend:run` task.
2. Run the Android app.
3. Navigate to the Signup screen.
4. Create a new user account using the new fields.
5. Verify that it navigates to the Profile screen upon success.
6. Logout and try to login with the same credentials.
