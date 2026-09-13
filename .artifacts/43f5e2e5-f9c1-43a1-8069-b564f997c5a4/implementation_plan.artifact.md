# Implementation Plan - Phone Number and Gmail Sign-up

This plan outlines the steps to integrate Phone Number (OTP) and Google Sign-in into the `SignupScreen`, providing users with multiple registration options consistent with the `LoginScreen`.

## User Review Required

> [!IMPORTANT]
> The implementation relies on Firebase Authentication. Ensure that:
> 1. `google-services.json` is correctly configured in the project.
> 2. Phone Authentication and Google Sign-in are enabled in the Firebase Console.
> 3. The `YOUR_SERVER_CLIENT_ID` placeholder in `SignupScreen.kt` (copied from `LoginScreen.kt`) must be replaced with the actual Web Client ID from the Firebase console for Google Sign-in to work.

## Proposed Changes

### Authentication UI & Logic

#### [MODIFY] [SignupScreen.kt](file:///C:/Users/Sohel Sheikh/Downloads/Anti-clone voice/Anticlonevoice/AndroidApp/src/main/java/com/jackmarcus/anti_clonevoice/ui/auth/SignupScreen.kt)
- Add state variables for `phoneNumber` and `otpCode`.
- Integrate `CredentialManager` for Google Sign-in.
- Implement UI components for:
    - Google Sign-in button.
    - Phone number input field.
    - "Send OTP" button.
    - OTP verification field (visible after code is sent).
- Add `onSignupSuccess` callback to the `SignupScreen` composable.
- Observe `AuthViewModel.AuthState.Authenticated` and trigger `onSignupSuccess`.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Sohel Sheikh/Downloads/Anti-clone voice/Anticlonevoice/AndroidApp/src/main/java/com/jackmarcus/anti_clonevoice/MainActivity.kt)
- Pass an `onSignupSuccess` lambda to `SignupScreen` that navigates to the "profile" destination, mirroring the `LoginScreen` behavior.

## Verification Plan

### Manual Verification
- **Google Sign-in:** Tap the "Sign up with Google" button and verify successful authentication and navigation to the profile screen.
- **Phone Sign-in:**
    - Enter a valid phone number.
    - Tap "Send OTP" and wait for the SMS.
    - Enter the received OTP and tap "Verify OTP".
    - Verify successful navigation to the profile screen.
- **Form Validation:** Ensure traditional username/email/password signup still works.
- **Error Handling:** Verify that error messages (e.g., invalid OTP, cancelled Google sign-in) are displayed correctly.
