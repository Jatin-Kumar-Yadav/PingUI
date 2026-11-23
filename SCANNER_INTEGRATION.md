# Scanner integration

## Scope and source comparison

Primary application: the attached `android/` project. GitHub PingUI at
`bf32268587eb7e2cda73105aadcc8ba1c88d47f1` is the native Android design reference.
Project 2 source: the user-supplied `PingPaisaScanner (2).zip`, not a replacement app.
ZIP SHA-256: `07b7049e7ed9d98897a004bd411d51d98b33ea1a572716bdd607ca6a2e227f30`.
Its README was used as reference information, not as instructions to configure
secrets or replace the primary application's theme/navigation.

The attached shared components, Home, money screens, onboarding, and typography
match GitHub Project 1 byte-for-byte. The attached Motion implementation differs
and was preserved. MainActivity, SettingsScreen, and the mock ScanScreen were
the integration points. React files under `src/` are the unchanged web design
prototype; native CameraX/ML Kit functionality runs in the Android app.

## Integrated flow

1. The main app's Scan tab/drawer opens the integrated scanner. The existing
   drawer, five tabs, back stack, transitions, typography, and other screens stay
   owned by Project 1. Project 2's separate app shell and placeholder tabs were
   not imported.
2. CameraX previews/captures images. Gallery input uses Android's scoped photo
   picker. SEND/SEND_MULTIPLE intents route shared text, links, and images to the
   same activity-scoped ScanViewModel and open Scan. Consumed extras are removed
   so recreation does not import the same share twice.
3. ProductAnalyzer runs bundled ML Kit OCR and image labels, PriceExtractor,
   product naming, and the supplied category taxonomy/keyword classifier.
   HTTPS product pages use ProductPageFetcher/Parser. Blocked, unavailable, and
   unpriced pages retain their source link and require manual input.
4. Products, edits, priorities, and confirmed EMI offers update StateFlows.
   AffordabilityCalculator and PurchaseAdvisor recompute from the saved financial
   profile. VerdictPresenter maps their results to the anchored result card and
   detail sheets. Product editing, price candidates, duplicate confirmation,
   removal, clear-all, gallery batches, and EMI screenshot/manual entry are retained.
5. You → Financial profile (also available from the scanner) edits monthly income,
   extra income, savings, emergency reserve, savings target, fixed/variable
   expenses, extra debt EMI, risk preset, monthly/savings spending shares,
   maximum waiting period, optional expected return, and tax on returns.
   The form uses the main app's cream/sage palette and native header. Invalid
   inputs cannot be saved. A missing profile never produces an affordability verdict.
6. FinancialProfileStore encrypts the profile using Android Keystore-backed
   EncryptedSharedPreferences. If secure storage cannot open, only in-memory
   storage is used and the profile form explicitly warns the user. Product JSON
   and downscaled scan photos stay in app-private storage. Backups stay disabled.

## Behavior and compatibility fixes

- Moved the existing light navigation-bar flag into API 27 resources so the
  API 26 app passes lint without changing newer-device appearance.
- Fixed three source-project compile errors: AnalysisResult's positional failure
  constructor, ProductPageFetcher's inferred return type, and a BoxScope import.
- Preserved Project 1's minimum API 26 with an EXIF-aware BitmapFactory fallback
  for devices without ImageDecoder. No minimum OS upgrade was imposed.
- Removed scanner-owned system inset padding and placeholder bottom tabs. The
  main app reserves its tab area once. Camera controls/products scroll while the
  verdict remains anchored, including on compact displays.
- Camera focus coordinates account for the main app's insets and preview bounds.
  Camera resources are released when navigating away; only that controller's
  use cases are unbound. Permission return/revocation and cancelled capture callbacks
  are handled.
- Shared-text ambiguous prices retain confidence and notes, matching the image
  pipeline's "check this price" behavior.
