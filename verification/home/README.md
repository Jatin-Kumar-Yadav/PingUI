> Historical baseline before the native screen corrections. For the current
> implementation and render evidence, see [the verification index](../README.md).

# Home visual verification

**Result: NOT an exact visual match.** The native Home renders successfully,
but the remaining differences are visible, not just text antialiasing.
No application UI source was changed during this verification.

## Evidence

- [Mobile comparison](mobile-comparison.png): unchanged web Home on the left,
  native Compose Home on the right, at the same 390 × 756 content size.
- [Figma comparison](figma-comparison.png): the supplied Figma node on the left,
  native Compose Home on the right, at the same 1280 × 993 content size.

Both comparisons exclude reference OS status/navigation areas. Native system
bars are also disabled in the renderer; differences in simulated versus real
OS bars are not counted as app mismatches. Images are not resized for comparison.

The Figma reference is file `oCZ7HAm5nmY7FRy1WV5zc7`, node `1:3`.
Its screenshot was retrieved successfully at 1280 × 1081. Editable design
context still returned “This node type is not supported.”

## Findings

| Area | Reference | Native Home | Result |
| --- | --- | --- | --- |
| Greeting | Manrope 400, 22px, 25.3px line height | Manrope 700, 22sp, 30.8sp line height | Different weight, height, and placement |
| Monthly spend amount | Manrope 400, 33px, 49.5px line height | Manrope 700, 33sp, 46.2sp line height | Visibly heavier; different text metrics |
| Section headings | Manrope 400, 15px, 22.5px line height | Manrope 700, 15sp, 21sp line height | Visibly heavier; vertical rhythm differs |
| Hero | CSS 140-degree gradient with a lower inset shadow | Default diagonal Compose gradient without that inset shadow | Shading differs |
| Clay cards and badges | Separate card and badge shadows; badges have a blurred inset highlight | Shared `homeSurface` treatment, including a stroked highlight | Highlights and shadows visibly differ |
| Header alignment | 40px menu followed by a 9px gap | 44dp menu touch container followed by a 9dp gap | Greeting starts 4 logical units farther right |
| Bottom tabs | 58px app area after excluding the 34px reference OS area; 12px side padding | 56dp app area with no side padding | Tab geometry and placement differ |
| Content and structure | Monthly spend, three metrics, categories, seven tools, two transactions, five tabs | Same content and ordering | Present |
| Original icons | Web SVG artwork | Exported VectorDrawables | Source asset check passed; rendered Home uses the corresponding icons |

Reference typography values were read from the browser's computed styles, not
inferred from the appearance of the screenshot. Tailwind's heading reset means
the current prototype headings are weight **400**, despite using heading tags.
The native values are from `HomeDashboard.kt`.

## Checks performed

1. Rendered the unchanged web source into temporary output and inspected its
   computed styles at 390 × 844; removed the 54px mock status area and 34px
   bottom OS area for the mobile comparison.
2. Rendered the actual `PingPaisa()` Compose root, including its fixed tabs,
   with Paparazzi `2.0.0-alpha01` / Layoutlib `15.1.4`, at density 160dpi and
   font scale 1.0, with system UI disabled.
3. Rendered the native root at 1280 × 993 in landscape orientation, at full
   device resolution, for direct comparison with the cropped Figma screenshot.
4. Ran `python3 android/tools/export_svg_assets.py --check`: **passed**, all
   63 SVG, drawable, and manifest files match the unchanged web source.
5. Checked all 18 unique Home/tab SVG and drawable pairs: present, non-empty,
   with valid 24 × 24 vector viewports.
6. Ran `./gradlew :app:recordPaparazziDebug --no-daemon --console plain
   --max-workers=2` in an isolated copy: **passed**, both Home screenshot tests
   rendered without test failures.

The Gradle result confirms compilation and screenshot generation, **not visual
parity**. No Figma-to-native equality assertion passed; the visual comparison
fails for the differences above.

## Isolation and limitations

The JDK, SDK, Gradle wrapper, Maven mirror configuration, Paparazzi dependency,
and test harness were installed only in `/tmp/pingpaisa-verify`. The copied
`PingPaisa()` function's visibility was changed from private to internal solely
to call the existing root from the screenshot tests; its UI implementation was
not changed.

`src/App.tsx`, `src/index.css`, and the Android app implementation were left
unchanged. These are Layoutlib screenshots, not emulator/device screenshots.
Real-device system insets, scrolling gestures, drawer interaction, transitions,
and other screens are not certified by this comparison.
