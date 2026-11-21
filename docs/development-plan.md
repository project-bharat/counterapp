# Japa Counter development plan

Last updated 21 November 2026. This file is the running project handoff. Read it before starting a new development task and update it in the same change when a milestone, decision, test result, or blocker changes. Keep feature details in linked briefs and record build and commit references here.

## Product direction

Build a Marathi-first native Android Japa counter with English support. Keep the original Build #528 Japa interface as the visual foundation and make daily practice quick to start, reliable offline, and easy to review. The app uses Kotlin, Jetpack Compose, Room, and WorkManager. The application ID is `com.projectbharat.counterapp`.

Manual counting, local history, reminders, and local backup belong in the free experience. Pro adds private Google Drive backup and sync through a Play subscription, with Room as the local source of truth and no developer-operated backend. Pricing, premium analytics, and any voice allowance are open product decisions.

## Current baseline

Development is on `japa-ui-recovery-528`. The last verified app build is **#546** at `eadf1fc`. GitHub Actions compiled the debug APK and passed unit tests. The standalone APK and the APK inside the ZIP were verified to match. Phone testing is pending. [Build #546 and artifacts](https://github.com/project-bharat/japacounterapp/actions/runs/44876342188)

| Area | Current state |
| --- | --- |
| Recovery and cleanup | Build #528 Japa interface preserved as the foundation. Baby Movement and Steps were removed from active routes, quick actions, notifications, reminders, and the widget. Legacy Room and backup records remain supported. |
| Practice | Manual and on-device voice counting, presets and custom mantras, persistent per-mantra sessions, and a Finish summary. |
| Progress | Editable daily goal starting at 108, separate practice and goal streaks, and a calendar that opens the selected day's history. |
| Local backup | Version-1 JSON export and restore through Android file pickers. Includes Japa sessions and goals as well as legacy data. Room schema is version 9. |
| Billing and cloud | Planned. Play Billing, the Pro paywall, Drive account linking, and background sync are pending. |
| Release | Debug APK verified. Signed release bundle, Play Console setup, and on-device validation are pending. |

## Delivery order

1. **Validate Build #546 on phones.** Test a fresh install and an upgrade with existing data. Check manual and voice counts across interruption, Finish, microphone denial, offline model availability, goal history, calendar navigation, reminders, widget refresh, and a local backup round trip.
2. **Polish the daily practice experience.** Prioritize one-handed controls, clear voice states, feedback preferences, and recent or favourite mantras. Review Marathi wrapping, large fonts, dark mode, and TalkBack. Keep optional content and public leaderboards out of the critical path.
3. **Prepare the Play release and subscription.** Confirm the current target API and billing requirements, configure Play signing and test tracks, prepare the signed Android App Bundle and store materials, and verify purchase, restore, cancellation, and expiry in Play testing. Confirm prices and subscription periods before publishing.
4. **Implement and verify Pro Drive backup.** Follow the backup brief. Set up Google OAuth and the Drive API, preserve older local backups, and validate account linking, revoked access, retry behavior, and conflicts across devices. [Backup and Drive sync brief](backup-and-drive-sync-brief.md)
5. **Evaluate follow-up features.** Consider personal milestones, recap cards, and advanced goals after the core flows are validated.

## Milestone log

- **Build #544** completed the Japa-only recovery cleanup and passed debug CI. The APK and ZIP were verified. [Build #544](https://github.com/project-bharat/japacounterapp/actions/runs/44873765597)
- **Build #546** added persistent practice sessions, Finish summary, daily goals, and calendar-to-history navigation. Debug CI and artifact verification passed. [Build #546](https://github.com/project-bharat/japacounterapp/actions/runs/44876342188)
- **21 November 2026** recorded the offline-first backup and Pro Drive sync requirements for future implementation.
