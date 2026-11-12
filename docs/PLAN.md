# ProjectX — Professional Drawing & Animation Studio for Android

**Plan version:** 1.0 · **Date:** 2026-11-12 · **Working name:** ProjectX (final name TBD)

A Procreate-class painting app with a true infinite canvas (unbounded pan and zoom) and built-in animation, designed for Android phones and tablets from day one.

---

## 1. What the research says

### 1.1 The iPad benchmark

| App | What it is known for | Model |
|---|---|---|
| **Procreate** | The default iPad painting app. Custom Metal engine ("Valkyrie"), up to 120 fps, Brush Studio, Photoshop brush import, gesture-first UI, timelapse replay, Animation Assist | One-time purchase (~$12.99 per reviews) |
| **Procreate Dreams 2** | Animation: multi-track Flipbook, three timeline modes (Compose / Keyframes / Perform), record motion by touch, audio tracks, selection + warp inside frames, alpha video, GIF export with dithering | One-time purchase (~$19.99 per reviews) |
| **Concepts** | Vector strokes on an infinite canvas; every stroke stays editable and movable | Subscription / unlocks |
| **Endless Paper** | Infinite *zoom* — vector strokes stay crisp at any depth; custom 64-bit Metal vector engine | Paid |
| **ToonSquid, RoughAnimator, FlipaClip** | Dedicated 2D animation tools | Mixed |
| **Clip Studio Paint** | Comics/illustration powerhouse, desktop-grade but desktop-style UI | Subscription |

What makes these apps feel "insane":

1. **Latency and feel.** The stroke appears under the pen instantly and the brush responds to pressure, tilt and speed with no stutter. This matters more than any feature list.
2. **A deep brush engine.** Shape + grain textures, dozens of dynamics, wet mixing, smudge, dual brushes, and a brush editor users can share from.
3. **A gesture-first, uncluttered UI.** Two-finger tap undo, pinch/rotate canvas, hold-to-snap shapes, colour picking by long-press.
4. **Never losing work.** Continuous autosave, deep undo, timelapse.
5. **Fair pricing.** Procreate's one-time price is a major reason for its loyalty.

### 1.2 The Android gap

Procreate is Apple-only. The Android field today:

| App | Strength | Weakness we can exploit |
|---|---|---|
| Infinite Painter | Closest to Procreate; one-time unlock | Fixed canvas, no real animation suite |
| Clip Studio Paint | Most powerful | Subscription, desktop UI crammed onto touch |
| ibis Paint X | Huge user base, works on phones | Ads / subscription, cluttered |
| Krita | Free, professional | Tablet-only, desktop UI, heavy |
| Sketchbook, MediBang, HiPaint | Simple, light | Shallow brush engines |
| Concepts | Infinite vector canvas | Not a painting app; subscription |
| FlipaClip | Animation on phones | Weak drawing tools |

**No Android app combines** Procreate-grade painting feel, a true infinite-zoom canvas, integrated animation, a real phone UI, and a one-time price. That is the product.

### 1.3 Platform facts that shape the plan

- **Low-latency inking is now a solved platform problem.** Jetpack `androidx.graphics` provides front-buffered rendering (draw the newest stroke segment straight to the display) and there is a motion-prediction library; `androidx.ink` is stable at 1.0.0 (Dec 2025) with 1.1 alphas adding custom brush families, particle brushes and a pixel eraser.
- **`androidx.ink` is not enough for a painting engine.** It produces vector mesh strokes — ideal for notes and highlighters, not for wet mixing, smudging or grain-textured stamps. We reuse its *techniques* (and possibly its input modelling), not its renderer.
- **Vulkan is the future of Android graphics.** Devices launching with Android 13+ must support Vulkan 1.3, Android 16+ Vulkan 1.4, and new chipsets are moving OpenGL ES onto ANGLE (GLES emulated over Vulkan). Build Vulkan-first.
- **Stylus hardware is fragmented.** Samsung S Pen (Wacom EMR) is the best-in-class; Xiaomi, OnePlus, Lenovo and others ship proprietary pens; USI 2.0 exists on some devices. Tilt, pressure curves and report rates differ per device — the app needs per-device calibration.

---

## 2. Product definition

### 2.1 Positioning

> The professional art studio Android never got: paint, zoom forever, animate — on the device already in your pocket.

### 2.2 Target users