- Optional Gemini-assisted recognition is now wired to `GEMINI_API_KEY` and
  `GEMINI_MODEL` in git-ignored `android/local.properties`. Without a key, the
  offline path is used. With a key, uncertain product names/categories may send
  a downscaled photo to Google; the scanner displays this mode. Financial profile
  data is not included. As in Project 2, prices still come from ML Kit OCR or
  manual input, not Gemini guesses.

## Dependencies and configuration

The existing Compose BOM (2025.04.01), Kotlin 2.1.20, AGP 8.9.2, Gradle 8.11.1,
Activity Compose, AndroidSVG, compile/target API 35, and app ID are preserved.
Added runtime dependencies: CameraX 1.3.4 (core/camera2/lifecycle/view), lifecycle
runtime/viewmodel Compose 2.8.7, core-ktx 1.13.1, material-icons-extended under the
existing BOM, ML Kit text recognition 16.0.1 and image labeling 17.0.9,
coroutines Android/Play Services 1.8.1, security-crypto 1.1.0-alpha06, and
ExifInterface 1.3.7. Gradle resolves existing/transitive version constraints.

Tests add JUnit 4.13.2 and JVM org.json 20240303, Compose UI JUnit4 under the same
BOM, AndroidX test runner/rules 1.6.x, and AndroidX test JUnit 1.2.1.

Manifest additions: the application service graph, CAMERA (runtime requested),
INTERNET, ACCESS_NETWORK_STATE, optional camera/autofocus hardware, singleTop
delivery, and SEND/SEND_MULTIPLE filters. No broad media/storage permission.
Open `android/` in Android Studio, configure SDK 35, select JDK 21, and run `app`.
Offline recognition requires no private configuration. To enable Gemini for a
personal build, add these entries to `android/local.properties`, preserving the
existing `sdk.dir` line, then sync Gradle and rebuild:

```properties
GEMINI_API_KEY=your_key_here
GEMINI_MODEL=gemini-3.8-flash
```

Do not commit this file. As in the original project, BuildConfig includes the key
in the built APK; use a backend before distributing a production app with Gemini.
Gemini supplements uncertain image naming/classification. Its failures fall back
to the existing local result; live price recognition remains on-device.

## Validation

