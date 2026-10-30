# AndroidLiquidGlass / Backdrop

Upstream: https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6

Copyright 2025 Kyant. Apache License 2.0; complete license in LICENSE and APK assets/licenses/android-liquid-glass.txt.

The renderer is directly linked from the published, unmodified artifact `io.github.kyant0:backdrop:1.0.6`.
The app calls its LayerBackdrop, drawBackdrop, blur, lens and Highlight implementations. Its transitive shapes dependency is `io.github.kyant0:shapes:1.2.0`.
Version 0.19.34 adds `core/glass/.../LiquidLensEffect.kt`, adapted from the same tag's Lens.kt and Shaders.kt for a continuous dispersion coefficient. At 100% and zero/off it calls the original dependency directly; other strengths retain the upstream SDF, refraction and seven-sample dispersion algorithm with a variable uniform. The Apache license and attribution are retained. This adds no rendering pass or per-strength shader recompilation.
The previous app-authored AGSL renderer is removed from current source; its pre-migration backup is in artifacts/history.

Six catalog implementations are imported and adapted from tag 1.0.6 under core/glass/src/main/kotlin/com/kyant/backdrop/catalog:

- components/LiquidBottomTabs.kt
- components/LiquidBottomTab.kt
- components/LiquidButton.kt
- utils/DampedDragAnimation.kt
- utils/InteractiveHighlight.kt
- utils/DragGestureInspector.kt

Retained upstream packages make the origin explicit. Modified files carry source, copyright, license and modification headers.
Changes: app G2 Shape adapter for non-navigation components, weather tint, shared optics / interaction settings,
reduced-motion support and velocity updates during tab-tap animation.
Version 0.3.3 restores the upstream Capsule, 64 / 56 dp tab sizes and 78 / 56 pressed-scale baseline.
Version 0.8.0 proportionately reduces the navigation shell / lens to 56 / 49 dp (87.5%), retaining the Capsule, sampling and press algorithms. Global app optics are now fixed; all button content is centered. Button-to-menu morphing is app-owned and reuses the unmodified upstream renderer.
LiquidButton retains the upstream elastic scale, tanh offset and directional drag transform;
its transform is extracted for reuse by slider thumbs. It accepts shared optics, app Shape, adaptive height and role.
InteractiveHighlight exposes a bridge for accessible slider gestures, shared intensity and a 140 ms minimum press window.
DampedDragAnimation gains the same minimum press window with generation guards against stale releases.
GlassOptics resolves user refraction, blur, highlight, sampling zoom, tint, dispersion and interaction once for all components.
The renderer artifact and its shaders remain unmodified; only app policy and the copied catalog integration change.
DragGestureInspector additionally forwards the final UP displacement when Android coalesces the last MOVE.
DampedDragAnimation records requested targets synchronously so drag completion does not read a stale coroutine target.
Version 0.3.4 observes drag / highlight gestures on a stable parent; the moving lens only draws.
External selection drives its spring without echoing a navigation callback. Drag callbacks commit only beyond touch slop;
taps continue through the stable tab click targets.
This prevents a stretched lens swallowing adjacent tab taps and a release overwriting a newer tap target.

The upstream lens shader uses rounded-rectangle distance approximation; exact clipping and outline highlights use
the app's cached continuous G2 Shape through a CornerBasedShape adapter for surfaces and buttons. Navigation uses the original Capsule.
The approximation is upstream code,
not the previous custom G2 shader. Core geometric paths are not replaced by Android default rounded rectangles.

Toolchain: AGP 8.13.2, Kotlin 2.3.10, KSP 2.3.5, Hilt 2.58, Compose BOM 2026.02.01 (Compose 1.10.4), Gradle 8.13.
Dependency resolution and APK build checks are recorded in docs/verification.md.
