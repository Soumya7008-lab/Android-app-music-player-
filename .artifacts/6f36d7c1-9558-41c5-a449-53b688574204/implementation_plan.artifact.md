# App Size Optimization Plan

The current app size is approximately 100 MB. I have identified several major contributors to this size, including disabled minification, unused dependencies, and unoptimized native library delivery. This plan outlines the steps to reduce the app size while preserving all visual components, animations, and critical logic.

## Proposed Changes

### Build Configuration Optimization

#### [MODIFY] [app/build.gradle.kts](file:///D:/andriodProjects/app/build.gradle.kts)
- Enable R8 minification (`isMinifyEnabled = true`) for release builds.
- Enable resource shrinking (`isShrinkResources = true`) to remove unused resources.
- Add `ndk.abiFilters` to include only common architectures (`arm64-v8a`, `armeabi-v7a`), reducing the native payload.
- Remove unused Room dependencies.

#### [MODIFY] [gradle.properties](file:///D:/andriodProjects/gradle.properties)
- Enable R8 "Full Mode" for more aggressive dead-code elimination and optimization.

### Dependency Cleanup

#### [MODIFY] [gradle/libs.versions.toml](file:///D:/andriodProjects/gradle/libs.versions.toml)
- Remove unused Room library definitions.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleRelease` and compare the APK size before and after.
- Perform a smoke test of the UI to ensure R8 hasn't over-aggressively removed needed code (though the current project doesn't seem to use much reflection that would be risky).

### Manual Verification
- Deploy the optimized build to a device.
- Verify that the 16D Spatial Audio and Equalizer features still work correctly (as they involve native code and JNI).
- Verify all animations and UI components remain intact.