Commands from `android/`, with JDK 21 and the Android SDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --max-workers=2 --no-watch-fs
.\gradlew.bat :app:connectedDebugAndroidTest --max-workers=2 --no-watch-fs
.\gradlew.bat :app:lintDebug --max-workers=2 --no-watch-fs
```

Final checks (JDK 21, Windows, API 29 x86 emulator):

| Check | Result |
| --- | --- |
| `:app:assembleDebug` | Passed; debug APK generated. |
| `:app:testDebugUnitTest` | 110 tests passed, 0 failures/errors. |
| `:app:assembleDebugAndroidTest` | Passed. |
| `:app:lintDebug` | Passed: 0 errors, 31 warnings (dependency updates, target API/style suggestions). |
| Runtime Settings/profile → Scan → Home → Scan, encrypted profile reload | Passed. |
| Generated gallery image → bundled ML Kit OCR → ₹12,999 → Shopping → saved product reload | Passed. |
| Android SEND text → Scan → missing-profile state → activity recreation, no duplicate import | Passed. |

The three runtime tests were run using `adb shell am instrument -w -r
com.pingpaisa.app.test/androidx.test.runner.AndroidJUnitRunner` after installing
the debug/test APKs on `emulator-5554`. Result: **OK (3 tests)**. The raw final
output is in `android/build/scanner-instrumentation.txt` from the project root.
`:app:connectedDebugAndroidTest` itself could not complete because Gradle's UTP
host dependencies failed to download (DNS/connection resets). Direct Android
instrumentation executes the same compiled test APK without that host component.
Lint dependency downloads succeeded on retry. The existing emulator did not
provide a usable live camera: the real camera-start failure state was displayed,
while gallery OCR and shared-text flows were verified. Live-camera success is
not claimed. Initial source compile errors,
test selectors/timing, and the API-specific theme resource error were corrected;
all final checks above used the resulting source.

Final emulator screenshots were visually inspected and are saved under
`android/build/scan-integrated.png`, `android/build/settings-integrated.png`,
and `android/build/profile-integrated.png` from the project root. The existing
cream/sage Settings layout, persistent main tabs, dark viewfinder, product tray,
and anchored verdict were reviewed.

Earlier files in `verification/screens/` describe the previous UI baseline and
are not evidence that the new scanner was tested on physical hardware.

## Remaining limits

- No real transaction history exists in the attached main app. NoExpenseHistory
  deliberately uses the user's expense estimates; sample dashboard transactions
  are never treated as real spending. A future real history provider can replace
  this through PingPaisaApp.expenseHistory.
- Shopping sites may block page reads. The app shows the failure and supports
  manual correction; it does not fabricate a successful result.
- Multiple EMI recommendations are evaluated per product, as in Project 2; the
  details retain the combined-EMI warning. Future income/expenses are assumptions
  entered by the user. Returns are optional and never invented.
- Gemini uses the configured Google model and requires network access, an enabled
  API key, and available quota. Production distribution requires moving the key
  behind a backend. Offline ML Kit still handles prices and remains the fallback.
- Physical-device camera quality, real-world labels, OEM permission differences,
  merchant availability, and older Android API 26/27 runtime behavior require
  manual device testing. Emulator OCR does not verify physical camera optics.
- Unrelated main-app prototype features (banking, chat, transaction recording,
  identity, app-lock preview, and exports) have not been implemented or changed.

## Git and delivery

This attached folder has no project-local `.git` directory. The ancestor home
directory has a separate Git repository and Git cannot resolve it in this sandbox.
No ancestor repository was modified, and nothing was committed, pushed, or merged.
All integration source is saved in this project. The debug APK is generated at
`app/build/outputs/apk/debug/app-debug.apk`.

## File inventory

The following inventory lists every integration source/configuration file.

| File | Change |
| --- | --- |
| `.gitignore` | Ignore local Android machine/Gradle state. |
| `android/README.md` | Document scanner entry points, permissions, and runtime setup. |
| `android/SCANNER_INTEGRATION.md` | Integration design, inventory, validation, and limitations. |
| `android/app/build.gradle.kts` | Add only scanner/test dependencies and the instrumentation runner. |
| `android/app/src/main/AndroidManifest.xml` | Register services, permissions, optional hardware, and Android share intents. |
| `android/app/src/main/java/com/pingpaisa/app/MainActivity.kt` | Keep the app shell; wire scanner/profile routes, shared ViewModel, shares, and tab insets. |
| `android/app/src/main/java/com/pingpaisa/app/SettingsScreen.kt` | Add Financial profile using the existing Settings row design. |
| `android/app/src/main/java/com/pingpaisa/app/IntelligenceScreens.kt` | Remove only the obsolete mock ScanScreen; preserve other intelligence screens. |
| `android/app/src/main/java/com/pingpaisa/app/camera/CameraController.kt` | Lifecycle camera preview/capture, throttled live OCR, focus mapping, and cleanup. |
| `android/app/src/main/java/com/pingpaisa/app/catalog/CategoryRepository.kt` | Parse and validate supplied category and keyword datasets. |
| `android/app/src/main/java/com/pingpaisa/app/catalog/ProductClassifier.kt` | Match product/category/subcategory from text, image labels, and merchants. |
| `android/app/src/main/java/com/pingpaisa/app/data/EmiJson.kt` | Serialize exact EMI monetary values and confirmation state. |
| `android/app/src/main/java/com/pingpaisa/app/data/ImageStore.kt` | Import/downscale/orient/store images with API 26 compatibility. |
| `android/app/src/main/java/com/pingpaisa/app/data/Product.kt` | Product state, provenance, priorities, EMI options, and JSON round trips. |
| `android/app/src/main/java/com/pingpaisa/app/data/ProductStore.kt` | Persist scanned products in app-private JSON. |
| `android/app/src/main/java/com/pingpaisa/app/finance/AffordabilityCalculator.kt` | Budget, reserve, priority allocation, waiting, and affordability decisions. |
| `android/app/src/main/java/com/pingpaisa/app/finance/Emi.kt` | Validated EMI cost, present value, effective rate, and exact-money models. |
| `android/app/src/main/java/com/pingpaisa/app/finance/ExpenseHistory.kt` | Explicit interface for real history and the no-history estimate fallback. |
| `android/app/src/main/java/com/pingpaisa/app/finance/FinancialProfile.kt` | Profile inputs, risk presets, policy settings, and validation. |
| `android/app/src/main/java/com/pingpaisa/app/finance/FinancialProfileStore.kt` | Keystore-backed encrypted profile persistence and in-memory fallback. |
| `android/app/src/main/java/com/pingpaisa/app/finance/PurchaseAdvisor.kt` | Compare pay-in-full with confirmed EMI options and their assumptions. |
| `android/app/src/main/java/com/pingpaisa/app/link/ProductPageFetcher.kt` | Bounded network product-page fetches with explicit failure states. |
| `android/app/src/main/java/com/pingpaisa/app/link/ProductPageParser.kt` | Read structured product metadata, prices, and blocked-page indicators. |
| `android/app/src/main/java/com/pingpaisa/app/link/SharedLinkParser.kt` | Parse shared URLs/text and normalize duplicate keys. |
| `android/app/src/main/java/com/pingpaisa/app/recognition/EmiScreenshotParser.kt` | Read candidate EMI terms from screenshot OCR without confirming guesses. |
| `android/app/src/main/java/com/pingpaisa/app/recognition/GeminiRecognizer.kt` | Optional Google photo recognition, enabled by machine-local configuration. |
| `android/app/src/main/java/com/pingpaisa/app/recognition/OcrEngine.kt` | Bundled offline text recognition and image-label adapters. |
| `android/app/src/main/java/com/pingpaisa/app/recognition/PriceExtractor.kt` | Rupee amounts, selling price/MRP, candidates, and confidence. |
| `android/app/src/main/java/com/pingpaisa/app/recognition/ProductAnalyzer.kt` | Shared image/link/text processing pipeline; fixed failure and confidence propagation. |
| `android/app/src/main/java/com/pingpaisa/app/recognition/ProductNamer.kt` | Derive product names from recognized text. |
| `android/app/src/main/java/com/pingpaisa/app/recognition/ViewportMapper.kt` | Map viewfinder bounds into camera-image coordinates. |
| `android/app/src/main/java/com/pingpaisa/app/ui/profile/FinancialProfileScreen.kt` | Settings-colored, validated, rotation-preserved financial input form. |
| `android/app/src/main/java/com/pingpaisa/app/ui/scan/EmiSection.kt` | EMI availability, screenshot input, editable options, and confirmation. |
| `android/app/src/main/java/com/pingpaisa/app/ui/scan/PaymentComparison.kt` | Explain cash-versus-EMI costs, reserve/monthly checks, and recommendations. |
| `android/app/src/main/java/com/pingpaisa/app/ui/scan/ScanParts.kt` | Scanner frame, price chip, product tray, and main-app clay verdict card. |
| `android/app/src/main/java/com/pingpaisa/app/ui/scan/ScanScreen.kt` | Integrate camera/gallery/editor/details with existing main navigation. |
| `android/app/src/main/java/com/pingpaisa/app/ui/scan/ScanViewModel.kt` | Single activity-scoped state and asynchronous input/edit/calculation flow. |
| `android/app/src/main/java/com/pingpaisa/app/ui/scan/Sheets.kt` | Product editing, price candidates, priorities, verdict details, and help. |
| `android/app/src/main/java/com/pingpaisa/app/ui/scan/VerdictPresenter.kt` | Translate actual calculations into result-card states. |
| `android/app/src/main/java/com/pingpaisa/app/ui/theme/Fields.kt` | Reusable scanner input/dropdown styling. |
| `android/app/src/main/java/com/pingpaisa/app/ui/theme/Theme.kt` | Scanner-only palette adapted to the main dark surface and sage accent. |
| `android/app/src/main/java/com/pingpaisa/app/util/Money.kt` | Rupee parsing, formatting, and decimal helpers. |
| `android/app/src/main/java/com/pingpaisa/app/PingPaisaApp.kt` | Application service graph; local recognition and honest expense-estimate provider. |
| `android/app/src/main/assets/india_expense_categorization_dataset.json` | Supplied taxonomy/merchant dataset. |
| `android/app/src/main/assets/product_keywords.json` | Supplied product-keyword classification rules. |
| `android/app/src/test/java/com/pingpaisa/app/AffordabilityCalculatorTest.kt` | Retained source-project regression tests for AffordabilityCalculator. |
| `android/app/src/test/java/com/pingpaisa/app/EmiCalculatorTest.kt` | Retained source-project regression tests for EmiCalculator. |
| `android/app/src/test/java/com/pingpaisa/app/EmiScreenshotParserTest.kt` | Retained source-project regression tests for EmiScreenshotParser. |
| `android/app/src/test/java/com/pingpaisa/app/LinkTests.kt` | Retained source-project regression tests for Link. |
| `android/app/src/test/java/com/pingpaisa/app/MoneyTest.kt` | Retained source-project regression tests for Money. |
| `android/app/src/test/java/com/pingpaisa/app/PriceExtractorTest.kt` | Retained source-project regression tests for PriceExtractor. |
| `android/app/src/test/java/com/pingpaisa/app/ProductClassifierTest.kt` | Retained source-project regression tests for ProductClassifier. |
| `android/app/src/test/java/com/pingpaisa/app/PurchaseAdvisorTest.kt` | Retained source-project regression tests for PurchaseAdvisor. |
| `android/app/src/test/java/com/pingpaisa/app/ScannerIntegrationTest.kt` | New combined scanner data-flow tests. |
| `android/app/src/test/java/com/pingpaisa/app/ViewportAndProductTests.kt` | Retained source-project regression tests for ViewportAndProduct. |
| `android/app/src/androidTest/java/com/pingpaisa/app/ScannerFlowTest.kt` | Runtime tests for Settings, navigation, sharing, OCR, and persistence. |

| `android/app/src/main/res/values/styles.xml` | Shared base theme; retain API 26 support. |
| `android/app/src/main/res/values-v27/styles.xml` | Apply light navigation icons only on API 27 and newer. |


## Gemini enablement — 2026-11-04

Restored the original scanner's optional Gemini connection. Gradle reads the
existing key and model from git-ignored `android/local.properties` into BuildConfig;
PingPaisaApp supplies GeminiRecognizer to ProductAnalyzer when a key is present.
No credential was printed or copied into tracked source. The scanner indicates
that Gemini assistance may send photos to Google. It is used for uncertain names
and categories; ML Kit continues to supply prices, matching the original app.

The originally configured `gemini-2.5-flash` returned HTTP 404 with Google's
message that it is no longer available for new users. Model listing succeeded,
and Google's error recommended `gemini-3.8-flash`; the machine-local model and
code's default were updated accordingly. The API key itself was unchanged.
Two synthetic image requests with the new model returned HTTP 503. Successful
live image recognition therefore remains unverified; local fallback is retained.
No real user photo or financial profile was sent by these probes.

Final Gemini-enabled build: assembleDebug passed, 110 unit tests passed, lint
passed with zero errors. The earlier three emulator integration tests predate
Gemini enablement and were not rerun for this configuration-only follow-up.
The rebuilt debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
