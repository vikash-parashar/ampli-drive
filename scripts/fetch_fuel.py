#!/usr/bin/env python3
"""Builds app/src/main/assets/fuel.bin from OpenStreetMap fuel and charging stations in India.

Data: (c) OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright).
The generated file is a derived database and stays under the ODbL.

Reads, through the Overpass API, every amenity=fuel and amenity=charging_station (nodes, ways
and relations, as their centre point) and keeps, per station: position, what it sells and a
short name. fuel:cng / fuel:lpg / fuel:diesel / fuel:octane_* tags decide the fuel types; a name
or brand that says CNG (or a city-gas brand) means CNG. A pump without any fuel:* tag is shown
as petrol and diesel, marked "likely" in the app.

fuel.bin (big-endian, read by FuelSnapshot.read):
  "AFS1", int generated (yyyymmdd),
  int nameCount, then per name: u16 byte length + UTF-8 bytes,
  int count, then per station: int latE5, int lonE5, u8 flags, u16 name index (0xFFFF none),
  stations sorted by 0.05-degree cell (lat cell, then lon cell) so the app needs no sort.
Flags: 1 CNG, 2 petrol, 4 diesel, 8 EV charging, 16 LPG, 32 open 24 h, 64 types assumed.

Also writes fuel_meta.json. Never fails the build: on any error the committed files are left
as they are and it exits 0.
"""
import datetime
import json
import math
import os
import struct
import sys
import time
import urllib.parse
import urllib.request

ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]

QUERY = """
[out:json][timeout:240];
area["ISO3166-1"="IN"][admin_level=2]->.in;
(
  nwr["amenity"="fuel"](area.in);
  nwr["amenity"="charging_station"](area.in);
);
out center tags;
"""

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")

CNG, PETROL, DIESEL, EV, LPG, OPEN_24H, ASSUMED = 1, 2, 4, 8, 16, 32, 64
CELL_DEG = 0.05
MAX_NAME = 48
NO_NAME = 0xFFFF
# City-gas companies whose outlets are CNG stations even when OSM has no fuel:cng tag.
CNG_BRANDS = ("indraprastha gas", "igl", "mahanagar gas", "mgl", "gujarat gas", "adani gas",
              "adani total gas", "green gas", "torrent gas", "think gas", "megha gas", "bhagyanagar gas",
              "haryana city gas", "avantika gas", "central u.p. gas", "sabarmati gas", "agp cgd")


def fetch():
    body = urllib.parse.urlencode({"data": QUERY}).encode()
    for url in ENDPOINTS:
        for attempt in range(2):
            try:
                req = urllib.request.Request(url, data=body, headers={"User-Agent": "AmpliCarHub-CI/1.0"})
                with urllib.request.urlopen(req, timeout=300) as r:
                    data = json.load(r)
                if "elements" in data:
                    print(f"fetch_fuel: {url} returned {len(data['elements'])} elements")
                    return data["elements"]
            except Exception as e:  # noqa: BLE001 - any failure means "try the next mirror"
                print(f"fetch_fuel: {url} attempt {attempt + 1} failed: {e}")
                time.sleep(5)
    return None


def yes(tags, key):
    return tags.get(key, "").strip().lower() in ("yes", "only", "true", "1")


def flags_for(tags):
    f = 0
    text = " ".join(tags.get(k, "") for k in ("name", "name:en", "brand", "operator")).lower()
    if tags.get("amenity") == "charging_station" or yes(tags, "fuel:electricity"):
        f |= EV
    if tags.get("amenity") == "fuel":
        if yes(tags, "fuel:cng") or "cng" in text.split() or "cng" in text or any(b in text for b in CNG_BRANDS):
            f |= CNG
        if yes(tags, "fuel:lpg") or yes(tags, "fuel:autogas"):
            f |= LPG
        if yes(tags, "fuel:diesel") or yes(tags, "fuel:HGV_diesel"):
            f |= DIESEL
        if any(yes(tags, k) for k in tags if k.startswith("fuel:octane_")) or yes(tags, "fuel:petrol"):
            f |= PETROL
        has_fuel_tags = any(k.startswith("fuel:") for k in tags)
        if not (f & (CNG | LPG | DIESEL | PETROL)) and not has_fuel_tags:
            # A plain pump in India sells petrol and diesel; say so, but mark it as a guess.
            f |= PETROL | DIESEL | ASSUMED
    if tags.get("opening_hours", "").strip() == "24/7":
        f |= OPEN_24H
    return f


def short_name(tags):
    for k in ("name:en", "name", "brand", "operator"):
        v = " ".join(tags.get(k, "").split())
        if v:
            return v[:MAX_NAME]
    return ""


def build(elements):
    names, index, rows, seen = [], {}, [], set()
    for e in elements:
        tags = e.get("tags", {})
        if "lat" in e:
            lat, lon = e["lat"], e["lon"]
        elif "center" in e:
            lat, lon = e["center"]["lat"], e["center"]["lon"]
        else:
            continue
        flags = flags_for(tags)
        if flags & (CNG | PETROL | DIESEL | EV | LPG) == 0:
            continue
        la, lo = int(round(lat * 1e5)), int(round(lon * 1e5))
        key = (la // 20, lo // 20, flags & EV)  # one station per ~20 m and kind (node + building)
        if key in seen:
            continue
        seen.add(key)
        name = short_name(tags)
        ni = NO_NAME
        if name:
            if name not in index and len(names) < NO_NAME - 1:
                index[name] = len(names)
                names.append(name)
            ni = index.get(name, NO_NAME)
        rows.append((math.floor(la / 1e5 / CELL_DEG), math.floor(lo / 1e5 / CELL_DEG), la, lo, flags, ni))
    rows.sort()
    return names, rows


def write(names, rows, generated, path):
    out = bytearray(b"AFS1")
    out += struct.pack(">ii", generated, len(names))
    for n in names:
        b = n.encode("utf-8")[:1000]
        out += struct.pack(">H", len(b)) + b
    out += struct.pack(">i", len(rows))
    for _, _, la, lo, flags, ni in rows:
        out += struct.pack(">iiBH", la, lo, flags, ni)
    with open(path, "wb") as f:
        f.write(out)
    return len(out)


def main():
    elements = fetch()
    if elements is None:
        print("fetch_fuel: Overpass unreachable; keeping the committed fuel.bin")
        return
    names, rows = build(elements)
    if not rows:
        print("fetch_fuel: no stations in the response; keeping the committed fuel.bin")
        return
    now = datetime.datetime.now(datetime.timezone.utc)
    size = write(names, rows, int(now.strftime("%Y%m%d")), os.path.join(ASSETS, "fuel.bin"))
    count = lambda bit: sum(1 for r in rows if r[4] & bit)  # noqa: E731
    meta = {
        "generated": now.strftime("%Y-%m-%d"),
        "count": len(rows),
        "cng": count(CNG),
        "ev": count(EV),
        "assumed": count(ASSUMED),
        "bytes": size,
        "source": "OpenStreetMap (Overpass API): amenity=fuel, amenity=charging_station",
        "license": "ODbL 1.0. Fuel and charging station data © OpenStreetMap contributors",
    }
    with open(os.path.join(ASSETS, "fuel_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)
    print(f"fetch_fuel: wrote {len(rows)} stations ({meta['cng']} CNG, {meta['ev']} EV, "
          f"{meta['assumed']} assumed petrol/diesel), {len(names)} names, {size} bytes")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001 - never fail the build over station data
        print(f"fetch_fuel: error {e}; keeping the committed fuel.bin", file=sys.stderr)
    sys.exit(0)
