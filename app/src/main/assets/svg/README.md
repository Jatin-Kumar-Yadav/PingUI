# PingPaisa SVG artwork

- `icons/`: all 30 inline line icons from the original React prototype.
- `charts/`: the original spending trajectory and financial health gauge.
- `manifest.json`: asset inventory and matching Android drawable names.

The original path geometry, 24×24 icon viewports, rounded strokes, and chart
gradients are preserved. Icon `currentColor` is resolved to the existing ink
token (`#35453f`) so each file also works in standalone SVG viewers.

Android does not load `.svg` with `painterResource`. Matching icon VectorDrawable
XML files are provided in `res/drawable/ic_pingpaisa_*.xml`. For example:

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_pingpaisa_wallet),
    contentDescription = "Wallet",
    tint = MaterialTheme.colorScheme.onSurface
)
```

These are source assets, not a replacement icon style. Home and the shared
bottom tabs use the matching original VectorDrawables. The remaining screens
still use their earlier Material outline icons and Canvas charts. The
full-fidelity chart SVGs remain available for editing or use with an SVG-capable
renderer; they are not approximated as VectorDrawables. CSS-drawn cards, clay
illustrations and blobs are not SVGs.

From the repository root, regenerate or verify these files with:

```sh
python3 android/tools/export_svg_assets.py
python3 android/tools/export_svg_assets.py --check
```

The exporter requires the web source only when regenerating/checking assets.
The Android project and its packaged assets run independently.
