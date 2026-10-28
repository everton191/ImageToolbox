# S Pen drawing fork

This fork keeps the upstream Image Toolbox modules, including AI tools, neural
tools, assistant, OCR and background removal. The drawing change is optional and
uses standard Android stylus input; it does not require a Samsung SDK or root.

## Use

1. Open **Draw** and import an image or create a canvas.
2. Turn on **Only draw with a pen (S Pen)** / **Desenhar somente com caneta (S Pen)**.
3. Draw with the pen. Fingers pan the zoomed canvas; pinch or double tap to zoom.
4. Use the existing color picker for any RGB color and the existing eraser button
   to erase. Undo/redo and the other drawing tools remain available.
5. New canvases and imported PNG files start with lossless PNG selected. You can
   still choose another format. Export uses the original image dimensions, not
   the smaller on-screen preview. An explicitly selected opaque background or
   lossy format can intentionally remove transparency.

The option starts off, preserving the normal finger-drawing workflow. It survives
screen recreation within the current drawing screen. Enabling it exits the
manual pan-only tool. Pen strokes keep their original pointer identity; a resting
palm cannot take over a stroke when the pen lifts. Fingers must be lifted before
starting a new navigation gesture after drawing. The S Pen side button, pressure
and tilt do not introduce new brush behavior in this change.

## Build and tests on Windows

Use JDK 21, the existing Android SDK, and platform `platforms;android-37.2`.
The checked-in Gradle wrapper downloads Gradle 9.8.2. With PowerShell:

```powershell
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :feature:draw:testMarketDebugUnitTest :app:assembleMarketDebug --console=plain --max-workers=4 '-Dorg.gradle.jvmargs=-Xmx4g' '-Pkotlin.daemon.jvmargs=-Xmx4g'
```

Market debug preserves the upstream Google/ML Kit integrations as well as the AI
modules. The upstream debug application ID is
`ru.tech.imageresizershrinker.debug`, separate from the release application.

The repository centralizes device tests in `:instrumentation-tests`. Draw tests
under `feature/draw/src/androidTest` are included there. Run focused device tests
with that module's connected Market debug task and the Android instrumentation
runner class filter; check its task list for the exact task name.

## Physical acceptance checks

- With pen-only mode on, finger drags must add no strokes. Pinch, double tap and
  pan a zoomed canvas; then draw with the pen at the transformed location.
- Draw with the palm resting on the display, lift the pen first, then lift the
  palm. Confirm no extra line, jump, or unwanted transform.
- Toggle pen-only mode off and confirm normal finger drawing still works.
- Select an arbitrary RGB color, erase, undo and redo. Rotate the screen and
  verify the option and image remain usable.
- Import a large transparent PNG, draw and erase, export PNG, and independently
  check the saved dimensions and alpha. Explicitly choose PNG if another format
  was selected manually.
- Smoke-test the existing AI entry points separately. Some functions require
  model downloads; preserving their code is not a claim that all models were
  downloaded or exercised on the device.

Install only the built debug APK onto the selected authorized device using
`adb -s <serial> install -r <apk>`. Do not clear data or uninstall an existing app
to work around installation failures.
