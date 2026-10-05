# Safe Start

Safe Start is an offline Android planner for people who underestimate time. It schedules flexible activities backwards from fixed Anchors and shows the latest time a realistic plan can begin.

## Included in the MVP

- One or more fixed-time Anchors and independent planning windows
- Required Tasks and lower-priority Optional activities
- Complexity-based buffers from 20% to 60%
- Demandingness-based pause suggestions
- Confirmations for reduced buffers and removed pauses
- Backwards scheduling with unused time kept at the start
- Per-window and overall capacity summaries
- Warning state from 80% and a hard block above 100%
- Manual activity ordering and movement between windows
- Reusable activity templates with explicit updates
- Local persistence through Android shared preferences
- Short first-use onboarding and an in-app terminology guide

## Build

Open the project in Android Studio, or run:

```text
gradlew.bat test assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The project targets Android API 36 and requires JDK 17.
