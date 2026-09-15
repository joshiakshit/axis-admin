# Axis Admin

Axis Admin is a separate Android operations app for the Axis backend.

It uses application ID `com.ash.axis.admin`. It contains only the dashboard, user governance, and remote config.

## Local setup

Copy `local.properties.example` to `local.properties`. Set:

- `sdk.dir` to the Android SDK.
- `AXIS_BACKEND_URL` to the Worker base URL.
Generate one personal app credential. Store it as the Worker's `ADMIN_APP_TOKEN` secret:

```bash
cd ../axis/backend
openssl rand -hex 32 > ../axis-admin/.admin-app-token
npx wrangler secret put ADMIN_APP_TOKEN < ../axis-admin/.admin-app-token
npm run deploy
```

Install the debug APK. Provision the same credential over USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm enable com.ash.axis.admin/.provision.ProvisionActivity
adb shell am start -n com.ash.axis.admin/.provision.ProvisionActivity \
  --es credential "$(cat .admin-app-token)"
```

The provisioning activity accepts calls only from the Android shell. It disables itself after saving the credential in encrypted app storage. The credential is not compiled into the APK or committed.

## Checks

```bash
./gradlew test ktlintCheck detekt :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
