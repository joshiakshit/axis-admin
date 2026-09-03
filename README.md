# Axis Admin

Axis Admin is a separate Android operations app for the Axis backend.

It uses application ID `com.ash.axis.admin`. It contains only admin sign-in, dashboard, user governance, and remote config.

## Local setup

Copy `local.properties.example` to `local.properties`. Set:

- `sdk.dir` to the Android SDK.
- `AXIS_BACKEND_URL` to the Worker base URL.
- `ICLOUD_API_TOKEN` to the current iCloudEMS API bearer used for OTP calls.

The iCloudEMS token is an external API credential. It is not an Axis Worker admin secret. Never place `ADMIN_TOKEN`, `SESSION_SECRET`, or `ADMIN_ADMNOS` in this project.

The Worker also needs `ICLOUD_API_TOKEN` as a secret. It uses this token to verify an admin identity through the iCloudEMS refresh endpoint.

```bash
cd ../axis/backend
npx wrangler secret put ICLOUD_API_TOKEN
```

## Checks

```bash
./gradlew test ktlintCheck detekt :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
