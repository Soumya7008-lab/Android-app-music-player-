# Implementation Plan - Runtime Permissions for Storage and Media

The user wants the app to request runtime permissions when performing actions that require access to storage or media files (Rescan and Edit Poster). Currently, these permissions are declared in the manifest but not requested at runtime, forcing the user to grant them manually in settings.

## Proposed Changes

### [app](file:///D:/andriodProjects/app)

#### [MODIFY] [HomeScreen.kt](file:///D:/andriodProjects/app/src/main/java/com/example/myapplication/HomeScreen.kt)
- Implement a `permissionLauncher` using `ActivityResultContracts.RequestMultiplePermissions()`.
- Create a helper function to check and request permissions for specific actions.
- Update `onScan` and `onEditPoster` to trigger the permission request if necessary.
- Add logic to handle the results of the permission request.

## Verification Plan

### Manual Verification
- Deploy the app to a device/emulator.
- Revoke storage/media permissions if granted.
- Tap "RESCAN" and verify that a permission dialog appears.
- Tap "Edit Poster" on a track and verify that a permission dialog appears.
- Grant the permissions and verify that the actions proceed (music is scanned, gallery opens).
- Test on both Android 13+ (API 33+) and older versions (API 32 or below) to ensure correct permissions are requested.