1. **Illustrators and concept artists** on Galaxy Tab S / flagship tablets (the "I'd buy an iPad just for Procreate" crowd).
2. **Hobbyists and students on phones**, including mid-range devices — the largest Android audience.
3. **Animators and content creators** making short loops, animatics and social clips.

### 2.3 Pillars (in priority order)

1. **Feel** — perceived pen latency on par with the best Android apps, steady frame pacing at the display's refresh rate.
2. **Infinite canvas** — unbounded pan *and* zoom with crisp strokes at any depth.
3. **Brush depth** — an engine and editor on the level of Procreate's Brush Studio.
4. **Phone = first-class** — the full feature set on a 6" screen, not a cut-down "pocket" edition.
5. **Animation built in** — from flipbook to keyframed multi-track timeline.
6. **Trust** — never lose a stroke; one-time price; files stay local and open.

### 2.4 Non-goals for 1.0

iOS/desktop ports, cloud collaboration, 3D model painting, CMYK print pipeline, generative AI features (see open decisions).

---

## 3. Feature scope by release

### MVP — closed alpha (prove the feel)

- Canvas: pan / zoom / rotate, fixed-size documents, infinite mode prototype
- Brush engine v1: stamp-based, shape + grain, pressure/tilt/velocity dynamics, stabilisation, eraser, smudge
- ~25 curated brushes (sketch, ink, paint, airbrush)
- Layers: add/merge/reorder, opacity, 12 core blend modes, alpha lock, clipping mask
- Colour: disc + HSB sliders, eyedropper, palettes, recent colours
- Undo/redo (deep), continuous autosave, crash recovery
- Gestures: two-finger undo, three-finger redo, hold-to-pick colour
- Gallery, PNG/JPEG export
- Low-latency stylus path + finger painting

### 1.0 — public launch (professional painting)

- **Infinite canvas** fully shipped (see §5)
- Brush Studio: full editor, dual brush, wet mix, colour dynamics, taper, import `.abr`; share/export our own brush format
- ~150 brushes across 12+ sets
- Selections (lasso, rectangle, ellipse, magic wand, colour range), feathering
- Transform (free, distort, warp/mesh), liquify
- Layer masks, groups, all 27 standard blend modes, reference layer
- Adjustments & filters: curves, HSB, colour balance, Gaussian/motion blur, sharpen, noise, gradient map, bloom, chromatic aberration
- Drawing aids: QuickShape snapping, perspective (1/2/3-point), isometric, symmetry (vertical/quadrant/radial), rulers
- Fill with threshold drag, gradient tool, text tool (basic)
- Colour harmony, palettes from image, wide-gamut (Display P3) documents
- Timelapse recording → MP4 export
- Import/export: PSD (layers), PNG, JPEG, WebP, TIFF, PDF; share-sheet integration
- Reference image window, split-screen and drag-and-drop support
- Keyboard shortcuts, DeX / desktop windowing, foldable layouts

### 1.5 — animation (flipbook)

- Frame-by-frame with onion skinning, frame hold, per-frame layers
- Simple timeline, playback at 1–60 fps, audio scrub track
- Export MP4, GIF, animated WebP, PNG sequence

### 2.0 — motion studio (Dreams-class)

- Multi-track timeline: drawing tracks, image/video tracks, audio tracks
- Keyframes for transform / opacity / filters with easing curves
- "Perform": record motion by dragging in real time
- Groups, masks and nested flipbooks on the timeline
- Transparent export (WebM/VP9 alpha, PNG sequence), 4K render

### Later

Vector layers with editable paths, comic tools (panels, speech bubbles, tones), cloud sync and backup, brush marketplace, ChromeOS/desktop, iOS.

---

## 4. Technical architecture

### 4.1 Stack (recommended)

| Layer | Choice | Why |
|---|---|---|
| UI shell | **Kotlin + Jetpack Compose** | Adaptive layouts for phone/tablet/foldable, fast iteration |
| Canvas surface | `SurfaceView` + `SurfaceControl`, owned by native code | Lowest-latency path, front-buffer capable |
| Engine | **C++20** shared library (`libpx_engine`) via JNI | No GC pauses, SIMD, full control over memory; same choice as Krita / ibis / Infinite Painter |
| GPU | **Vulkan 1.1 baseline** behind a thin render-hardware interface; **GLES 3.1 fallback** for devices with broken Vulkan drivers | Vulkan is where Android is going; fallback protects reach |
| Shaders | GLSL → SPIR-V (glslang), cross-compiled to GLES with SPIRV-Cross | One shader source |
| Document store | **SQLite** container + zstd-compressed tile blobs | Transactional autosave, crash-safe, partial loading |
| Colour | Linear-light compositing, 8-bit or 16-bit-float per channel, lcms2 for ICC | Correct blending, wide gamut |
| Video | MediaCodec / MediaMuxer | Hardware encode for timelapse and animation export |
| Build | Gradle + CMake + NDK, GitHub Actions CI | Standard |

