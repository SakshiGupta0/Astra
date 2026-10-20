#!/usr/bin/env python3
"""
ASTRA golden-fixture generator.

Builds independent reference values with the Swiss Ephemeris `swetest` tool
(Lahiri ayanamsha, SIDM 1). Swiss Ephemeris is used ONLY here, as a test
reference; it is not part of the app. Its source is not in this repo.

Usage:
    SWETEST=/path/to/swetest SWEPH=/path/to/ephe python3 make_fixtures.py OUT_DIR

Outputs (tab-separated):
    natal.tsv     30 natal charts: sidereal Sun..Saturn, mean Rahu, Lagna
    ingress.tsv   sign changes for Sun, Mars, Mercury, Jupiter, Venus, Saturn, Rahu (day-level)
    stations.tsv  retrograde / direct stations for Mercury..Saturn (day-level)
    panchang.tsv  sunrise and tithi / nakshatra / yoga at sunrise for 12 date-city pairs
"""
import os, subprocess, sys
from datetime import datetime, date, timedelta, timezone
from zoneinfo import ZoneInfo

SWETEST = os.environ.get("SWETEST", "swetest")
EPHE = os.environ.get("SWEPH", ".")

def swe(args):
    out = subprocess.run([SWETEST] + args + ["-head", f"-edir{EPHE}"], capture_output=True, text=True, check=True).stdout
    return out

PLACES = {
    "Delhi": (28.6139, 77.2090, "Asia/Kolkata"), "Mumbai": (19.0760, 72.8777, "Asia/Kolkata"),
    "Kolkata": (22.5726, 88.3639, "Asia/Kolkata"), "Chennai": (13.0827, 80.2707, "Asia/Kolkata"),
    "Bengaluru": (12.9716, 77.5946, "Asia/Kolkata"), "Hyderabad": (17.3850, 78.4867, "Asia/Kolkata"),
    "Jaipur": (26.9124, 75.7873, "Asia/Kolkata"), "Lucknow": (26.8467, 80.9462, "Asia/Kolkata"),
    "Pune": (18.5204, 73.8567, "Asia/Kolkata"), "Ahmedabad": (23.0225, 72.5714, "Asia/Kolkata"),
    "Guwahati": (26.1445, 91.7362, "Asia/Kolkata"), "Srinagar": (34.0837, 74.7973, "Asia/Kolkata"),
    "Kochi": (9.9312, 76.2673, "Asia/Kolkata"), "Lahore": (31.5204, 74.3587, "Asia/Karachi"),
    "New York": (40.7128, -74.0060, "America/New_York"), "London": (51.5074, -0.1278, "Europe/London"),
    "Dubai": (25.2048, 55.2708, "Asia/Dubai"), "Singapore": (1.3521, 103.8198, "Asia/Singapore"),
    "Sydney": (-33.8688, 151.2093, "Australia/Sydney"), "Tromso": (69.6492, 18.9553, "Europe/Oslo"),
    "San Francisco": (37.7749, -122.4194, "America/Los_Angeles"), "Toronto": (43.6532, -79.3832, "America/Toronto"),
}

# id, place, local datetime — chosen to cover eras, time-zone history, DST, latitudes and edge times
NATAL = [
    ("N01", "Delhi", "1947-08-15 00:00"), ("N02", "Mumbai", "1985-03-21 06:15"), ("N03", "Kolkata", "1943-07-10 14:30"),
    ("N04", "Chennai", "1999-12-31 23:59"), ("N05", "Bengaluru", "1995-02-14 11:05"), ("N06", "Hyderabad", "2000-02-29 04:40"),
    ("N07", "Jaipur", "1962-10-20 18:45"), ("N08", "Lucknow", "1975-06-26 02:20"), ("N09", "Pune", "2010-08-08 08:08"),
    ("N10", "Ahmedabad", "1931-01-26 09:30"), ("N11", "Guwahati", "1989-11-09 21:10"), ("N12", "Srinagar", "2004-04-04 16:00"),
    ("N13", "Kochi", "1971-12-16 05:55"), ("N14", "Lahore", "1940-03-23 12:00"), ("N15", "New York", "1992-07-04 15:30"),
    ("N16", "New York", "2021-03-14 03:30"), ("N17", "London", "1966-07-30 15:00"), ("N18", "Dubai", "2008-09-09 09:09"),
    ("N19", "Singapore", "2015-08-09 20:00"), ("N20", "Sydney", "1988-01-26 07:45"), ("N21", "Tromso", "1994-12-21 12:00"),
    ("N22", "San Francisco", "1977-05-25 10:30"), ("N23", "Toronto", "2001-09-11 08:46"), ("N24", "Delhi", "1910-05-18 10:00"),
    ("N25", "Mumbai", "1952-01-01 00:01"), ("N26", "Kolkata", "2024-01-22 12:29"), ("N27", "Bengaluru", "1993-10-20 19:26"),
    ("N28", "Chennai", "1968-02-29 13:13"), ("N29", "Delhi", "2023-08-23 18:04"), ("N30", "London", "1953-06-02 11:15"),
]

def utc_of(place, local):
    lat, lon, zone = PLACES[place]
    dt = datetime.strptime(local, "%Y-%m-%d %H:%M").replace(tzinfo=ZoneInfo(zone))
    return dt.astimezone(timezone.utc)

