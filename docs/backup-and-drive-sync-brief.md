# Japa backup and Google Drive sync

Future implementation brief. Use the existing Kotlin, Jetpack Compose, Room, and WorkManager app. Room remains the offline source of truth. Free users keep full local use and manual JSON export and restore. A verified Google Play Pro subscription adds private Google Drive backup and restore. The app requires no developer-operated backend, Firebase Firestore, or external app server.

## Project preparation

Inspect both Gradle build files, the manifest, Room schema and migrations, the existing backup manager and Compose screen, and any billing or dependency injection setup before making changes. Use the project's existing conventions and add only the missing dependencies. Keep reusable backup and sync logic under `com.app.core.backup`, with a project-specific Room adapter for Japa entities.

The recovery branch at this writing has Room schema 9 and existing version-1 JSON export and restore. The snapshot covers categories, counter logs, legacy baby sessions and detections, walking sessions, Japa sessions, and Japa goals. Preserve compatibility with those backups. Confirm billing and paywall availability during implementation. The current app dependencies do not include Play Billing.

## Serialization and free device backup

- Define a serializable `BackupPayload` using Kotlin serialization or Gson. Include `schemaVersion = 1`, `appVersion`, `exportedAtTimestamp`, and serialized data for every current Room entity. Treat the payload format number and Room database version as independent.
- Provide `BackupRepository.exportDatabaseToJson(): String` and `importDatabaseFromJson(jsonString: String): Result<Unit>`. Capture the snapshot in a single read transaction. Validate data and version before replacing or upserting records in a single transaction. Accept existing version-1 exports with a compatibility decoder.
- Provide a `LocalFileBackupManager` integrated with Android SAF through `CreateDocument("application/json")` and `OpenDocument()`. Use `AppBackup_YYYYMMDD_HHmm.json` as the default filename, perform stream I/O on `Dispatchers.IO`, and show clear success or error feedback. Confirm before replacing local records.

## Pro account linking and Drive

- Use Android Credential Manager for Sign in with Google and Google Identity AuthorizationClient for Drive access. The precise OAuth scope is `https://www.googleapis.com/auth/drive.appdata`. Keep sign-in identity and Drive authorization bound to the linked account.
- Enable the Drive API and configure the OAuth consent screen and Android OAuth client for the release package and signing certificate. Put the **Web OAuth client ID** in `app/src/main/res/values/strings.xml` as `<string name="default_web_client_id" translatable="false">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>`, or use the equivalent generated resource if the project already provides it. The app should handle an unconfigured cloud build gracefully.
- Implement `GoogleDriveSyncManager` with Drive REST v3 and a lightweight bearer-token HTTP client. Use only `appDataFolder` and list with `spaces=appDataFolder`. Create or update one canonical `app_cloud_backup.json` per account.
- Implement `uploadBackup(json: String): Result<Long>` and `downloadLatestBackup(): Result<String?>`. Track returned Drive metadata, including file ID and server `modifiedTime`, alongside the last successful sync and dirty revision. Compare remote state with the last known remote snapshot before overwriting. If both copies changed, preserve both and surface a conflict. A deliberate cloud restore uses the same validated transactional import as local restore.
- Prompt for consent or reauthorization in the foreground when required. Background work can proceed silently while the linked account and authorization are valid.

## Billing and WorkManager

- Use existing Play Billing entitlement and paywall plumbing when available. Otherwise define `SubscriptionChecker` exposing `isProUser: StateFlow<Boolean>` and wire it to validated Play purchase state. Ensure Play is the source of subscription status. Document the verification limits of a backendless design.
- Use unique, network-constrained `DriveAutoSyncWorker` work, with a periodic interval such as 12 hours and optional scheduling when a durable dirty marker is set. Skip cloud operations unless Pro, linked, and authorized. Respect the auto-sync setting and cancel automatic work on disconnect or loss of entitlement.
- Expose `triggerManualDriveSync()` for Sync Now. Coordinate scheduled and manual work to prevent overlapping overwrites. Mark a revision synced only after success and retain changes made while an upload was running.

## Backup and Sync screen

Use the existing Compose theme and language support. Show **Device Backup** with **Export Backup File** and **Restore from File** for everyone. Show **Google Drive Cloud Sync** with a Pro badge. Free users see a short explanation and an **Upgrade to Pro** entry to the paywall. Pro users see **Connect Google Account**, or their connected email and **Disconnect**, plus **Auto-Sync**, **Sync Now**, **Restore from Cloud**, and the formatted **Last synced** time when available. Show progress, failures, authorization needs, and conflicts clearly.

Verify old and new backup round trips, failed imports and rollback, offline and revoked-account behavior, local edits during upload, two-device conflicts, and Play test-track purchase and entitlement changes before release.
