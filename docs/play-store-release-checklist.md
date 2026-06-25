# Play Store Release Checklist

Use this checklist before each signed Google Play upload.

## Build and signing

- [ ] Confirm `versionCode` is higher than every previously uploaded artifact.
- [ ] Confirm `versionName` matches the public release version.
- [ ] Build a **signed release App Bundle** in Android Studio (`.aab` preferred for Play), not only an APK.
- [ ] Confirm the release variant uses minification and resource shrinking.
- [ ] Save the generated mapping file for each release if you need crash de-obfuscation.
- [ ] Install the signed release artifact on at least one phone and one tablet before uploading.

## Local validation commands

Run these from the repository root:

```bash
./gradlew clean
./gradlew lint
./gradlew test
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Expected result: every command ends with `BUILD SUCCESSFUL`.

## Release smoke test

- [ ] Fresh install opens to the library without crashes.
- [ ] Import one valid EPUB through the in-app picker.
- [ ] Open an EPUB from Android Files / share sheet / email attachment if available.
- [ ] Duplicate import is detected or handled cleanly.
- [ ] Reader opens, closes, and restores progress after app restart.
- [ ] Reader settings persist: theme, font, brightness, layout.
- [ ] Search, sort, grouping, book details, bookmarks/highlights features behave as expected for the release scope.
- [ ] Dark, light, sepia, and OLED themes remain legible.
- [ ] Rotate the device and test tablet/wide layouts.
- [ ] Kill the app from recents, relaunch, and verify no data loss.
- [ ] Disable network and confirm dictionary/online features fail gracefully.

## Play Console content

- [ ] App name, short description, and full description are final.
- [ ] App icon and feature graphic are uploaded.
- [ ] Phone screenshots are uploaded.
- [ ] Tablet screenshots are uploaded if advertising tablet support.
- [ ] Content rating questionnaire is complete.
- [ ] Target audience is selected correctly.
- [ ] Ads declaration is complete.
- [ ] App category is selected.
- [ ] Contact email is valid.

## Privacy and policy

- [ ] Privacy policy URL is published and matches the app behavior.
- [ ] Data Safety form covers local EPUB files, library metadata, reading progress, settings, and any network lookups.
- [ ] Explain the `INTERNET` permission if dictionary lookup or other online functionality is present.
- [ ] Confirm no test analytics, debug endpoints, or sample credentials are shipped.
- [ ] Confirm backup/data extraction rules are intentional for user library data.

## Manifest and release behavior

- [ ] Verify every exported activity/receiver is intentional.
- [ ] Verify EPUB file-open and share intents work on release builds.
- [ ] Confirm disabled widget receivers are intentionally disabled or remove them before release.
- [ ] Confirm no developer-only screen is reachable from launcher or normal navigation.

## After upload

- [ ] Upload to internal testing first.
- [ ] Add release notes.
- [ ] Test install from Play internal testing, not just local sideload.
- [ ] Watch Android vitals, pre-launch report, crashes, and ANRs.
- [ ] Promote gradually after internal testing passes.
