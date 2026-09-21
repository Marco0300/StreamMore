# Streammore Android TV

Native Android TV client for the existing Streammore service.

This is **not** a WebView. The app has its own Compose UI, Android TV focus
navigation, and Media3/ExoPlayer playback. It consumes the existing Streammore
API and Flyx retrieval URLs.

## Development

Set the service address in `app/build.gradle.kts` if needed:

```text
http://192.168.3.91:3896
```

Build a debug APK:

```bash
export ANDROID_SDK_ROOT=$HOME/android-sdk
./gradlew assembleDebug
```

## Release updates

The TV app checks the public GitHub Releases API for this repository:

```text
https://github.com/Marco0300/StreamMore/releases/latest
```

Publish a newer signed APK as a GitHub Release with a higher semantic version
(for example `v1.0.1`). The APK asset must be signed with the same release key as
previous versions. Android still requires the user to confirm installation.

The release signing keystore is deliberately excluded from this repository.
Keep it backed up securely; losing it prevents future APK updates.

