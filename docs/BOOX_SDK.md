# Boox (Onyx) SDK

What the official Onyx Android SDK offers and which parts Kanshu should use. Researched from the official sample repo [onyx-intl/OnyxAndroidDemo](https://github.com/onyx-intl/OnyxAndroidDemo) (last commit 2026-06-29): all 18 `doc/` wiki pages plus the demo app source, which exercises several APIs the README never mentions (EAC, high contrast, screensaver). This is desk research — nothing here has been validated on the Go 7 Gen 2 yet. The Phase 0 EPD spike in `docs/PRD_NATIVE_READER.md` remains the on-device validation gate.

## Artifacts and Setup

The SDK ships as Maven artifacts from Onyx's own repository plus jitpack:

```gradle
repositories {
  maven { url "http://repo.boox.com/repository/maven-public/" } // note: http, needs allowInsecureProtocol
  maven { url "https://jitpack.io" }
}

dependencies {
  implementation("com.onyx.android.sdk:onyxsdk-device:1.3.5") // what the demo app actually uses
}
```

| Artifact | Contents | Kanshu relevance |
| --- | --- | --- |
| `onyxsdk-device` | `Device`, `EpdController`, `EpdDeviceManager`, EAC, brightness, contrast, screensaver, utils | **Yes — the one we'd depend on** |
| `onyxsdk-base` | Older superset of the same surface (EPD, scribble hooks, dictionary, touch regions) | Overlaps; docs reference it but demo uses `onyxsdk-device` |
| `onyxsdk-pen` | `TouchHelper` raw stylus input + `BrushRender` | No — Go 7 Gen 2 has no stylus |
| `onyxsdk-scribble` | Older pen SDK (EventBus + dbflow) | No |
| `onyxsdk-data` | OTA, WeChat/OSS upload, file downloader | No |

Version documentation is inconsistent: the README pins `onyxsdk-device:1.1.11` / `onyxsdk-pen:1.2.1`, `doc/Onyx-Base-SDK.md` says base 1.4.3.7 is latest, while the demo's own `build.gradle` uses `onyxsdk-device:1.3.5` / `onyxsdk-pen:1.5.4`. Treat the demo's `build.gradle` as ground truth and check the Maven repo index when we integrate.

Integration footguns observed in the demo app:

- The demo calls `HiddenApiBypass.addHiddenApiExemptions("")` on Android 11+ (`SampleApplication.java`) — parts of the SDK touch hidden platform APIs. We must verify on-device whether the refresh paths we need require this; needing it would be a mark against deep integration.
- `repo.boox.com` is plain HTTP; Gradle requires `isAllowInsecureProtocol = true` on that repo.
- The SDK is Java, pulls RxJava transitively, and the demo targets SDK 30. It predates our toolchain; expect lint noise and keep it quarantined in one module.
- Everything is Boox-firmware-specific. Any call must sit behind an interface with a no-op default so the app still runs on emulators and non-Boox devices.

## Capability Inventory

### 1. EPD refresh control — the reason we care

Three layers, from broadest to most surgical:

**App-scope refresh mode** (`Device.currentDevice()`, from `RefreshModeDemoActivity`): sets the systemwide refresh strategy while our app is foreground. `UpdateOption`: `NORMAL` (best quality, general reading), `REGAL` (minimal ghosting, light backgrounds), `FAST_QUALITY` (slight ghosting, skimming), `FAST` (scrolling), `FAST_X` (video/web, heavy detail loss).

```java
Device.currentDevice().setAppScopeRefreshMode(UpdateOption.NORMAL);
UpdateOption current = Device.currentDevice().getAppScopeRefreshMode();
```

**Per-view / per-invalidate mode** (`EpdController`): `UpdateMode` values — `DU` (1-bit black/white partial, fastest), `DU_QUALITY` (DU with dither), `GU` (16-level gray partial), `GC` (16-level gray **full** refresh, clears ghosting), `REGAL` (optimized 16-level partial for text pages), `ANIMATION` / `ANIMATION_QUALITY` (A2-style fast black/white).

```java
EpdController.setViewDefaultUpdateMode(view, UpdateMode.REGAL); // default mode for a view
EpdController.invalidate(view, UpdateMode.GC);                  // one-shot full refresh
EpdController.repaintEveryThing(UpdateMode.GC);                 // whole-screen repaint
EpdController.applyAppScopeUpdate(TAG, true, true, UpdateMode.DU_QUALITY, Integer.MAX_VALUE);
EpdController.clearAppScopeUpdate();
```

**Managed wrapper** (`EpdDeviceManager`): the pattern e-readers actually want — partial updates with an automatic full refresh every N updates:

```java
EpdDeviceManager.setGcInterval(5);                        // GC after every 5 partial updates
EpdDeviceManager.applyWithGCIntervalWitRegal(view, true); // regal partial, GC when interval hits
EpdDeviceManager.applyWithGCIntervalWithoutRegal(view);   // plain partial variant
EpdDeviceManager.enterAnimationUpdate(true);              // enter fast B/W mode
EpdDeviceManager.exitAnimationUpdate(true);               // leave it
```

This maps one-to-one onto the classic reader behavior: REGAL/GU partial update per page turn, full GC refresh every N pages (user-configurable, Kindle-style), GC on chapter change and overlay dismissal to wipe ghosting.

### 2. EAC — the system optimizer that can fight us

`SimpleEACManage` (from `EacDemoActivity`, undocumented in the wiki) controls Onyx's E-ink App Compatibility layer — the same per-app "optimization" users reach via long-press on an app icon. Key calls: `setSupportEAC(context, boolean)`, `setAppEACEnable(context, boolean)`, `isAppEACEnabled(pkg)`, `isHookEpdc(pkg)`, `setEACRefreshConfigEnable(...)` (firmware 3.2+), `setFollowSystemRotation(...)` (3.3.1+). All are called off the main thread in the demo.

This matters defensively: EAC can hook the EPD controller (`isHookEpdc`) and apply its own contrast/refresh policy on top of ours. A reader that manages its own refresh probably wants to detect and steer EAC state rather than let the system second-guess page turns. What NeoReader does here is worth inspecting on-device during the spike.

### 3. Front light

`BrightnessController` (`com.onyx.android.sdk.api.device.brightness`) with `getBrightnessProvider()` / `getBrightnessType()`; the demo's `FrontLightFactory` picks between plain front-light, warm+cold dual-temperature, and CTM models depending on device. Requires `WRITE_SETTINGS` permission. The Go 7 Gen 2 has a warm/cold front light, so a brightness slider in the reader overlay is feasible without leaving the app.

### 4. High contrast

`GlobalContrastController.setHighContrastEnabled(boolean)` / `isHighContrastEnabled()` (from `BooxSettingsDemoActivity`) toggles the system dark-text enhancement. Demo warns not to call during early app lifecycle (use `View.post`). Marginal for us — we render pure black on white already — but relevant if grayscale covers or anti-aliased text look washed out.

### 5. Dictionary

`DictionaryUtils.queryKeyWord(context, keyword)` (base SDK 1.4.3.7+) queries the device's installed dictionaries and returns per-dictionary explanations. Blocking call — must run off the main thread; `explanation` may be null even on success. This is a direct hit for Phase 3 word selection ("long-press word → definition popup") and the Mandarin-learning north star in `docs/PRD.md`, using dictionaries the user already installed on the device instead of bundling our own.

### 6. Misc utilities

- `DeviceUtils.setFullScreenOnResume(activity, boolean)` / `isFullScreen()` — Onyx-sanctioned immersive mode toggle, works on all their devices. Cross-check against our current insets-based immersive reader.
- `ScreenResourceManager.setScreensaver(context, imagePath, true)` (+ `setWallpaper`, `setShutdown`, with `supportWallpaperSetting()` capability checks) — programmatic sleep-screen image. Enables a beloved e-reader feature: current book cover as the sleep screen.
- `EpdController.setAppCTPDisableRegion(context, Rect[])` / `appResetCTPDisableRegion` — disable touch input in screen regions (base 1.4.3+). We don't need it (Compose tap zones already partition the screen), but it exists for accidental-touch hardening.
- `DeviceEnvironment.getRemovableSDCardDirectory()` — removable storage path; irrelevant until local-folder sources.
- NeoReader integration (`ReaderDemoActivity`): open a file in NeoReader via intent, query its reading progress through `content://com.onyx.content.database.ContentProvider/Metadata`. Not useful — Kanshu is the reader.
- `EpdController.setWebViewContrastOptimize(webView, boolean)` — WebView anti-A2 helper; obsolete for us since the native engine removed WebView.
- Onyx settings deep links (`onyx.settings.action.network` etc.) — could link "open Wi-Fi settings" from the Kavita connection screen on failures.

### 7. Pen / scribble SDK — not applicable

`onyxsdk-pen`'s `TouchHelper` gives raw low-latency stylus strokes with pressure (`RawInputCallback`, `BrushRender`). The Go 7 Gen 2 B&W has no stylus digitizer. Ignore unless a future device changes the picture.

## What Kanshu Should Use, By Phase

| Kanshu need | SDK surface | When (per `PRD_NATIVE_READER.md`) |
| --- | --- | --- |
| Validate refresh control exists on Go 7 Gen 2 | `Device.currentDevice()`, `EpdController`, `EpdDeviceManager` smoke test | **Phase 0 spike (required deliverable)** |
| Page turns: partial refresh + full GC every N pages | `EpdDeviceManager.setGcInterval` + `applyWithGCIntervalWitRegal`, or manual `setViewDefaultUpdateMode(REGAL)` + counted `invalidate(GC)` | Phase 2 baseline EPD |
| Clear ghosting on chapter change / overlay dismiss / settings apply | `EpdController.invalidate(view, UpdateMode.GC)` | Phase 2 |
| Keep system optimizer from fighting our refresh | `SimpleEACManage` state detection | Phase 2 (investigate in Phase 0 spike) |
| Word definition popup; Mandarin dictionary lookup | `DictionaryUtils.queryKeyWord` | Phase 3 selection actions |
| Fast skim / page-scrubbing mode | `EpdDeviceManager.enterAnimationUpdate` (DU/A2) | Phase 5 |
| Brightness slider in reader overlay | `BrightnessController` | Later (Phase 5 / polish) |
| Book cover as sleep screen | `ScreenResourceManager.setScreensaver` | Later, delightful |
| Wi-Fi settings deep link from connection errors | Onyx settings intents | Later, cheap |

### Open questions for the Phase 0 spike

1. Does our Compose `Canvas` page surface respond to `EpdController.setViewDefaultUpdateMode` on the view hosting it, or do we need `applyAppScopeUpdate`? The demos all use classic Views.
2. Do the refresh APIs work without the hidden-API bypass on the Go 7 Gen 2's firmware (Android 12)?
3. What does EAC do to Kanshu out of the box (`isAppEACEnabled`, `isHookEpdc`), and does default EAC behavior already give acceptable page turns with zero SDK code?
4. Which `UpdateMode` does a full-page text swap actually produce by default — i.e., how bad is the baseline we'd be improving on?

### Integration shape

Keep the SDK behind the `EinkPageTurner`-style abstraction already named in `PRD_NATIVE_READER.md`: a small interface owned by Kanshu (`requestPageTurnRefresh()`, `requestFullRefresh()`, `setSkimMode(Boolean)`), with a Boox implementation wrapping `EpdDeviceManager`/`EpdController` and a no-op default. Only that implementation module depends on `onyxsdk-device`, so the http Maven repo, RxJava baggage, and any hidden-API concerns stay quarantined and the app remains runnable on emulators. `:reader-navigator` stays SDK-free — refresh orchestration is a feature-layer concern triggered around page-turn events, not a rendering-engine concern.
