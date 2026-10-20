#!/usr/bin/env python3
"""
Builds app/src/main/assets/places.tsv from GeoNames (CC BY 4.0, https://www.geonames.org).
India: every populated place with population >= 1,000 (small towns matter for birth places).
Rest of the world: populated places with population >= 15,000.

Output columns (tab-separated, UTF-8, one place per line, sorted by population desc):
  name  admin1  countryCode  lat  lon  timeZone  population  alternateNames(|-separated)
Run in CI (GitHub Actions) — the workspace that writes the app cannot reach geonames.org.
"""
import csv, io, sys, unicodedata, urllib.request, zipfile

BASE = "https://download.geonames.org/export/dump/"

def fetch(name):
    with urllib.request.urlopen(BASE + name, timeout=120) as r:
        return r.read()

def rows_from_zip(zname, inner):
    z = zipfile.ZipFile(io.BytesIO(fetch(zname)))
    with z.open(inner) as f:
        for line in io.TextIOWrapper(f, encoding="utf-8"):
            yield line.rstrip("\n").split("\t")

def is_ascii_latin(s):
    return all(ord(c) < 128 for c in s) and any(c.isalpha() for c in s)

def main(out_path):
    admin1 = {}
    for line in fetch("admin1CodesASCII.txt").decode("utf-8").splitlines():
        p = line.split("\t")
        if len(p) >= 3:
            admin1[p[0]] = p[2]  # ascii name
    places = {}
    def add(r):
        gid, name, asciiname, alts, lat, lon, fclass, fcode, cc = r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8]
        if fclass != "P" or fcode in ("PPLH", "PPLQ", "PPLW"):  # historical / abandoned / destroyed
            return
        tz = r[17]
        if not tz:
            return
        pop = int(r[14] or 0)
        display = asciiname if asciiname else name
        altlist = []
        if pop >= 50000:
            seen = {display.lower()}
            for a in alts.split(","):
                a = a.strip()
                if 3 <= len(a) <= 40 and is_ascii_latin(a) and a.lower() not in seen and not a.startswith("http"):
                    seen.add(a.lower()); altlist.append(a)
                if len(altlist) >= 4:
                    break
        if name != display and is_ascii_latin(name):
            altlist.insert(0, name)
        places[gid] = (display, admin1.get(f"{cc}.{r[10]}", ""), cc, f"{float(lat):.4f}", f"{float(lon):.4f}", tz, pop, "|".join(altlist))
    for r in rows_from_zip("cities1000.zip", "cities1000.txt"):
        if r[8] == "IN":
            add(r)
    for r in rows_from_zip("cities15000.zip", "cities15000.txt"):
        if r[8] != "IN":
            add(r)
    out = sorted(places.values(), key=lambda p: -p[6])
    with open(out_path, "w", encoding="utf-8") as f:
        for p in out:
            f.write("\t".join(str(x).replace("\t", " ") for x in p) + "\n")
    india = sum(1 for p in out if p[2] == "IN")
    print(f"wrote {len(out)} places ({india} in India) to {out_path}")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/places.tsv")
