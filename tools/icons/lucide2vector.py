#!/usr/bin/env python3
"""Converts Lucide SVG icons (ISC licence) into Android vector drawables (stroke-based, tinted at runtime)."""
import re, sys, xml.etree.ElementTree as ET

def circle(cx, cy, r):
    return f"M{cx-r},{cy}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0"

def ellipse(cx, cy, rx, ry):
    return f"M{cx-rx},{cy}a{rx},{ry} 0 1,0 {2*rx},0a{rx},{ry} 0 1,0 {-2*rx},0"

def rect(x, y, w, h, rx=0, ry=None):
    ry = rx if ry is None else ry
    if not rx:
        return f"M{x},{y}h{w}v{h}h{-w}z"
    return (f"M{x+rx},{y}h{w-2*rx}a{rx},{ry} 0 0 1 {rx},{ry}v{h-2*ry}a{rx},{ry} 0 0 1 {-rx},{ry}"
            f"h{-(w-2*rx)}a{rx},{ry} 0 0 1 {-rx},{-ry}v{-(h-2*ry)}a{rx},{ry} 0 0 1 {rx},{-ry}z")

def f(e, k, d=0.0):
    return float(e.get(k, d))

def convert(svg_path, out_path):
    root = ET.parse(svg_path).getroot()
    paths = []
    for e in root.iter():
        tag = e.tag.split('}')[-1]
        if tag == "path": paths.append(e.get("d"))
        elif tag == "circle": paths.append(circle(f(e, "cx"), f(e, "cy"), f(e, "r")))
        elif tag == "ellipse": paths.append(ellipse(f(e, "cx"), f(e, "cy"), f(e, "rx"), f(e, "ry")))
        elif tag == "rect": paths.append(rect(f(e, "x"), f(e, "y"), f(e, "width"), f(e, "height"), f(e, "rx"), e.get("ry") and f(e, "ry")))
        elif tag == "line": paths.append(f"M{f(e,'x1')},{f(e,'y1')}L{f(e,'x2')},{f(e,'y2')}")
        elif tag in ("polyline", "polygon"):
            pts = re.split(r"[\s,]+", e.get("points").strip())
            pairs = [f"{pts[i]},{pts[i+1]}" for i in range(0, len(pts), 2)]
            paths.append("M" + "L".join(pairs) + ("z" if tag == "polygon" else ""))
    body = "\n".join(
        f'    <path android:pathData="{d}" android:strokeColor="#FFFFFFFF" android:strokeWidth="2" '
        f'android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>' for d in paths)
    with open(out_path, "w") as o:
        o.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Lucide icon (ISC licence), converted by tools/icons/lucide2vector.py -->\n'
                '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" '
                'android:viewportWidth="24" android:viewportHeight="24">\n' + body + "\n</vector>\n")

if __name__ == "__main__":
    src, out = sys.argv[1], sys.argv[2]
    for name in sys.argv[3:]:
        convert(f"{src}/{name}.svg", f"{out}/ic_{name.replace('-', '_')}.xml")
