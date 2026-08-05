# Sa2ration (Shizuku)

A minimal, non-root Android saturation controller inspired by Sa2ration.
The interface contains a guided setup button, setup log, live preview image, and slider.

Preview photo: [Justyna Serafin on Pexels](https://www.pexels.com/photo/autumn-colors-of-trees-in-the-mountain-14105397/).

## Requirements

- Android 7.0 or newer
- [Shizuku](https://shizuku.rikka.app/download/) installed and running
- Wireless debugging (Android 11+) or a USB/PC start after each reboot

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Notes

- The supported saturation range is 0% to 100%; 100% is the default.
- The app saves and reapplies the chosen value automatically.
- On startup it detects Shizuku, requests permission, and opens Shizuku or its official download page when user action is required.
- Android applies global saturation using a built-in three-second transition. The app sends slider changes continuously, but Samsung blocks the private instant SurfaceFlinger path.
- Samsung blocks ADB-shell access to SurfaceFlinger on this phone, so increasing saturation above 100% is not possible without root. Android's supported color display service can only reduce saturation.
- The original Sa2ration repository has no software license, so none of its code or artwork is included here.
