# GTA5 Android Launcher

An Android launcher for the GTA V web port: it checks your game files, serves them locally, and starts the game either in a **native
engine** inside the app (Vulkan, touch controls, sound, saves) or in Chrome.

> **You need your own legally obtained copy of the game files.** This repository contains no game code, game data or engine binary.
> The native engine library (`libgta5engine.so`) is built from your own game files with a separate, private toolchain and is never
> committed here.

## Features

- **Game files**: pick the game folder (Storage Access Framework), verify it against `manifest.json` (missing files, size mismatches,
  progress), archive extraction.
- **Two ways to run the game** (Settings → *Run game in*):
  - **Native engine (in the app)** – `NativeGameActivity` loads `libgta5engine.so` (the game engine as native ARM64 code with a Vulkan
    renderer) in its own process. No browser, smooth frame pacing, Android audio (AAudio), real save files.
  - **Chrome** – a built-in HTTP server (`127.0.0.1`) serves the web build with the cross-origin isolation headers it needs, and the
    game opens in a Chrome Custom Tab with an injected touch overlay (`assets/touch/touch.js`).
  - **Automatic** – native when the engine library is present, otherwise Chrome.
- **Context touch controls** (native mode, `controls/NativeControlsView.java`): the buttons change with what the player is doing –
  on foot, car, motorbike, bicycle, boat, submarine, helicopter, plane, the phone (phone dock) and the pause menu (menu navigation).
  The engine reports the situation (`NativeEngine.controlState()`); keys follow the PC defaults (horn G, lean Shift/Ctrl, flight
  pitch/roll on the numpad, …). Floating move stick, swipe-to-look on the right half, fire/aim buttons that also turn the camera,
  latching sprint, optional steering buttons, size/opacity/camera speed/invert-Y/haptics settings.
- **Gamepad** support (buttons and sticks mapped to the game's controls; the touch overlay can hide when a pad is used).
- **Graphics settings** like the iOS port: presets (Ultra Performance … Max) and individual options (resolution scale, textures,
  shadows, reflections, grass, draw distance, frame limit, CPU cores).
- Story saves manager, self radio, cheats/vehicle spawner (Chrome mode), mods folder.

## How it works

```
MainActivity ──► GameActivity ──► Native engine?  yes ─► NativeGameActivity (process :engine)
     │                                  │                   ├─ SurfaceView ──► engine renders with Vulkan (wgpu-native)
     │                                  │                   ├─ NativeControlsView ──► NativeEngine.key/mouse (JNI)
     │                                  │                   └─ AAudio, saves in files/engine/userdata
     │                                  no
     │                                  └──► GameServerService (foreground service) ──► GameServer :8080 ──► Chrome Custom Tab
     └─ Settings, game-file verification, saves, radio, mods
```

- `engine/NativeEngine.java` – JNI to the engine: `start(...)`, `setSurface`, `key`, `mouseMove`, `mouseButton`, `wheel`,
  `setFocus`, `framesPresented`, `controlState`, `crashReason`.
- `NativeGameActivity.java` – builds the engine arguments from the settings (resolution = surface × scale, quality options), hands
  over the surface, routes Back/gamepad, shows loading/crash status. It runs in a separate process that ends when the game closes.
- `server/GameServer.java` – local HTTP server: range requests, `POST /data/batch`, ETag caching, COOP/COEP/CORP headers, the touch
  overlay injection.
- `settings/` – `GameSettings` (presets and options, launch arguments) and the full-screen settings UI.

## Building

Requirements: JDK 17, Android SDK 34.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Without an engine library the app builds and works in Chrome mode. For native mode, build `libgta5engine.so` with the native engine
toolchain; it is linked into `app/src/local/jniLibs/arm64-v8a/`.

### Local files (`app/src/local/`, not in git)

Both build types add `app/src/local/{res,assets,jniLibs}` on top of `main`, so files there replace those of the same name:

- `jniLibs/arm64-v8a/libgta5engine.so` – your engine build;
- `res/drawable/icon_*.png`, `assets/touch/buttons/icon_*.png`, `assets/touch/icons/*.png` – your own button artwork (the repository
  ships simple text placeholders with the same names);
- `assets/fonts/ChaletLondonNineteenSixty.ttf` – optional title font (the system font is used without it).

`app/src/main/jniLibs/arm64-v8a/libwgpu_native.so` is [wgpu-native](https://github.com/gfx-rs/wgpu-native) v29.0.1.1 (MIT/Apache-2.0).

## Requirements

- Android 8.0+ (API 26), arm64. Native mode needs Vulkan; tested on a Poco F6 (Snapdragon 8s Gen 3, 12 GB): ~25–35 FPS.
- About 3–4 GB of free RAM for the game in native mode.

## Disclaimer

Not affiliated with or endorsed by Rockstar Games or Take-Two Interactive. Grand Theft Auto and GTA V are trademarks of their owners.
This project is a launcher; it does not include or distribute the game.
