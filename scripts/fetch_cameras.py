#!/usr/bin/env python3
"""Builds app/src/main/assets/cameras.json from OpenStreetMap speed cameras in India.

Data: (c) OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright).
The generated file is a derived database and stays under the ODbL.

Reads, through the Overpass API:
  * nodes tagged highway=speed_camera
  * nodes tagged enforcement=maxspeed (or average_speed)
  * relations type=enforcement + enforcement=maxspeed/average_speed, using their "device"
    members (the camera nodes) and the relation's maxspeed when the node has none.

Writes a compact JSON array of {id, lat, lon, maxspeed, dir, type} plus cameras_meta.json.
Never fails the build: on any error the committed files are left as they are and it exits 0.
"""
import datetime
import json
import os
import re
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
[out:json][timeout:170];
area["ISO3166-1"="IN"][admin_level=2]->.in;
(
  node["highway"="speed_camera"](area.in);
  node["enforcement"~"^(maxspeed|average_speed)$"](area.in);
  relation["type"="enforcement"]["enforcement"~"^(maxspeed|average_speed)$"](area.in);
);
out body;
>;
out body qt;
"""

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")

CARDINAL = {
    "N": 0, "NNE": 22.5, "NE": 45, "ENE": 67.5, "E": 90, "ESE": 112.5, "SE": 135, "SSE": 157.5,
    "S": 180, "SSW": 202.5, "SW": 225, "WSW": 247.5, "W": 270, "WNW": 292.5, "NW": 315, "NNW": 337.5,
}


def fetch():
    body = urllib.parse.urlencode({"data": QUERY}).encode()
    for url in ENDPOINTS:
        for attempt in range(2):
            try:
                req = urllib.request.Request(url, data=body, headers={"User-Agent": "AmpliCarHub-CI/1.0"})
                with urllib.request.urlopen(req, timeout=180) as r:
                    data = json.load(r)
                if "elements" in data:
                    print(f"fetch_cameras: {url} returned {len(data['elements'])} elements")
                    return data["elements"]
            except Exception as e:  # noqa: BLE001 - any failure means "try the next mirror"
                print(f"fetch_cameras: {url} attempt {attempt + 1} failed: {e}")
                time.sleep(5)
    return None


def parse_speed(value):
    """'60', '60 km/h', '40 mph', '60;80' -> km/h as int, or None ('IN:urban', 'signals')."""
    if not value:
        return None
    m = re.match(r"^\s*(\d+(?:\.\d+)?)\s*(mph|km/h|kmh|kph)?", value.split(";")[0])
    if not m:
        return None
    v = float(m.group(1))
    if m.group(2) == "mph":
        v *= 1.609344
    v = int(round(v))
    return v if 5 <= v <= 200 else None


def parse_dir(value):
    """'90', '270;90' (first), 'NE' -> bearing; 'forward'/'backward'/'both' -> None."""
    if not value:
        return None
    first = value.split(";")[0].strip().upper()
    if first in CARDINAL:
        return float(CARDINAL[first])
    try:
        return float(first) % 360.0
    except ValueError:
        return None


def camera_type(tags):
    if tags.get("enforcement") == "average_speed" or tags.get("speed_camera") == "average_speed":
        return "average_speed"
    return "speed_camera"


def build(elements):
    nodes = {e["id"]: e for e in elements if e.get("type") == "node" and "lat" in e}
    cams = {}

    def add(node, extra_tags=None):
        tags = node.get("tags", {})
        extra = extra_tags or {}
        maxspeed = parse_speed(tags.get("maxspeed")) or parse_speed(extra.get("maxspeed"))
        direction = parse_dir(tags.get("direction")) if "direction" in tags else parse_dir(tags.get("camera:direction"))
        key = f"n{node['id']}"
        old = cams.get(key)
        cams[key] = {
            "id": key,
            "lat": round(node["lat"], 6),
            "lon": round(node["lon"], 6),
            "maxspeed": maxspeed if maxspeed is not None else (old or {}).get("maxspeed"),
            "dir": direction if direction is not None else (old or {}).get("dir"),
            "type": camera_type({**extra, **tags}),
        }

    for n in nodes.values():
        tags = n.get("tags", {})
        if tags.get("highway") == "speed_camera" or tags.get("enforcement") in ("maxspeed", "average_speed"):
            add(n)

    for rel in (e for e in elements if e.get("type") == "relation"):
        rtags = rel.get("tags", {})
        for m in rel.get("members", []):
            if m.get("type") == "node" and m.get("role") == "device" and m.get("ref") in nodes:
                add(nodes[m["ref"]], rtags)

    return sorted(cams.values(), key=lambda c: c["id"])


def main():
    elements = fetch()
    if elements is None:
        print("fetch_cameras: Overpass unreachable; keeping the committed cameras.json")
        return
    cams = build(elements)
    if not cams:
        print("fetch_cameras: no cameras in the response; keeping the committed cameras.json")
        return
    with open(os.path.join(ASSETS, "cameras.json"), "w", encoding="utf-8") as f:
        json.dump(cams, f, separators=(",", ":"))
    meta = {
        "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d"),
        "count": len(cams),
        "source": "OpenStreetMap (Overpass API)",
        "license": "ODbL 1.0. Speed camera data © OpenStreetMap contributors",
    }
    with open(os.path.join(ASSETS, "cameras_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)
    with_limit = sum(1 for c in cams if c["maxspeed"] is not None)
    with_dir = sum(1 for c in cams if c["dir"] is not None)
    print(f"fetch_cameras: wrote {len(cams)} cameras for India ({with_limit} with maxspeed, {with_dir} with direction)")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001 - never fail the build over camera data
        print(f"fetch_cameras: error {e}; keeping the committed cameras.json", file=sys.stderr)
    sys.exit(0)
