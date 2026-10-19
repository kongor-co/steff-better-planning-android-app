# Safe Start

Safe Start is an offline Android planner for people who underestimate time. It schedules flexible activities backwards from fixed Anchors and shows the latest time a realistic plan can begin.

## Included in the MVP

- One continuous timeline with one or more fixed-time Anchors
- Capacity enforcement for each time segment between Anchors
- Required Tasks and lower-priority Optional activities
- Complexity-based buffers from 20% to 60%
- Demandingness-based pause suggestions
- Confirmations for reduced buffers and removed pauses
- Backwards scheduling with unused time kept at the start
- Per-window and overall capacity summaries
- Warning state from 80% and a hard block above 100%
- Manual activity ordering and movement across Anchors
- Persistent completion checkboxes for planned activities
- Reusable activity templates with explicit updates
- Named saved plans with explicit Save actions
- Plans home screen with rename, duplicate, and delete actions
- Recoverable unsaved drafts with guarded navigation
- Global reusable activities shared across every plan
- Versioned local persistence with checksums, backup recovery, and legacy migration
- Short first-use onboarding and an in-app terminology guide

## Build

Open the project in Android Studio, or run:

```text
gradlew.bat test assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The project targets Android API 36 and requires JDK 17.
