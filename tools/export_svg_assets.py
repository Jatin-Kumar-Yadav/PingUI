#!/usr/bin/env python3
"""Export the web prototype's inline SVG artwork without rasterising or restyling it."""

import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ANDROID = Path(__file__).resolve().parents[1]
SOURCE = ANDROID.parent / "src" / "App.tsx"
ASSETS = ANDROID / "app/src/main/assets"
DRAWABLES = ANDROID / "app/src/main/res/drawable"
SVG_NS = "http://www.w3.org/2000/svg"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("", SVG_NS)
ET.register_namespace("android", ANDROID_NS)
INK = "#35453f"


def svg_markup(markup):
    """The source SVGs use only literal JSX attributes, not expressions."""
    for jsx, svg in {
        "stopColor": "stop-color", "stopOpacity": "stop-opacity",
        "strokeWidth": "stroke-width", "strokeLinecap": "stroke-linecap",
        "strokeLinejoin": "stroke-linejoin", "strokeDasharray": "stroke-dasharray",
    }.items():
        markup = markup.replace(jsx + "=", svg + "=")
    return markup


def vector_path(element):
    tag = element.tag
    if tag == "path":
        return element.attrib["d"]
    if tag == "circle":
        x, y, r = (float(element.attrib[key]) for key in ("cx", "cy", "r"))
        return f"M{x-r},{y} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0"
    if tag == "rect":
        x, y, w, h = (float(element.attrib[key]) for key in ("x", "y", "width", "height"))
        r = float(element.get("rx", "0"))
        return (
            f"M{x+r},{y} H{x+w-r} A{r},{r} 0 0,1 {x+w},{y+r} V{y+h-r} "
            f"A{r},{r} 0 0,1 {x+w-r},{y+h} H{x+r} A{r},{r} 0 0,1 {x},{y+h-r} "
            f"V{y+r} A{r},{r} 0 0,1 {x+r},{y} Z"
        )
    raise ValueError(f"Unsupported SVG icon element: {tag}")


def vector_xml(fragment):
    def attr(key):
        return f"{{{ANDROID_NS}}}{key}"
    root = ET.Element("vector", {
        attr("width"): "24dp", attr("height"): "24dp",
        attr("viewportWidth"): "24", attr("viewportHeight"): "24",
    })
    for element in ET.fromstring(f"<g>{fragment}</g>"):
        ET.SubElement(root, "path", {
            attr("pathData"): vector_path(element),
            attr("fillColor"): "#00000000", attr("strokeColor"): INK,
            attr("strokeWidth"): "1.9", attr("strokeLineCap"): "round",
            attr("strokeLineJoin"): "round",
        })
    ET.indent(root)
    return ET.tostring(root, encoding="unicode", xml_declaration=True) + "\n"


def outputs():
    source = SOURCE.read_text()
    icon_block = source.split("const paths: Record<IconName, ReactNode> = {", 1)[1].split("\n  };", 1)[0]
    icons = re.findall(r"^\s+(\w+): (.+),$", icon_block, re.MULTILINE)
    expected = set(re.findall(r'"(\w+)"', source.split("type IconName =", 1)[1].split(";", 1)[0]))
    assert {name for name, _ in icons} == expected, "An icon could not be extracted"
    result = {}
    entries = []
    for name, jsx in icons:
        fragment = svg_markup(jsx.replace("<>", "").replace("</>", ""))
        svg = (
            f'<svg xmlns="{SVG_NS}" width="24" height="24" viewBox="0 0 24 24" '
            f'fill="none" stroke="{INK}" stroke-width="1.9" stroke-linecap="round" '
            f'stroke-linejoin="round">{fragment}</svg>\n'
        )
        ET.fromstring(svg)
        asset = f"svg/icons/{name}.svg"
        drawable = f"ic_pingpaisa_{name}"
        result[ASSETS / asset] = svg
        result[DRAWABLES / f"{drawable}.xml"] = vector_xml(fragment)
        entries.append({"name": name, "asset": asset, "drawable": drawable})
    charts = re.findall(r'<svg viewBox="[^"]+">.*?</svg>', source)
    assert len(charts) == 2, "Update chart names if SVG artwork is added"
    for name, markup in zip(("spend_trajectory", "health_gauge"), charts):
        svg = svg_markup(markup).replace("<svg ", f'<svg xmlns="{SVG_NS}" ', 1) + "\n"
        ET.fromstring(svg)
        asset = f"svg/charts/{name}.svg"
        result[ASSETS / asset] = svg
        entries.append({"name": name, "asset": asset})
    result[ASSETS / "svg/manifest.json"] = json.dumps({
        "source": "src/App.tsx", "icons": len(icons), "charts": len(charts),
        "defaultStroke": INK, "assets": entries,
    }, indent=2) + "\n"
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check committed assets match the source")
    args = parser.parse_args()
    generated = outputs()
    for path, text in generated.items():
        if args.check:
            assert path.is_file() and path.read_text() == text, f"Missing or stale: {path}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
    print(f"{'Verified' if args.check else 'Exported'} {len(generated)} SVG, drawable and manifest files.")


if __name__ == "__main__":
    main()