`minSdk 29` (Android 10): required for front-buffered rendering and gives a Vulkan 1.1 floor on 64-bit devices. `arm64-v8a` primary, `x86_64` for emulators/ChromeOS.

### 4.2 Module map

```
app/                 Compose UI, gallery, panels, gestures, settings
canvas-view/         Surface host, input capture, JNI bridge
engine/  (C++)
  core/              math, memory pools, job system, logging
  rhi/               Vulkan + GLES backends
  tiles/             sparse tile store, GPU tile atlas, LRU, disk swap
  brush/             stamp generator, dynamics, wet mix, smudge, brush format
  stroke/            input filtering, stabilisation, prediction, replay
  compositor/        layer tree, blend modes, masks, adjustment passes
  infinite/          deep-zoom coordinates, LOD pyramid, stroke index
  history/           undo journal (tile diffs + op log)
  document/          SQLite schema, autosave, migration
  io/                PSD, PNG/JPEG/WebP/TIFF, PDF, ABR, video
  anim/              timeline, keyframes, playback, render queue
tools/               brush authoring, golden-image test runner, benchmarks
```

### 4.3 Input and latency pipeline

1. Receive `MotionEvent` on a dedicated thread; consume **all historical (batched) samples** — pens report at 240 Hz+ while the display runs at 60–144 Hz.
2. Request unbuffered dispatch so samples are not held until vsync.
3. Run the **motion predictor** to extend the stroke 1–2 frames ahead; predicted points are drawn only in the temporary front layer and discarded each frame.
4. Draw the new segment to the **front buffer** (single-buffered layer above the canvas) immediately; commit to the real tiles and normal double-buffered path on the next frame.
5. Palm rejection: ignore touch while a pen is in proximity; honour `FLAG_CANCELED`; user-selectable "pen only draws" mode.
6. Per-device **pressure curve calibration** and tilt/azimuth availability detection; finger input simulates pressure from velocity and contact size.

**Targets:** stroke segment visible within one display frame of the input sample on flagship hardware; 0 dropped frames during a continuous stroke on the reference devices; measured with a 960 fps camera rig, not guessed.

### 4.4 Tile engine and memory

- Layers are **sparse grids of 256×256 tiles**; empty tiles cost nothing.
- Tiles live in GPU texture atlases; an **LRU** evicts cold tiles to compressed RAM, then to disk.
- **Undo = copy-on-write tile snapshots** per stroke (only touched tiles), spilled to disk; hundreds of undo steps without holding them in RAM.
- Memory budget set from the device's memory class at launch; maximum layer count is computed and shown to the user (as Procreate does) instead of crashing.
- Compositing caches the flattened result below and above the active layer so painting cost is independent of layer count.

### 4.5 Brush engine

- **Stamp model:** a stroke is a sequence of textured stamps spaced along a smoothed path; stamps are GPU-instanced in batches.
- **Per-stamp attributes** driven by curves: size, opacity, flow, rotation, scatter, squish, hue/sat/brightness jitter — mapped from pressure, tilt, azimuth, barrel rotation, velocity, stroke distance, random.
- **Stroke buffer:** stamps accumulate into a per-stroke buffer, then composite to the layer — gives correct "glaze" vs "build-up" opacity.
- **Grain:** moving (rolls with the stroke) or texturised (fixed to canvas), with scale/depth/blend controls.
- **Wet mix:** brush carries a colour charge; picks up canvas colour, dilutes, and runs out. Pigment-style mixing via an in-house spectral mix (blue + yellow = green, not grey).
- **Smudge** shares the wet-mix sampler.
- **Stabilisation:** streamline (exponential), pulled-string, and motion filtering; taper in/out.
- **Brush format:** versioned, documented, zip-based (`.pxbrush`) — JSON parameters + PNG textures. Import Photoshop `.abr`.
- **Determinism:** same inputs + same seed = same pixels. Required for timelapse replay, infinite-canvas re-rendering, and golden-image tests.

---

