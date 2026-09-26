# Notify

Android notes with text and voice recordings, offline Russian transcription,
audio/video file transcription, saved transcript history, and text/Markdown export.
The app supports light, dark, and system appearance.

## Build

Requirements: Git LFS, Android Studio with JDK 21, and Android SDK 36.
The Android Studio bundled JDK works. The minimum supported Android version is 7.0
(API 24).

```powershell
git lfs install
git clone git@github.com:bigflyy/Notify.git
cd Notify
git lfs pull
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Open this directory in Android Studio to configure the local SDK path, or provide
`sdk.dir` in an untracked `local.properties` file. Gradle downloads dependencies on
the first build. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

The exact speech models and `app/libs/sherpa-onnx-1.12.39.aar` are included using
Git LFS. These files are needed to build and run offline transcription; a checkout
containing only LFS pointer files is not sufficient. Build output, IDE caches,
local SDK settings, and signing keys are intentionally not versioned.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the license files under
`app/src/main/assets` for dependency and model notices.

## Versions

- `main`: latest tested application and standalone repository setup.
- `stable/original`: original stable version from the previous repository's `main`.
- `feature/*`: preserved development versions, including the latest transcript browser.

The project history was extracted from `lab04/Notify` in
`bigflyy/Object-oriented-analysis-and-design-2`. Notify's 22 existing commits and
all feature branches were preserved without the unrelated coursework. Commit IDs
changed because the project now lives at the repository root. The source repository
and original local project remain intact.

The large models and native library were not tracked in the old repository and
are added on `main` during migration. When building an older version, retain or
restore the required binary dependencies from `main`.

## Phone UI tests

Use a separate app installation to keep test fixtures separate from personal notes:

```powershell
.\gradlew.bat -PisolatedUiTests=true :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r --no-window-animation -e class com.example.notify.TranscriptBrowsingInstrumentedTest,com.example.notify.TranscriptDeletionInstrumentedTest com.example.notify.codextest.test/androidx.test.runner.AndroidJUnitRunner
```

Accept any installation or microphone permission prompts on the test app. Build
again without `-PisolatedUiTests=true` for the normal `com.example.notify` application.