def natal():
    rows = ["id\tplace\tlocal\tlat\tlon\tzone\tutc\tsun\tmoon\tmercury\tvenus\tmars\tjupiter\tsaturn\trahu\tasc"]
    for cid, place, local in NATAL:
        lat, lon, zone = PLACES[place]
        u = utc_of(place, local)
        out = swe([f"-b{u.day}.{u.month}.{u.year}", f"-ut{u.hour}:{u.minute:02d}:{u.second:02d}", "-p0123456m", "-sid1", "-fPl",
                   f"-house{lon},{lat},W"])
        vals = {}
        for line in out.splitlines():
            parts = line.split()
            if not parts: continue
            if line.startswith("mean Node"): vals["rahu"] = float(parts[2])
            elif line.startswith("Ascendant"): vals["asc"] = float(parts[1])
            elif parts[0] in ("Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn"): vals[parts[0].lower()] = float(parts[1])
        rows.append("\t".join([cid, place, local, str(lat), str(lon), zone, u.strftime("%Y-%m-%dT%H:%M:%SZ")] +
                              [f"{vals[k]:.7f}" for k in ["sun", "moon", "mercury", "venus", "mars", "jupiter", "saturn", "rahu", "asc"]]))
    return rows

def daily(planet_code, start, days):
    out = swe([f"-b{start.day}.{start.month}.{start.year}", "-ut0", f"-n{days}", "-s1", f"-p{planet_code}", "-sid1", "-fTls"])
    res = []
    for line in out.splitlines():
        p = line.split()
        if len(p) >= 3:
            d = datetime.strptime(p[0], "%d.%m.%Y").date()
            res.append((d, float(p[1]), float(p[2])))
    return res

def ingress_and_stations(start=date(2026, 10, 1), days=820):
    ing = ["graha\tday_before_utc\tfrom_sign\tto_sign"]
    st = ["graha\tday_before_utc\tkind"]
    codes = {"SUN": "0", "MARS": "4", "MERCURY": "2", "JUPITER": "5", "VENUS": "3", "SATURN": "6", "RAHU": "m"}
    for g, c in codes.items():
        series = daily(c, start, days)
        for (d1, l1, s1), (d2, l2, s2) in zip(series, series[1:]):
            if int(l1 // 30) != int(l2 // 30):
                ing.append(f"{g}\t{d1.isoformat()}\t{int(l1 // 30)}\t{int(l2 // 30)}")
            if g not in ("SUN", "RAHU") and (s1 < 0) != (s2 < 0):
                st.append(f"{g}\t{d1.isoformat()}\t{'RETRO' if s2 < 0 else 'DIRECT'}")
    return ing, st

PANCHANG = [("2026-11-01", "Bengaluru"), ("2026-11-08", "Delhi"), ("2026-12-25", "Mumbai"), ("2027-01-14", "Kolkata"),
            ("2027-03-22", "Chennai"), ("2027-04-14", "Hyderabad"), ("2027-06-21", "Srinagar"), ("2027-08-15", "Guwahati"),
            ("2027-10-29", "Jaipur"), ("2028-02-29", "Kochi"), ("2026-10-20", "London"), ("2027-07-04", "New York")]

def panchang():
    rows = ["date\tplace\tlat\tlon\tzone\tsunrise_utc\tsunset_utc\ttithi\tnakshatra\tyoga"]
    for ds, place in PANCHANG:
        lat, lon, zone = PLACES[place]
        d = date.fromisoformat(ds)
        midnight_utc = datetime(d.year, d.month, d.day, tzinfo=ZoneInfo(zone)).astimezone(timezone.utc)
        out = subprocess.run([SWETEST, "-rise", f"-b{midnight_utc.day}.{midnight_utc.month}.{midnight_utc.year}",
                              f"-ut{midnight_utc.hour}:{midnight_utc.minute:02d}", f"-geopos{lon},{lat},0", "-p0", "-n1", f"-edir{EPHE}"],
                             capture_output=True, text=True, check=True).stdout
        line = [l for l in out.splitlines() if l.startswith("rise")][0].split()
        rise = datetime.strptime(f"{line[1]} {line[2]}", "%d.%m.%Y %H:%M:%S.%f").replace(tzinfo=timezone.utc)
        sset = datetime.strptime(f"{line[4]} {line[5]}", "%d.%m.%Y %H:%M:%S.%f").replace(tzinfo=timezone.utc)
        pos = swe([f"-b{rise.day}.{rise.month}.{rise.year}", f"-ut{rise.hour}:{rise.minute:02d}:{rise.second:02d}", "-p01", "-fPl"])
        trop = {l.split()[0]: float(l.split()[1]) for l in pos.splitlines() if l.strip()}
        sid = swe([f"-b{rise.day}.{rise.month}.{rise.year}", f"-ut{rise.hour}:{rise.minute:02d}:{rise.second:02d}", "-p01", "-sid1", "-fPl"])
        sd = {l.split()[0]: float(l.split()[1]) for l in sid.splitlines() if l.strip()}
        tithi = int(((trop["Moon"] - trop["Sun"]) % 360) // 12)
        nak = int(sd["Moon"] // (360 / 27))
        yoga = int(((sd["Sun"] + sd["Moon"]) % 360) // (360 / 27))
        rows.append("\t".join([ds, place, str(lat), str(lon), zone, rise.strftime("%Y-%m-%dT%H:%M:%SZ"),
                               sset.strftime("%Y-%m-%dT%H:%M:%SZ"), str(tithi), str(nak), str(yoga)]))
    return rows

if __name__ == "__main__":
    outdir = sys.argv[1]
    os.makedirs(outdir, exist_ok=True)
    ing, st = ingress_and_stations()
    for name, rows in [("natal.tsv", natal()), ("ingress.tsv", ing), ("stations.tsv", st), ("panchang.tsv", panchang())]:
        with open(os.path.join(outdir, name), "w") as f:
            f.write("\n".join(rows) + "\n")
        print(name, len(rows) - 1, "rows")