## 5. Infinite canvas and infinite zoom — the hard part

Pixel painting apps (Procreate) have fixed resolution. Infinite-zoom apps (Concepts, Endless Paper) are vector-only and cannot do painterly effects. We want both, so the design is a **hybrid**.

### 5.1 Two document types, one engine

| | **Fixed canvas** | **Infinite canvas** |
|---|---|---|
| Size | Chosen at creation (up to device limit) | Unbounded pan and zoom |
| Source of truth | Pixels (tiles) | **Stroke log** — tiles are only a cache |
| All pixel tools (liquify, blur, smudge) | Yes | Yes, with a "baked at this zoom" rule (§5.4) |
| Best for | Print, finished illustration | Sketching, world-building, zoom art, boards |

### 5.2 Coordinates without precision loss

32-bit floats break down after ~10⁷:1 zoom. Instead:

- Space is a **sparse quadtree**. A position is *(node address, small local offset)*; the node address uses 64-bit integers per level plus a level index.
- The camera is anchored to a node and **re-anchors** as you zoom or pan (a "floating origin"), so the GPU only ever sees small, precise numbers.
- Result: zoom depth is limited by storage, not by arithmetic.

### 5.3 Rendering

- Every stroke is stored as its recorded input samples + brush snapshot + seed, anchored to the quadtree level where it was drawn.
- The screen is filled from a **tile pyramid**: the level of detail matching the current zoom. Missing tiles are produced by **replaying the strokes that intersect them** (found through a spatial index) on background threads; coarser levels are downsampled.
- Rendered tiles are **cached persistently on disk**, so each tile at each level is computed once. Edits invalidate only the tiles they touch.
- Because stamp spacing scales with brush size, the number of stamps touching any tile stays bounded no matter how deep the zoom.
- While tiles are still rendering, the coarser level is shown upscaled, then sharpens — like a map app.
- Strokes far smaller than a pixel at the current zoom are culled and represented by the downsampled level.

### 5.4 Pixel tools on an infinite canvas

Brush and eraser strokes are resolution-independent. Operations that are inherently pixel-based (smudge, liquify, blur, flood fill) are recorded in the op log with the zoom level they were applied at and **baked at that level**: crisp at that zoom and shallower, softened if you zoom far past it. The UI states this plainly. Users can also explicitly rasterise a region.

### 5.5 Risk control

This is the highest-risk system in the project. It gets a **dedicated prototype in Phase 0** with a go/no-go gate: 10,000-stroke document, zoom across 10¹² range, tiles resolve within 150 ms on the mid-range reference device. If it fails, 1.0 ships infinite *pan* with a deep-but-finite zoom, and unbounded zoom moves to a later release.

---

## 6. UX plan — phones and tablets

### 6.1 Principles

- The canvas is the app. Chrome hides while drawing; a four-finger tap toggles full screen.
- Everything reachable in two taps; everything undoable.
- Same features on every form factor — layout adapts, capability does not.

### 6.2 Adaptive layouts

| Size class | Layout |
|---|---|
| **Compact (phones)** | Slim top bar; collapsible side sliders for size/opacity placed for thumb reach (left- or right-handed); panels open as bottom sheets; one-handed colour picker; brush/eraser/smudge quick toggle |
| **Medium (foldables, small tablets)** | Top bar + floating popovers; layers panel can pin to one side |
| **Expanded (tablets, DeX, desktop windows)** | Popovers plus optional docked panels; keyboard shortcuts; hover preview of brush cursor; stylus button mapping |

### 6.3 Gestures

Two-finger tap undo · three-finger tap redo · pinch zoom/rotate · quick-pinch fit-to-screen · long-press eyedropper · draw-and-hold shape snapping · three-finger swipe for cut/copy/paste · two-finger hold for a perfect straight line. All remappable.

### 6.4 Onboarding

No tutorial wall. A blank canvas within two taps of first launch, an interactive gesture cheat-sheet, and sample artworks with their timelapses.

---

## 7. File format and interoperability

- **Native `.pxd`:** a single SQLite file — layer tree, tile blobs (zstd), stroke/op log, thumbnails, timelapse segments, metadata. Written transactionally: a crash or kill never corrupts a document.
- **Autosave** after every stroke (journal append), full checkpoint in the background.
- **Import:** PSD, PNG, JPEG, WebP, TIFF, HEIF, PDF pages, `.abr` brushes, palettes (`.aco`, `.ase`, `.swatches`).
- **Export:** PSD with layers, PNG, JPEG, WebP, TIFF, PDF, MP4 timelapse; animation as MP4, GIF, animated WebP, WebM with alpha, PNG sequence.
- Storage Access Framework for user-chosen folders; optional backup to the user's own cloud drive.
- The format specification is published — users own their work.

