# PingPaisa — native Android UI

A Kotlin / Jetpack Compose implementation of the existing PingPaisa design.
Open **`android/`** in Android Studio. The Figma document and original web UI
(`src/App.tsx`, `src/index.css`) are not modified by the native implementation.

## Run

1. Open this folder in Android Studio Meerkat or newer.
2. Install Android SDK 35 and use JDK 17 or newer for Gradle.
3. Sync, choose an API 35 emulator or an Android 8+ device, and run `app`.

The Gradle **8.11.1 wrapper is included**; a separate Gradle installation is not
needed. Android Studio creates the machine-specific `local.properties` SDK path.
Do not commit that file. From this directory, build with:

```sh
./gradlew :app:assembleDebug
```

The first sync needs internet access. There are no private keys or backend
requirements. The app does not need the repository's web dependencies.

## Screens and code

All 16 screens and the Style Guide use native Compose layouts:

- `MainActivity.kt`: theme, inset handling, screen routing, hidden drawer,
  fixed tabs, back navigation, and slide/fade transitions.
- `ClayComponents.kt`: reference typography, directional Gaussian clay shadows,
  inset highlights, gradient surfaces, original-icon badges, headers, buttons,
  transaction cards, grids, and scrolling containers.
- `HomeDashboard.kt`: sage hero, overlapping spend card, category pills,
  seven money tools, and recent activity.
- `MoneyScreens.kt`: Transactions, Transaction detail, Add an expense,
  Budgets & goals, Cash flow, Recurring payments, and Loan simulator.
- `IntelligenceScreens.kt`: Scan price, Paisa AI, June forecast, Money map,
  and Financial health.
- `SettingsScreen.kt`: profile, organisation/security groups, toggle, privacy card.
- `GettingStartedScreens.kt`: Onboarding, PIN screen, and Style Guide.
- `PingPaisaTypography.kt`: bundled Manrope and explicit symbol fallback faces.

The former generic Material-icon headers, elevated placeholder cards, simplified
transaction layout, QR placeholder, chat form, and approximate chart paths have
been replaced. The original SVG charts are rendered with AndroidSVG; the
adapter also implements the SVG `pathLength` dash normalization that AndroidSVG
1.4 does not perform itself. Original artwork files are not rewritten.

## Design references and fonts

The linked Figma nodes `5:600`, `5:857`, and `5:1119` supplied editable context
for Scan, Chat, and Settings. The seven attached screenshots and unchanged web
prototype supply the remaining screen layout/style reference. The earlier
Home inspection established that headings in the current prototype are
**Manrope 400**, not automatically bold just because they are heading elements.
Bold/extrabold labels and numeric text retain their individual weights.

Figma-resolved Manrope 4.504 and the declared Noto fallback faces are bundled
as TTFs. Symbol fallback is explicit for glyphs absent from Manrope, such as
backspace and ellipsis. All faces resolved successfully. Licenses are in
`licenses/`; `app/src/main/assets/figma/fonts.json` records face provenance.
Downloaded reference SVGs are retained under `assets/figma/`; the original web
SVG/drawable pairs remain under `assets/svg/` and `res/drawable/`.

## Layout and interaction

- Full-width content uses the available window, without an embedded phone frame.
- Actual Android safe-drawing/keyboard insets are consumed once. Status and
  gesture-navigation indicators are OS-owned, never painted into the app.
- The five tabs stay fixed while page content scrolls beneath their reserved
  reachability area. Chat suggestions/input and the Scan verdict are anchored.
- Grids reflow on narrow windows or at increased system font scale. Text remains
  in sp; content can grow instead of being globally shrunk.
- The drawer opens from the original menu button and exposes every screen.
- PIN entry, expense keypad/type/category, recurring/month filters, loan slider
  and tenure, sample chat sending, app-lock toggle, and back navigation work as
  local design-demo interactions. Screen state is retained while navigating.
- CSS-inspired transitions and the scan-line motion use Compose animations.

## Verification

See [`verification/README.md`](verification/README.md) for the current result,
reference coverage, screenshots, and limitations. The older
`verification/home/` report is a historical **before-correction** baseline.

The screenshot harness is kept outside the application source set. To rerun it
with an installed JDK/SDK, from the repository root:

```sh
python3 android/tools/verify_visuals.py
python3 android/tools/export_svg_assets.py --check
```

The first command creates a temporary copy, adds Paparazzi only to that copy,
renders the actual app root, and copies the screenshots/test report back to
`verification/screens/native`. It does not add a screenshot-test runtime or
Byte Buddy to the shipped app. The optional `--work-dir` preserves the temporary
project for inspection. The desktop-only XML parser shim makes disabling an
unsupported parser feature a no-op; it does not replace or suppress chart UI.

Rendering/build success is **not** a claim of pixel identity across Android and
browser rasterizers. Device insets, font rendering, keyboard behavior, gestures,
and animated states still need a real emulator/device review. There is no
certification of exact Figma-layer parity for screens without editable context.

## Design-only scope

All data is illustrative. There is no SMS/UPI ingestion, bank connection, real
login/biometric security, camera capture, AI integration, file export, or durable
data storage. The app requests no runtime permissions. The visible privacy and
financial copy is retained from the design, not a claim that those services
are implemented. Dialogs describe the prototype boundary when relevant.
