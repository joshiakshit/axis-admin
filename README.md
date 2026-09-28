# Axis Admin

Axis Admin is a separate Android operations app for the Axis backend.

It uses application ID `com.ash.axis.admin`. It contains only the dashboard, user governance, and remote config.

## Requirements

- JDK 17 and Android SDK 35.
- An Android device or emulator running Android 8.0 or later.
- The Axis backend with an approved administrator and configured session secrets.
- Android platform tools for USB provisioning.

The app uses Kotlin, Jetpack Compose, Hilt, Retrofit, and encrypted preferences.
Building the app does not grant administrator access. The backend must accept
the separately provisioned credential.

## Local setup

Copy `local.properties.example` to `local.properties`. Set:

- `sdk.dir` to the Android SDK.
- `AXIS_BACKEND_URL` to the Worker base URL.
Generate one personal app credential. Store it as the Worker's `ADMIN_APP_TOKEN` secret:

```bash
openssl rand -hex 32 > .admin-app-token
cd ../axis/backend
npx wrangler secret put ADMIN_APP_TOKEN < ../../axis-admin/.admin-app-token
npm run deploy
cd ../../axis-admin
```

These commands use a Bash-compatible shell and assume `axis` and `axis-admin`
are sibling folders. Build the debug APK before installing it:

```bash
./gradlew :app:assembleDebug
```

Install the debug APK. Provision the same credential over USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm enable com.ash.axis.admin/.provision.ProvisionActivity
adb shell am start -n com.ash.axis.admin/.provision.ProvisionActivity \
  --es credential "$(cat .admin-app-token)"
```

The debug provisioning activity requires Android's privileged `DUMP` permission,
which the ADB shell holds. It disables itself after saving the credential in
encrypted app storage. The credential is not compiled into the APK or committed.

## Checks

```bash
./gradlew test ktlintCheck detekt :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

On Windows PowerShell, use `./gradlew.bat` in place of `./gradlew`.

## Project structure

- `app/src/main/`: admin screens, API client, and encrypted session storage.
- `app/src/debug/`: device credential provisioning.
- `app/src/test/`: model, repository, API contract, and view-model tests.
- `config/detekt/`: static analysis settings.

Keep `local.properties`, `.admin-app-token`, and signing keys outside Git.