---

## 8. Roadmap

Estimates assume a core team of **4–6** (see §10). A solo developer should expect roughly 2–3× the durations and should cut 1.0 scope to the MVP plus infinite canvas.

| Phase | Duration | Deliverable | Exit criteria |
|---|---|---|---|
| **0. Foundations & prototypes** | 8 weeks | Three throwaway prototypes: (a) low-latency stroke on Vulkan surface, (b) tile engine with 100 layers, (c) infinite-zoom stroke replay | Latency within one frame on reference flagship; 60 fps pan/zoom on mid-range; infinite-zoom gate from §5.5 |
| **1. Engine core** | 12 weeks | RHI, tiles, compositor, brush engine v1, undo, document store | 4K canvas, 30 layers, no dropped frames while painting on mid-range device |
| **2. MVP app** | 10 weeks | Full MVP feature list, phone + tablet UI | Closed alpha to 50 artists; crash-free sessions ≥ 99% |
| **3. Pro tools** | 16 weeks | Brush Studio, selections, transform, liquify, filters, guides, PSD, infinite canvas shipping-quality | Open beta on Play; artist panel rates brush feel ≥ 4/5 against Infinite Painter |
| **4. Polish & launch** | 8 weeks | Performance tiers, device matrix fixes, localisation, store assets, timelapse | **1.0 on Google Play** |
| **5. Flipbook animation** | 12 weeks | 1.5 release | 500-frame project plays at 24 fps on mid-range |
| **6. Motion studio** | 20 weeks | 2.0 release | Multi-track timeline with audio, 4K export |

Total to 1.0: **~12–13 months**. To 2.0: **~20–21 months**.

---

## 9. Quality and testing

- **Device lab (minimum):** Galaxy Tab S (current + two generations back), Galaxy S Ultra phone with S Pen, Pixel Tablet / Pixel phone, Xiaomi Pad, OnePlus Pad, Lenovo Tab with pen, one low-end Mali-GPU phone with 4 GB RAM, one foldable.
- **GPU coverage:** Adreno, Mali, Xclipse, PowerVR, Immortalis — each with Vulkan validation layers in CI builds.
- **Golden-image tests:** every brush × canonical stroke rendered and pixel-compared on each commit (enabled by engine determinism).
- **Latency rig:** high-speed camera measurements tracked per release.
- **Performance budgets** enforced in CI: frame time, tile upload time, memory ceiling per device tier.
- **File-format fuzzing** on all importers (PSD, ABR, images).
- **Soak tests:** 8-hour scripted painting sessions checking for leaks and autosave integrity; forced-kill recovery tests.
- **Artist advisory panel** of 10–20 working illustrators/animators from alpha onward — feel cannot be unit-tested.

---

## 10. Team

| Role | Count | Focus |
|---|---|---|
| Graphics / engine engineer | 2 | RHI, tiles, compositor, infinite canvas |
| Brush & input engineer | 1 | Brush engine, stylus pipeline, latency |
| Android / UI engineer | 1–2 | Compose shell, adaptive layouts, gallery, platform integration |
| Product designer | 1 | Interaction design, icons, brush curation with artists |
| QA / device lab (part-time → full-time at beta) | 1 | Device matrix, regression |
| Brush artist (contract) | 1 | Default brush library |

---

## 11. Business model

- **Free download** with a genuinely usable core (limited layers/canvas size, core brushes, watermark-free export).
- **One-time "Pro" unlock** with regional pricing — the model artists praise in Procreate and Infinite Painter and the clearest differentiator against Clip Studio and ibis subscriptions.
- Animation studio (2.0) as a second one-time unlock or bundled upgrade.
- No ads on the canvas, ever. Optional later: brush pack marketplace with revenue share for creators.
- Launch channels: art YouTubers/TikTokers who review Android tablets, Reddit art and Galaxy Tab communities, Samsung Galaxy Store featuring, timelapse sharing with an optional end card.

---

