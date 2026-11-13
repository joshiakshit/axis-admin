# Axis Admin 1.1.0 rebuild status

The phone installer is the signed release APK at
`app/build/outputs/apk/release/axis-admin-release.apk` (version code 2).
The debug APK remains at `app/build/outputs/apk/debug/app-debug.apk` for development.
Release signing material is outside Git in `~/.local/share/axis-admin/signing`;
see the root README for build and backup details.
The current app token is unchanged by this rebuild.

Implemented in the Android app:

- Forced AMOLED theme, white Axis mark, real top bar, inset-aware bottom navigation, and readable labels.
- Pending-first Users screen, preserved search/filter/selection state, user details, selected-request approval, and per-user progress/errors.
- Compact request-focused Overview; failed analytics no longer discards successful health data.
- Validated provisioning, replacement-token recovery, single-flight session renewal, and one retry on authentication rejection. Permission denials preserve a valid session.
- Counts update after mutations; stale page loads are canceled when user actions begin.
- Saved config drafts, validation, full change review, navigation discard protection, and server-change conflict handling.
- Paginated activity UI that distinguishes errors from an empty audit history.
- Separate screen/component files and an explicit Forget device flow that clears encrypted credentials and saved page state.

Deployed in the sibling `../axis/backend`:

- Wrapper-compatible student enrollment without unconfirmed provider settings. Only the separate admin credential flow issues signed administrator sessions with the required admin-app purpose; student claims cannot promote users or authorize admin routes. Legacy unmarked admin sessions are rejected.
- Stats and paginated audit endpoints, administrator approval checks, and approved-user checks for telemetry.
- Atomic access-change/audit transactions.
- Configuration migration from the existing KV seed to an authoritative D1 row on first write. Revision compare-and-swap and audit commit together; stale saves return 409 and audit failures roll back the save.
- Validation of release policy, HTTPS download links, and automatic-approval prefixes. Audit records contain changed configuration field names, never secret values.

Validation:

- `./gradlew test ktlintCheck detekt :app:assembleDebug --max-workers=2` passes with JDK 17.
- 20 Android tests pass in each of debug and release variants.
- Signed release assembly, APK alignment, v2/v3 signature verification, and manifest checks pass. The APK is non-debuggable and excludes the debug provisioning activity.
- 69 backend tests and TypeScript checking pass; Wrangler deployment dry run passes.
- Migration SQL and stale revision rejection were also exercised with SQLite.
- The APK was installed on the Android 15 emulator and the main layouts inspected at 360dp width. Overview was also inspected at 130% font scale; the white Axis mark was verified after activity recreation.
- The unsaved-change navigation prompt was exercised using a temporary draft; the draft was discarded without saving live settings.

The backend was deployed on 3 November 2026 as Worker version `c59c8d24-0756-48a3-b849-0cc5d4619ae4`. Migration `0005_admin_audit.sql` was applied successfully; no migrations remain pending. A private database backup and pre-deployment snapshot were saved outside Git under `~/.local/share/axis-admin/deployments/2026-11-03-admin-update`.

Live verification passed for device-credential sign-in, health, Stats, Audit, Config, and Users. All 27 user records and remote settings exactly matched the pre-deployment snapshot. Anonymous admin requests and the old unmarked admin session returned 401. The new admin session carries the required admin-app purpose. The updated Android app can renew its old session using the unchanged saved app credential.

Student enrollment identity remains self-reported/spoofable. The deployed fix separates administrator authority without claiming to verify upstream student identity. No iCloudEMS issuer/JWKS settings are required.

Remaining follow-up work includes individual device credential registration/revocation, richer audit filtering, server-side analytics aggregation/pagination for large installations, an automated release/update pipeline, and broader automated accessibility/UI coverage. Existing sessions are not revoked by rotating the shared app credential.
