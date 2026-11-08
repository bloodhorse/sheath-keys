# sheath-keys

the keyboard for sheath (`~/tower/forge/sheath`), bekh's Android shell. a fork of Thumb-Key
(upstream `dessalines/thumb-key`, AGPL; ours is `bloodhorse/sheath-keys`, `origin`). never push upstream.

the deal: Thumb-Key's engine stays exactly as it is (swipe recognition, layouts, text processing,
settings); only the look is ours. the bar is Wurstfinger, the iOS MessagEase keyboard bekh uses, made
darker: pure black like the launcher, tiles barely off black, white big letter, dim flicks, no tint.
the brief and the why live in `~/tower/forge/sheath/docs/input.md`, "the keyboard for messages".

## what we changed

all of it is defaults, no new settings; a user can still pick any upstream theme or layout.

- `ui/theme/Color.kt` → `sheath()`: one black palette for light and dark. tiles `#121212`, pressed
  `#2C2C2C`, letters white, flicks `#9A9A9A`, symbols the same at half alpha (Thumb-Key's `MUTED`).
  added as `ThemeColor.Sheath` at the end of the enum (ordinal 13; ordinals are stored in the db, so
  new entries only ever go at the end).
- `db/AppDb.kt` defaults: theme color Sheath, layout 16 (`ENMessagEaseSymbols`: classic
  `a n i / h o r / t e s` with every direction filled, symbols included), black backdrop on, key padding
  3 dp (a 6 dp gap between tiles), no border, radius 22 (a percent of the key, ≈11 dp), bottom inset
  ignored so the keys run to the screen's edge.
- `KeyboardScreen.kt`: the 1 dp "pretty line" over the backdrop is gone.
- `KeyboardKey.kt`: the big letter is Medium weight, everything else Normal (upstream bolds all of it).
- `app/build.gradle.kts`: `applicationId` is `com.bekh.sheath.keys` (debug adds `.debug`); the Kotlin
  namespace stays `com.dessalines.thumbkey` so upstream merges stay painless.

## build and install

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug -x lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable com.bekh.sheath.keys.debug/com.dessalines.thumbkey.IMEService
adb shell ime set com.bekh.sheath.keys.debug/com.dessalines.thumbkey.IMEService
```

the emulator, and how to check the keyboard by script, is in `~/tower/forge/sheath/docs/build.md`.

## gotchas

- the wrapper is needed: AGP 9.5 wants gradle 9.9, brew's gradle 8 can't build this. the first run
  downloads it and installs SDK platform 37 by itself.
- the defaults are SQL column defaults (Room's `@ColumnInfo(defaultValue)`), so they only apply to a
  fresh database. changing one changes Room's schema hash: an installed build then crashes on open with
  "Room cannot verify the data integrity". `adb shell pm clear com.bekh.sheath.keys.debug` and re-set
  the IME. the exported schema in `app/schemas/` changes with it; commit that too.