## 12. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| GPU driver bugs across Android vendors | Crashes, artefacts on specific devices | Thin RHI with GLES fallback, per-device workaround table, validation layers in CI, staged rollouts |
| Infinite zoom with painterly brushes proves too slow | Headline feature slips | Phase 0 gate, persistent tile cache, fallback to finite deep zoom |
| Latency varies wildly by device | "Feels laggy" reviews | Front-buffer + prediction, per-device tuning, published list of recommended devices |
| Memory pressure / background kills | Lost work | Transactional autosave per stroke, disk-backed undo, dynamic layer limits |
| Scope — Procreate has 13+ years of polish | Never ships | Strict phase gates; feel before features; animation after 1.0 |
| Incumbents respond (or Procreate comes to Android) | Market squeezed | Differentiate on infinite canvas + phone UX + animation in one app |
| Importing third-party brush formats | Legal / compatibility | Support documented/commonly interoperable formats (`.abr`); get legal review before touching proprietary ones |
| Piracy of one-time unlock | Revenue | Play licensing + integrity checks; accept some loss |

---

## 13. Decisions needed before Phase 0

1. **Team and budget** — solo/small team or funded team? This sets the real timeline.
2. **Final name and brand.**
3. **Engine language** — C++20 (recommended, most prior art and libraries) or Rust (safer, thinner ecosystem for this domain).
4. **Free tier limits** and Pro price point.
5. **Generative AI stance** — Procreate publicly refuses generative AI, and many artists care. Recommended: no generative features; say so in marketing.
6. **Reference devices** — pick one flagship tablet, one mid-range phone, one low-end phone as the performance yardsticks.

---

## 14. First four weeks (concrete next steps)

1. Create the repository, Gradle + CMake + NDK skeleton, CI building a debug APK.
2. Vulkan surface with pan/zoom/rotate of a test image at display refresh rate.
3. Stylus input thread → round-brush strokes rendered through the front-buffer path; measure latency.
4. Sparse tile store with a single layer and undo via tile snapshots.
5. Infinite-zoom spike: stroke log + quadtree coordinates + replay into LOD tiles.
6. Recruit 5 artists for weekly feel-testing; acquire the three reference devices.

---

## Sources

- [Best Drawing Apps for iPad in 2026 — Linearity](https://www.linearity.io/blog/best-drawing-apps-for-ipad-in-2026/)
- [Top iPad animation apps — Linearity](https://www.linearity.io/blog/animation-apps-for-ipad/)
- [What's new in Procreate Dreams 2 — Procreate Help](https://help.procreate.com/articles/8AzGf-procreate-dreams-2-update-at-a-glance)
- [Procreate 5 review (Valkyrie engine) — MacStories](https://www.macstories.net/reviews/procreate-5-review-a-rebuilt-graphics-engine-drives-fantastic-animation-color-and-brush-tools-in-an-art-app-perfectly-tailored-to-the-ipad/)
- [Procreate 5 — MacRumors](https://www.macrumors.com/2019/12/08/procreate-5-ipad-animation-assist-brush-studio/)
- [Procreate (software) — Wikipedia](https://en.wikipedia.org/wiki/Procreate_(software))
- [Procreate on AI](https://procreate.com/ai)
- [Endless Paper — App Store](https://apps.apple.com/app/id1294105620)
- [Concepts](https://concepts.app/en/sketchnoting/)
- [Best drawing apps for Android — Creative Bloq](https://www.creativebloq.com/buying-guides/best-drawing-app-for-android)
- [Best drawing apps for Android — Android Police](https://www.androidpolice.com/best-drawing-apps-for-android/)
- [Procreate alternatives for Android — SlashGear](https://www.slashgear.com/1271780/best-procreate-alternatives-android-phones)
- [Infinite Painter docs](https://docs.infinitestudio.art/painter/about/app/)
- [HiPaint — AlternativeTo](https://alternativeto.net/software/hipaint/about)
- [Ink API overview — Android Developers](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/about-ink-api)
- [androidx.ink release notes](https://developer.android.com/jetpack/androidx/releases/ink)
- [androidx.graphics.lowlatency reference](https://developer.android.com/reference/androidx/graphics/lowlatency/package-summary)
- [Stylus low latency — Android Developers blog](https://medium.com/androiddevelopers/stylus-low-latency-d4a140a9c982)
- [Implement Vulkan — Android Open Source Project](https://source.android.com/docs/core/graphics/implement-vulkan)
- [Artist review: Samsung Tab S11 — Parka Blogs](https://www.parkablogs.com/content/samsung-tab-s11-ultra-artist-review)
