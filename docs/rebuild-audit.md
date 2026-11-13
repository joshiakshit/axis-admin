# Axis Admin rebuild audit

Date: 3 November 2026

## Scope and evidence

Reviewed all Android production screens, theme, session storage, repository, view model, API models, tests, manifest, and build configuration. Compared them with the sibling Axis backend's routes, identity/session handling, configuration validation, user mutations, and migrations. Made authenticated read requests to the deployed admin APIs. Did not mutate users or remote configuration, exercise the identity vulnerability against production, or change app/backend source.

The APK built successfully earlier in this chat. The current baseline passes `test ktlintCheck detekt --max-workers=2`: 11 tests in each of the debug and release variants, with no failures. These tests do not establish deployed API compatibility or cover the important UI recovery/concurrency cases.

Git has six commits and only the main branch in this checkout. The reflog begins at today's clone; there are no stashes or unreachable objects reported by `git fsck`. A plain-text scan of the available Android Studio and IntelliJ local histories found no Axis Admin matches. This does not establish that the lost version cannot exist on another machine, account, backup, or in compressed IDE history.

## Must fix before expanding functionality

### Critical: backend identity verification

`../axis/backend/src/session.ts:44` decodes iCloudEMS token claims without checking their signature or expiry. `../axis/backend/src/index.ts:151` uses those claims to identify a user, compares the admission number against `ADMIN_ADMNOS`, and can issue an administrator session. The admin app's separate device-token flow does not remove this alternative route into admin access.

Replace this trust boundary with verified upstream identity: validate the issuer's signed token with supported keys and claim checks, or validate identity against the upstream service. Determine which mechanism iCloudEMS actually supports before implementation. Add regression tests proving fabricated, expired, and wrong-issuer identities cannot obtain sessions or privileged roles. Restrict administrator issuance to an explicitly verified administrator flow. This is a source-confirmed vulnerability; production exploitability was not tested.

Implementation update after confirming the wrapper context: the unconfirmed JWKS dependency was removed. The local backend now restricts administrator issuance to the separately provisioned admin app credential and requires a signed admin-app purpose on privileged sessions. Student enrollment never promotes roles; old unmarked admin sessions are rejected. Student identity remains self-reported and spoofable. This addresses the admin escalation without claiming upstream identity verification; this boundary was deployed on 3 November 2026 and verified with live credential-based sign-in and legacy-session rejection.

### Broken deployed features

Authenticated live probes returned:

| API | Result |
| --- | --- |
| Device session | 200 |
| Health | 200 |
| Users | 200 |
| Remote config | 200 |
| Stats | 404 |
| Audit log | 404 |

`AdminViewModel.connect()` loads only health, whereas `loadDashboard()` fetches health and then stats and commits both together. A successful initial dashboard can therefore fail on refresh or return navigation; the fresh health response is discarded when stats fails. Load independent resources separately and retain partial success.

The sibling Worker has neither stats/audit routes nor audit writes/migrations. Implement real aggregation and persisted audit events, then verify the deployed contract. An unavailable audit service must display an explicit error, not an empty history. Current API contract tests omit both stats and audit.

### Sessions and replacement-device recovery

`AdminRepository.authorized()` sends the stored session without checking expiry and clears it on either 401 or 403. It does not renew and retry. Session issuance occurs only through `connect()`. Add one coordinated refresh on expired/401 sessions with a single retry; distinguish denied permission from expired authentication. Never blindly retry writes without clear idempotency semantics.

Provisioning saves an unvalidated device credential and flips the UI into the provisioned state before backend acceptance. An incorrect token leaves a retry screen with no replace-token route. Validate before committing provisioning state, or provide explicit Edit token and Forget device flows. Separate clearing an access session from deleting the device credential; the current `clear()` and unused `logout()` retain the latter.

The shared `ADMIN_APP_TOKEN` does not support individual device revocation. Rotating it prevents new sessions with the old credential but does not revoke already issued seven-day sessions. A later backend improvement should introduce individually revocable device credentials, stored as hashes, with device/session identifiers and revocation checks.

## UI direction

Use a consistent AMOLED interface: black page canvas, near-black grouped surfaces, white primary text and branding, muted secondary text, and small semantic status accents. Force the dark palette independently of system appearance. Align splash/window/status/navigation bar appearance too: the current XML theme explicitly uses a light parent/status bar while Compose switches with system theme.

The existing vector is already an A without its crossbar. Reuse it. Replace the tiny, theme-tinted standalone header with a real top app bar: white mark at the leading edge, Axis Admin identity and a clear page title, plus relevant refresh/overflow actions. Keep the mark white everywhere.

