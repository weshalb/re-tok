# ReTok (test build)

Android test app: while TikTok is open, it recognises the video on screen and shows a floating bubble when a comment on that video was saved (by you or a friend). Tapping the bubble opens TikTok's own comment link.

## Build
GitHub Actions builds `ReTok.apk` on every push to `main` and publishes it as a release. Locally: `gradle assembleDebug` (needs the Android SDK).

## How it works
- `BubbleService` – accessibility service, only reacts to TikTok (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`). Reads visible texts, matches them against saved comments on the device, draws the bubble as an accessibility overlay.
- `ShareActivity` – share target. Share a TikTok comment link to save it, together with the texts of the video currently on screen. Also imports comments a friend sent ("cb1:" text).
- `Matcher` – learns which texts appear on most screens (tabs, buttons) and ignores them; a video matches when at least 2 and at least half of its distinctive on-screen texts were saved.
- `MainActivity` – setup, saved comments, and a "Test info" dump of what the app read on the last TikTok screen.

No server yet: friends exchange saved comments with the "Send to friend" button.
