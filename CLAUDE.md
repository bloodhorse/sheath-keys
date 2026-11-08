# sheath-keys

the keyboard for sheath (`~/tower/forge/sheath`), bekh's Android shell. a fork of Thumb-Key
(upstream `dessalines/thumb-key`, AGPL; ours is `bloodhorse/sheath-keys`, `origin`). never push upstream.

the deal: Thumb-Key's engine stays exactly as it is (swipe recognition, layouts, text processing,
settings); only the look is ours. the bar is Wurstfinger, the iOS MessagEase keyboard bekh uses, made
darker: a heavy dark frost panel with rounded corners, keys of faint glass with a hairline edge on it,
the big letter white, the flicks grey, symbols dimmer, nothing moves. the brief and the why live in
`~/tower/forge/sheath/docs/input.md`, "the keyboard for messages".

## what we changed

mostly defaults, no new settings; a user can still pick any upstream theme or layout.

- **the panel** (`IMEService.dressPanel`): the IME window is exactly the keyboard's rectangle and
  already translucent, so its background is the panel: a `GradientDrawable` (radius 22 dp, `#101012`
  at 80%) plus `Window.setBackgroundBlurRadius` (40 dp). Android rounds the blur to the drawable's
  outline, so the drawable goes on before the blur. if cross-window blur is off (battery saver, older
  Android) the tint goes to 95% instead, via `addCrossWindowBlurEnabledListener`. the keys sit 6 dp
  inside it (`PANEL_INSET_DP` in `Utils.kt`: view padding, and subtracted in `getAutoKeyWidth`).
  only the top corners are round, the bottom edge is flush with the screen's: the blur rounds all four
  corners alike or none (a top-only outline is a path, and the blur goes square and shows unrounded
  past the top corners), so the window hangs one radius below the screen (`FLAG_LAYOUT_NO_LIMITS`,
  `y = -radius`), its rounded bottom corners off it, and the view gets that radius back as padding.
- **the system's IME nav buttons** (back chevron, keyboard switcher) used to hang over the space bar
  and return; on API 35+ the IME hides its caption bar (`insetsController.hide(captionBar())`) and
  Android drops them.
- **tones** (`ui/theme/Color.kt` → `sheath()`, `ThemeColor.Sheath`, ordinal 13; ordinals are stored in
  the db, so new entries only go at the end): tiles white at 6%, pressed 18%, hairline outline white at
  13% (`DEFAULT_KEY_BORDER_WIDTH` 4 = 0.4 dp), flicks `#B5B5B7`, symbols the same at half alpha
  (`MUTED`). measured off Wurstfinger: panel `#22`, tile `#2D`, outline `#42`, flicks `#B5`, symbols `#6C`.
- **sizes** (`fontSizeVariantToFontSize`): the big letter is the key / 2.9 (was 2.5), flicks / 4 (was
  5), Wurstfinger's proportions. big letter Medium weight, the rest Normal (upstream bolds everything).
- **no press animation**: upstream flashed the key and dropped the typed letter down it; both
  `AnimatedVisibility` blocks are gone from `KeyboardKey`. the pressed tint stays.
- **`keyboards/CommonKeys.kt`** (shared by every layout): `EMOJI_KEY_ITEM` keeps its name but is the
  globe now: tap cycles the chosen layouts, swipe down hides, swipe up opens settings. no emoji key, no
  drawer of nine icons. the `123` / `abc` key is text, with four edit flicks (select all, copy, cut,
  paste; undo and redo gone). controller centers are white.
- **`db/AppDb.kt`** defaults: theme color Sheath, layout 16 (`ENMessagEaseSymbols`: classic
  `a n i / h o r / t e s` with every direction filled; 8-way on every key, less forgiving than plain
  MessagEase, and that call is bekh's), key padding 3 dp, radius 22 (% of key), backdrop off (the
  panel is the window's), bottom inset ignored so the keys run to the screen's edge.
- **`app/build.gradle.kts`**: `applicationId` is `com.bekh.sheath.keys` (debug adds `.debug`); the
  Kotlin namespace stays `com.dessalines.thumbkey` so upstream merges stay painless.

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
on nothing1 LatinIME and the F-Droid Thumb-Key are disabled (`ime disable`), so ours is the only one.

**judging the frost:** `tools/frosttest/build.sh` builds and installs a 30-line app (no gradle) with
a loud gradient and big sharp text that stays put under the keyboard (`adjustNothing`);
`adb shell am start -n com.bekh.frosttest/.Main` for colour, add `--ez black true` for pure black.
`shots/` holds the current look over it: `letters.png`, `numeric.png`, `black.png`.

## gotchas

- the frost shows only over apps that draw under the keyboard. most Android apps are `adjust=resize`:
  they shrink above it and leave their flat window background behind the panel (the webview shell and
  Messaging paint white there, and a 80% dark tint over white is a grey slab). judge it in frosttest.
  `dumpsys SurfaceFlinger | grep blurRegions` proves the blur is on even when there's nothing to see.
- the wrapper is needed: AGP 9.5 wants gradle 9.9, brew's gradle 8 can't build this. the first run
  downloads it and installs SDK platform 37 by itself.
- the defaults are SQL column defaults (Room's `@ColumnInfo(defaultValue)`), so they only apply to a
  fresh database: a changed default doesn't show until `adb shell pm clear com.bekh.sheath.keys.debug`
  (then `ime enable` / `ime set` again).
- a debug build cold-starts slowly (ART verifies the 200 layout classes for seconds), and the first tap
  on a field after an install or a fresh launch often doesn't raise it at all. `mInputShown=true` also
  flips long before anything is drawn, and a tap aimed at a key then lands on the app. wait for the
  window, and tap again if it never comes:
  `until adb shell dumpsys window InputMethod | grep -q isOnScreen=true; do sleep 1; done`.
- `adb shell am start -d 'data:text/html;base64,...'`: the remote shell splits at the `;` unless the
  whole command is quoted for the far side.
