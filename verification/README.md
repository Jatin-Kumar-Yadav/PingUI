# Native UI verification

## Result

The Android implementation has been rebuilt across **all 16 screens and the
Style Guide**, rather than retaining the earlier generic Material layouts.
The actual Android project builds successfully, and **20 native screenshot
cases pass with zero failures or errors**.

This establishes that the native screens compile and render. It is **not a
certification of pixel-identical output on every Android device**. Browser and
Android text/shadow rasterization still differ, and the screenshot tests are
render tests, not automated Figma-image equality assertions.

The original Figma document and web UI have not been changed.

## Visual evidence

[Open the native contact sheet](screens/native-contact-sheet.png).

Each comparison uses equally sized 440 × 868 app-content images, with the
reference on the left and native Android on the right. Original pixels are
not stretched to make the comparison appear closer. OS status/navigation
areas and the Figma export's trailing content are excluded.

| Screen | Reference used | Comparison |
| --- | --- | --- |
| Onboarding | Unchanged web prototype | [View](screens/comparisons/Onboarding.png) |
| App lock | Unchanged web prototype | [View](screens/comparisons/Lock.png) |
| Home | Attached screenshot and unchanged web prototype | [View](screens/comparisons/Home.png) |
| Transactions | Attached screenshot and unchanged web prototype | [View](screens/comparisons/Transactions.png) |
| Transaction detail | Attached screenshot and unchanged web prototype | [View](screens/comparisons/Detail.png) |
| Add an expense | Attached screenshot and unchanged web prototype | [View](screens/comparisons/Entry.png) |
| Budgets & goals | Unchanged web prototype | [View](screens/comparisons/Budgets.png) |
| Cash flow | Unchanged web prototype | [View](screens/comparisons/Flow.png) |
| Recurring payments | Unchanged web prototype | [View](screens/comparisons/Recurring.png) |
| Loan simulator | Unchanged web prototype | [View](screens/comparisons/Loan.png) |
| June forecast | Unchanged web prototype | [View](screens/comparisons/Forecast.png) |
| Money map | Unchanged web prototype | [View](screens/comparisons/Heatmap.png) |
| Scan price | Figma `5:600` and attached screenshot | [View](screens/comparisons/Scan.png) |
| Paisa AI | Figma `5:857` and attached screenshot | [View](screens/comparisons/Chat.png) |
| Financial health | Unchanged web prototype | [View](screens/comparisons/Health.png) |
| Settings | Figma `5:1119` and attached screenshot | [View](screens/comparisons/Settings.png) |
| Style Guide | Unchanged web prototype | [View](screens/comparisons/Style.png) |

The seven latest uploads were checked against the earlier references and are
byte-for-byte identical. Editable design context was available for the three
Figma nodes above; the other screens were ported from the supplied screenshots
and the existing prototype, not from newly retrieved editable Figma layers.

## Corrections included

- Reference-sized Manrope, with regular/bold/extrabold weights applied per text
  element, instead of making all native headings bold.
- The exact Figma-resolved Manrope face and explicit Noto symbol fallbacks,
  bundled for offline use. All ten requested font faces resolved.
- Shared CSS-inspired directional Gaussian shadows, inset badge highlights,
  gradients, rounded surfaces, and the original icon artwork.
- The original Home hierarchy; side-by-side transaction donut and legend;
  detailed merchant/category/note UI; expense keypad; budget rings; cash-flow
  bars; recurrence cards; and loan controls.
- The camera-style Scan surface, viewfinder, moving scan line, price badge,
  capture button, and anchored verdict.
- Chat header, message geometry, rich reply spacing, suggestions, and fixed
  composer; Settings profile, groups, toggle, and privacy card.
- Original SVG chart artwork rather than replacement chart paths, including a
  rendering adapter for SVG `pathLength` dash normalization.
- Indian-number grouping independent of JVM locale behavior, including
  `₹6,00,000`, fixed tabs, actual device insets, scrolling, adaptive grids,
  navigation, and the hidden drawer.

## Checks run

| Check | Result |
| --- | --- |
| `./gradlew :app:assembleDebug` in the actual `android/` project | Passed |
| `python3 android/tools/verify_visuals.py --work-dir /tmp/pingpaisa-final-check --output android/verification/screens/native` with JDK/SDK configured | Passed |
| Native reference-size render for each of the 17 views | 17 passed |
| Home, 320 × 480 compact content viewport | Passed |
| Chat, 844 × 302 landscape content viewport | Passed |
| Transaction detail, 360 × 712, font scale 1.5 | Passed |
| `python3 android/tools/export_svg_assets.py --check` | All 63 exports passed |
| Referenced original vector assets | All 28 present, non-empty, valid |
| Original chart SVGs | Both present and valid |
| Downloaded Figma SVGs | All 36 present and valid |

The screenshot project used Paparazzi `2.0.0-alpha01`, Layoutlib `15.1.4`,
160dpi, and no simulated OS UI. The test plugin and desktop XML compatibility
helper are installed only in a temporary verification project, not the app.
The sandbox's public Maven mirror and reduced Gradle memory settings were
configured outside the repository.

See [render-results.json](screens/render-results.json) and the JUnit result in
`screens/native/`. The source hashes in the JSON record the implementation
actually rendered. The older `home/` report is a historical before-correction
baseline, not the current result.

## Remaining verification boundaries

- There are still small platform-specific text, fractional spacing, border,
  and blur differences visible in the comparisons. Scan-line position also
  depends on animation phase. These have not been relabeled as exact parity.
- The remaining fourteen views lack newly supplied editable Figma-frame
  context. Strict layer-by-layer certification needs their frame links.
- These are Layoutlib renders, not emulator/device screenshots. Real system
  bars, keyboard behavior, scrolling gestures, touch targets, drawer gestures,
  navigation transitions, and animations-disabled behavior require a device
  review. The extra viewport renders prove rendering, not every interaction.
- Screen content remains a design demo: there is no live camera, banking,
  authentication, persistence, AI service, or export backend.