Use a Scaffold with an inset-aware top bar, content, and bottom navigation. Replace the manually overlaid floating navigation and fixed 112dp clearance. Its labels are currently 9sp; improve readable labels, touch targets, font scaling, keyboard handling, and small-screen reflow. Use restrained transitions rather than bouncing navigation icons. Do not add extra nested cards or dashboards just to fill space.

### Overview

1. Lead with a prominent pending-review summary and a direct Review requests action.
2. Follow with a compact accounts summary and factual service/APK state.
3. Show activity and adoption data only when the underlying endpoint succeeds. Label time windows and freshness.
4. Keep detailed version/device/SDK distributions lower on the page or expandable. Replace raw event keys with understandable labels.
5. Show cached/stale, refreshing, partial failure, and unavailable states distinctly. Current SERVICE ONLINE is hardcoded for any loaded health object and can remain stale.

### Users

Default to Pending for the approval workflow. Keep search and counted status filters together above a compact list; make filters wrap or scroll at large font sizes. Preserve search, filter, scroll, and selection across tab changes and rotation.

Rows should emphasize name, admission number, request age, and status. Put email, role, app version, device, session count, and complete timestamps in a detail sheet. Use oldest-request-first for pending review, with alternate sorting available; the current last-seen ordering can bury older requests.

Make Approve the clear primary action for pending users. Put Kick and Ban in contextual actions with explicit confirmation and visually destructive treatment. Keep administrator restrictions aligned with the backend. For a banned user, label the action as restoring access rather than an ordinary approval. Add selected-user batch approval with a review of exact identities/count. Move Approve all out of the main page header.

Provide row-specific progress, disable repeated taps for that user, and show actionable success/failure feedback. Refresh overview counts and badges after mutations. The current badge depends on the users list being loaded; dashboard data is not synchronized after mutations. Add no-pending, no-search-results, initial loading, and retry states.

### Config

Group settings by task: Releases, Announcements, Access rules, and Emergency controls. Auto-approval prefixes belong under access rules. Distinguish the Axis release version/build from the separate upstream OTP `appVersion`; the model includes that field but the editor does not expose it.

Keep an editable draft separate from the last server snapshot. Refresh currently replaces local fields whenever `updatedAt` changes, and leaving the screen loses unsaved edits. Preserve drafts and warn on leaving with changes. Provide a save area above the keyboard/navigation and a review of every changed field.

Validate nonnegative build codes, the relationship between minimum/latest builds, URL format/scheme, and access-rule syntax, with matching backend checks. Existing client validation only parses integers; the backend also lacks relational and URL checks. Add concurrency protection with a revision/conditional write to avoid overwriting another administrator's edits.

Review all high-impact changes together. Current confirmation uses a `when` that shows only the first matching risk: raising minimum and enabling the kill switch together only describes the minimum change. Show affected-build/user counts only if reliably calculated. Use clear Emergency access controls and contextual messages rather than hiding impact in a general save action.

### Audit

Implement the backend before presenting it as history. Record actor, action, target, timestamp, outcome, and redacted before/after configuration values. Decide retention and access requirements. Never record raw tokens. Use a grouped timeline with readable names, admission numbers, local timestamps, and filters for actor/action/date. Distinguish no activity from failure to load. Add pagination as history grows.

### Device access

Use the same black canvas and white branding as the signed-in app. Provide a labeled token field, submitting state, clear invalid/revoked/offline errors, retry, and replacement-token controls. Disable duplicate submissions. Expose device/session information, sign out, and Forget device through account/device settings.

## Implementation structure and verification

Split the 1,041-line `AxisAdminApp.kt` into the shell, individual screens, shared components, and theme tokens. Introduce resource-specific loading/error/freshness states and keyed mutation state instead of one global loading/error flag. Cancel or supersede stale requests; rethrow coroutine cancellation rather than swallowing it as a general error. Avoid launching nested independent operations inside a global loading wrapper.

Use saved state for lightweight navigation/filter/draft state. Add backend pagination/search when scale warrants it rather than repeatedly transferring every user. Health currently loads the full user list just to count statuses; use aggregate SQL when implementing analytics.

Add meaningful checks for deployed endpoint compatibility, identity verification, session expiry/recovery, invalid provisioning recovery, duplicate approval taps, counts after mutations, combined config risks, stale config conflicts, and preservation of unsaved edits. Verify the actual Compose layouts at small phone sizes and large font settings, with keyboard and screen-reader navigation. Existing mock/reflection tests pass while the production endpoints fail.

Build sequence: identity/session boundary and API contracts; AMOLED shell and approval-focused Users screen; overview data/state handling; configuration draft/review flow; real audit and analytics; final accessibility, release packaging, and emulator/device verification. Publish a signed release with explicit version metadata and backup/tag the recovered/rebuilt baseline so replacement installs have an identifiable version.
