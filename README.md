# Walkie Talkie

Push-to-talk voice over your **local network only** (Wi-Fi or a phone hotspot). No internet, no accounts, no server.

## How it works
- **Discovery:** every phone broadcasts a small HELLO beacon (UDP port 47474) every 2 s and also unicasts it to peers it already knows, so it works even where broadcast is filtered.
- **Audio:** 16 kHz mono, G.711 mu-law (8-bit), 20 ms frames, sent as UDP unicast to each peer on the same channel. Receivers use a ~60 ms jitter buffer.
- **Channels:** 1-8. Only phones on the same channel hear each other.
- **Background:** a foreground service (microphone type) keeps listening when the screen is off.
- **Hotspot:** works the same as Wi-Fi. The hotspot phone and the phones joined to it all see each other.

Packet format is documented at the top of `engine/Protocol.kt`.

## Build
- Android Studio (Ladybug or newer), JDK 17: open the folder and run.
- Or: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- GitHub Actions (`.github/workflows/build.yml`) builds the debug APK on every push.

## Next ideas
Opus codec, volume-key PTT, optional per-channel password (encryption), recorded voice messages.
