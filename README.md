# Artify

A professional drawing and animation studio for Android phones and tablets,
built around a **truly infinite canvas**: pan forever and zoom across
14 orders of magnitude with strokes that stay sharp at any depth.

## Features

**Infinite canvas**
- Strokes are stored as recorded pen input, not pixels, and re-rendered at
  whatever zoom you are viewing. Zoom from 0.0001% to 10,000,000,000%.
- Double-precision world coordinates with per-stroke local origins.
- Pinch to zoom and rotate (snaps to 90°), dot grid that adapts to zoom,
  "fit artwork" view.

**Brush engine**
- Stamp-based engine with pressure → size / opacity, taper, streamline
  stabilisation, scatter, size / flow / rotation jitter, follow-stroke, angle
  and roundness.
- 25 built-in brushes across sketching, inking, painting, airbrushing,
  artistic, calligraphy and erasers. Every brush is editable and any brush can
  be an eraser.
- Low latency: unbuffered stylus input, all batched samples consumed, and
  motion prediction (`androidx.input`) to hide display lag.
- QuickShape: draw and hold to snap to a line, circle or ellipse.
- Symmetry: vertical, horizontal, quadrant and 6-way radial.

**Layers**
- Up to 200 layers with opacity, 17 blend modes, alpha lock, lock, hide,
  rename, duplicate, merge down, reorder and clear.
- Lasso selection with move, scale, rotate, flip, duplicate and delete.

**Color**
- HSV picker, hex input, recent colors, editable saved palette, eyedropper
  (touch and hold, or the stylus button).

**Animation**
- Animation Assist: every layer is a frame, with onion skins, playback at
  1–30 fps, and MP4 export.
- Timelapse replay of any artwork as an MP4.

**Files and export**
- Continuous autosave with crash-safe atomic writes.
- PNG, JPEG and transparent PNG export up to 8192 px, current-view export,
  share sheet, and the native `.artify` project format for sharing and import.

**Phones, tablets and keyboards**
- Adaptive layout for phones, foldables and tablets; left-handed mode.
- Gestures: two-finger tap undo, three-finger tap redo, four-finger tap hides
  the UI. Pen-only mode lets fingers navigate while the stylus draws, with palm
  rejection while the pen is down.
- Keyboard shortcuts on tablets, DeX and Chromebooks.

## Privacy and security

Artify requests **no permissions** and has no network access. See
[SECURITY.md](SECURITY.md).

## Building

Requirements: JDK 17 and the Android SDK (API 36).

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Before your first commit, install the secret-scanning hook:

```bash
python3 security/check_secrets.py --install-hook
```

Release signing is described in [docs/RELEASING.md](docs/RELEASING.md).

## Roadmap

See [docs/PLAN.md](docs/PLAN.md). Next up: a native C++/Vulkan engine for
pixel tools (smudge, liquify, wet mixing, flood fill), PSD import and export,
and a multi-track animation timeline.

## Project layout

```
app/src/main/java/io/github/houssam2021/artify/
  model/    documents, layers, strokes, brushes, camera (pure Kotlin)
  engine/   stamp generator, brush tips, renderer, shape detection
  canvas/   CanvasView: input, gestures, caching, live strokes
  editor/   EditorController: undo, layers, selection, animation, export
  io/       .artify codec, atomic storage, image and video export
  ui/       Compose screens and panels
security/   secret and signing-material scanner (CI + pre-commit)
```
