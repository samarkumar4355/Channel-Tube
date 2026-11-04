# ChannelTube (Android MVP)

ChannelTube is a native Android starter app built with Kotlin and Jetpack Compose. Users can save multiple YouTube channels, load their recent uploads, and browse a feed containing only those saved channels.

## Included in this project

- Search channels by name, YouTube channel URL, or `@handle`.
- Add and remove multiple channels.
- Save channels and video metadata locally with Room.
- Fetch each saved channel's **Uploads** playlist.
- Filter the Home feed against the currently saved channel IDs.
- Play videos using YouTube's embedded player in a WebView.
- Open a video in the YouTube app/browser when needed.
- A small unit test for the selected-channel feed filter.

## Requirements

- Android Studio with support for Android Gradle Plugin 9.1.1.
- JDK 17 or newer (Android Studio's bundled JBR is fine).
- Android SDK Platform 37 and Build Tools installed from SDK Manager.
- Internet access for Gradle dependency downloads and YouTube API calls.
- A Google Cloud project with **YouTube Data API v3** enabled and an API key.

## 1. Get a YouTube Data API key

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Create or select a project.
3. Enable **YouTube Data API v3** in APIs & Services → Library.
4. Create an API key in APIs & Services → Credentials.
5. Apply API restrictions to YouTube Data API v3. For a production release, also apply appropriate application restrictions and do not rely on a key embedded in a client app.

The current MVP calls the public Data API directly from the Android app. A key packaged into an APK can be extracted; for a production/public release, move YouTube Data API calls behind your own backend and keep the key on the server.

## 2. Add the API key

Open the project's `local.properties` file in the project root. Android Studio normally creates this file after you open the project. Keep its existing `sdk.dir` line and add:

```properties
YOUTUBE_API_KEY=YOUR_REAL_YOUTUBE_API_KEY
```

There is a `local.properties.example` file for reference. Do not commit `local.properties` or share your real key.

## 3. Open and run

1. Extract the ZIP.
2. Open the `ChannelTube` folder in Android Studio.
3. Install Android SDK Platform 37 if Android Studio asks for it.
4. Add `YOUTUBE_API_KEY` to `local.properties` as shown above.
5. Let Gradle sync finish.
6. Run the `app` configuration on an Android device or emulator (Android 8.0 / API 26 or newer).

You can also build from a terminal after installing the Android SDK:

```bash
./gradlew :app:assembleDebug
```

The included lightweight Gradle bootstrap downloads the official Gradle 9.3.1 distribution on first use. Android Studio may use the same wrapper configuration during sync.

## How to use the app

1. Open **Channels**.
2. Search for a channel name, paste a channel URL, or enter an `@handle`.
3. Tap **Add** on the channel you want. Repeat to add more channels.
4. Open **Feed** and tap **Refresh**.
5. Tap a video card to play it.
6. Remove a channel from **My Channels** to remove its cached videos from the feed.

## Current MVP limits

- The feed loads up to the latest 25 uploads per saved channel per refresh. Pagination is not implemented yet.
- Search by channel name uses YouTube's `search.list` endpoint, which has a higher quota cost than `channels.list` / `playlistItems.list`. Prefer a channel URL or `@handle` when you already know the channel.
- It shows recent uploads, not a full YouTube recommendation feed, Shorts feed, comments, live chat, or subscriptions.
- The embedded player may not support videos whose owners or YouTube restrict embedding. YouTube controls, branding, ads, and playback rules remain under YouTube's control.
- This app does not download, extract, or bypass access controls for YouTube videos.
- Feed filtering is enforced using saved channel IDs in the app. It does not restrict the separate YouTube app or browser.
- API access is subject to Google/YouTube quota, terms, and policies.

## Architecture

```text
Compose UI
   |
MainViewModel (StateFlow)
   |
ChannelRepository ---- YouTubeApi ---- YouTube Data API v3
   |
Room (channels + cached video metadata)
   |
FilterVideosUseCase (saved channel IDs only)
   |
Home Feed / Embedded YouTube Player
```

## Main project structure

```text
app/src/main/java/com/example/channeltube/
├── MainActivity.kt
├── data/
│   ├── local/        # Room entities, DAO, database
│   ├── remote/       # YouTube Data API client and DTO model
│   └── repository/   # Channel and video data coordination
├── domain/           # Selected-channel feed filter
├── ui/               # Compose screens and embedded player
└── viewmodel/        # UI state and user actions
```

## Useful official documentation

- [YouTube Data API v3](https://developers.google.com/youtube/v3)
- [Channels: list](https://developers.google.com/youtube/v3/docs/channels/list)
- [PlaylistItems: list](https://developers.google.com/youtube/v3/docs/playlistItems/list)
- [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference)
- [Jetpack Compose setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)



## License

This project is licensed under the MIT License.
See the [LICENSE](LICENSE) file for details.