# Reader launch benchmarks

`ReaderLaunchBenchmark` imports deterministic 24-chapter and 1,500-chapter EPUBs through
the app's external-open workflow. The benchmark APK shares only its generated fixture
directory through a non-exported, grant-based FileProvider. No production seeding endpoint
or user library reset is required.

Each iteration opens Chapter 1 before preparing one distinct entry point:

- Library: return to the library and use the fixture's Resume action.
- Book Details: remain on details and use its Read/Resume action.
- Non-current chapter: remain on details and open Chapter 2.

Measurement includes frame timing and the `reader.open` trace. Readiness selectors come
from `ReaderRenderState.Ready` after the common styling/content/pre-draw gate, and require
the expected chapter resource. Missing controls, failed imports, and readiness timeouts
fail the run. A negative-control test requires an intentionally missing entry to fail.

Run on an isolated device or emulator with the Gradle wrapper:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
```

The benchmark build is release-like, minified, profileable, and signed with the local debug
key solely for installation. Production release signing is unchanged. AndroidX rejects
emulators for trustworthy performance numbers; to verify harness correctness on an emulator:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR"
```

Emulator results are functional evidence, not reference-hardware performance acceptance.
Baseline profiles and the roadmap's 30%/20% improvement targets remain unmeasured.
