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

Open Charge Map: when the OPENCHARGEMAP_KEY environment variable is set (a CI secret), EV
chargers in India from Open Charge Map (https://openchargemap.org, CC BY 4.0) are merged in.
A charger within MERGE_RADIUS_M of an OpenStreetMap EV station is the same place and is dropped;
POIs whose data provider does not allow redistribution are skipped. Without the key this step
is skipped with a CI notice. The key is sent only as a request header and is never printed.
The app itself never calls Open Charge Map: everything is in fuel.bin.

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

OCM_POI = "https://api.openchargemap.io/v3/poi/?output=json&countrycode=IN&maxresults=200000&compact=true&verbose=false"
OCM_REFERENCE = "https://api.openchargemap.io/v3/referencedata/?countrycode=IN"
# Open Charge Map status types that are not a usable charger: planned, removed, duplicate.
OCM_SKIP_STATUS = {150, 200, 210}
MERGE_RADIUS_M = 50.0

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


def ocm_get(url, key):
    req = urllib.request.Request(url, headers={"User-Agent": "AmpliCarHub-CI/1.0", "X-API-Key": key})
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.load(r)


def fetch_ocm(key):
    """POIs and data providers from Open Charge Map, or None. Errors never show the key."""
    for attempt in range(2):
        try:
            pois = ocm_get(OCM_POI, key)
            reference = ocm_get(OCM_REFERENCE, key)
            print(f"fetch_fuel: Open Charge Map returned {len(pois)} POIs")
            return pois, reference.get("DataProviders", [])
        except Exception as e:  # noqa: BLE001
            msg = str(e).replace(key, "***") if key else str(e)
            print(f"fetch_fuel: Open Charge Map attempt {attempt + 1} failed: {msg}")
            time.sleep(5)
    return None


def provider_allows(provider):
    """False when the data provider's licence forbids redistribution (or OCM says it is not open)."""
    if provider is None:
        return True
    if provider.get("IsOpenDataLicensed") is False:
        return False
    text = " ".join(str(provider.get(k) or "") for k in ("License", "Comments")).lower()
    for bad in ("non-commercial", "noncommercial", "no redistribution", "not for redistribution",
                "may not be redistributed", "all rights reserved"):
        if bad in text:
            return False
    return True


def ocm_elements(pois, providers):
    """Open Charge Map POIs as OSM-like charging_station elements; skipped ones are counted."""
    allowed = {p.get("ID"): provider_allows(p) for p in providers}
    out, skipped = [], 0
    for poi in pois:
        if not allowed.get(poi.get("DataProviderID"), True):
            skipped += 1
            continue
        if poi.get("StatusTypeID") in OCM_SKIP_STATUS:
            continue
        a = poi.get("AddressInfo") or {}
        lat, lon = a.get("Latitude"), a.get("Longitude")
        if lat is None or lon is None:
            continue
        tags = {"amenity": "charging_station"}
        title = " ".join(str(a.get("Title") or "").split())
        if title:
            tags["name"] = title
        out.append({"lat": float(lat), "lon": float(lon), "tags": tags, "ocm": True})
    return out, skipped


def distance_m(lat1, lon1, lat2, lon2):
    """Equirectangular distance: exact enough at 50 m."""
    x = math.radians(lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2))
    y = math.radians(lat2 - lat1)
    return math.hypot(x, y) * 6371000.0


def position(e):
    if "lat" in e:
        return e["lat"], e["lon"]
    if "center" in e:
        return e["center"]["lat"], e["center"]["lon"]
    return None


def merge_ocm(osm, ocm, radius_m=MERGE_RADIUS_M):
    """OSM elements plus the Open Charge Map chargers that are not already an OSM EV station
    (or another OCM charger) within [radius_m]. Returns (merged, added)."""
    cell = 0.001  # ~110 m: neighbours within 50 m are in this or an adjacent cell
    grid = {}

    def add(lat, lon):
        grid.setdefault((math.floor(lat / cell), math.floor(lon / cell)), []).append((lat, lon))

    def near(lat, lon):
        ci, cj = math.floor(lat / cell), math.floor(lon / cell)
        for di in (-1, 0, 1):
            for dj in (-1, 0, 1):
                for p in grid.get((ci + di, cj + dj), ()):
                    if distance_m(lat, lon, p[0], p[1]) <= radius_m:
                        return True
        return False

    for e in osm:
        if flags_for(e.get("tags", {})) & EV:
            p = position(e)
            if p:
                add(*p)
    added = []
    for e in ocm:
        lat, lon = e["lat"], e["lon"]
        if near(lat, lon):
            continue
        add(lat, lon)
        added.append(e)
    return list(osm) + added, len(added)


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
    ocm_added, ocm_skipped = 0, 0
    key = os.environ.get("OPENCHARGEMAP_KEY", "").strip()
    if not key:
        print("::notice::OPENCHARGEMAP_KEY is not set; EV chargers come from OpenStreetMap only")
    else:
        got = fetch_ocm(key)
        if got is None:
            print("::notice::Open Charge Map unreachable; EV chargers come from OpenStreetMap only")
        else:
            ocm, ocm_skipped = ocm_elements(*got)
            elements, ocm_added = merge_ocm(elements, ocm)
            print(f"fetch_fuel: Open Charge Map added {ocm_added} chargers "
                  f"({len(ocm) - ocm_added} already in OpenStreetMap, {ocm_skipped} skipped for their licence)")
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
    if ocm_added:
        meta["ocm"] = ocm_added
        meta["ocm_source"] = "Open Charge Map (api.openchargemap.io), country IN"
        meta["ocm_license"] = "CC BY 4.0. EV charger data © Open Charge Map contributors"
    with open(os.path.join(ASSETS, "fuel_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)
    print(f"fetch_fuel: wrote {len(rows)} stations ({meta['cng']} CNG, {meta['ev']} EV, "
          f"{meta['assumed']} assumed petrol/diesel), {len(names)} names, {size} bytes")


def merge_check(folder):
    """Offline check for the unit test: merges the fixture files in [folder] and prints counts."""
    def load(name):
        with open(os.path.join(folder, name), encoding="utf-8") as f:
            return json.load(f)
    osm = load("osm.json")
    ocm, skipped = ocm_elements(load("ocm_poi.json"), load("ocm_providers.json"))
    merged, added = merge_ocm(osm, ocm)
    names, rows = build(merged)
    print(json.dumps({
        "ocm": len(ocm), "skipped": skipped, "added": added,
        "stations": len(rows), "ev": sum(1 for r in rows if r[4] & EV),
        "names": sorted(names),
    }, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--merge-check":
        merge_check(sys.argv[2])
        sys.exit(0)
    try:
        main()
    except Exception as e:  # noqa: BLE001 - never fail the build over station data
        print(f"fetch_fuel: error {e}; keeping the committed fuel.bin", file=sys.stderr)
    sys.exit(0)
