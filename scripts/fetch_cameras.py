#!/usr/bin/env python3
"""Builds app/src/main/assets/cameras.json from OpenStreetMap speed cameras in India.

Data: (c) OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright).
The generated file is a derived database and stays under the ODbL.

Reads, through the Overpass API:
  * nodes tagged highway=speed_camera
  * nodes tagged enforcement=maxspeed (or average_speed)
  * relations type=enforcement + enforcement=maxspeed/average_speed, using their "device"
    members (the camera nodes) and the relation's maxspeed when the node has none.
  * red-light cameras: nodes and relations with enforcement=traffic_signals (the relation's
    "device" members, else its "force" member, the signal itself), written with type "red_light".
  * average-speed zones: enforcement=average_speed relations with a start and an end point
    ("from"/"to" nodes, else the first and last "device"), written to speed_zones.json.

Writes a compact JSON array of {id, lat, lon, maxspeed, dir, type} plus cameras_meta.json, and
speed_zones.json as [{id, a: [lat, lon], b: [lat, lon], maxspeed, name}].
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
  node["enforcement"~"^(maxspeed|average_speed|traffic_signals)$"](area.in);
  relation["type"="enforcement"]["enforcement"~"^(maxspeed|average_speed|traffic_signals)$"](area.in);
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
    if tags.get("enforcement") == "traffic_signals":
        return "red_light"
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
        if tags.get("highway") == "speed_camera" or tags.get("enforcement") in ("maxspeed", "average_speed", "traffic_signals"):
            add(n)

    for rel in (e for e in elements if e.get("type") == "relation"):
        rtags = rel.get("tags", {})
        members = [m for m in rel.get("members", []) if m.get("type") == "node" and m.get("ref") in nodes]
        devices = [m for m in members if m.get("role") == "device"]
        if not devices and rtags.get("enforcement") == "traffic_signals":
            # Red-light relations often point only at the signal ("force"); warn at the signal.
            devices = [m for m in members if m.get("role") == "force"]
        for m in devices:
            add(nodes[m["ref"]], rtags)

    return sorted(cams.values(), key=lambda c: c["id"])


def build_zones(elements):
    """Average-speed sections: one entry per relation with a usable start and end."""
    nodes = {e["id"]: e for e in elements if e.get("type") == "node" and "lat" in e}
    zones = []
    for rel in (e for e in elements if e.get("type") == "relation"):
        rtags = rel.get("tags", {})
        if rtags.get("enforcement") != "average_speed":
            continue
        members = [m for m in rel.get("members", []) if m.get("type") == "node" and m.get("ref") in nodes]
        start = next((m for m in members if m.get("role") == "from"), None)
        end = next((m for m in members if m.get("role") == "to"), None)
        if start is None or end is None:
            devices = [m for m in members if m.get("role") == "device"]
            if len(devices) < 2:
                continue
            start, end = devices[0], devices[-1]
        a, b = nodes[start["ref"]], nodes[end["ref"]]
        if a["id"] == b["id"]:
            continue
        maxspeed = parse_speed(rtags.get("maxspeed")) or parse_speed(a.get("tags", {}).get("maxspeed"))
        zones.append({
            "id": f"r{rel['id']}",
            "a": [round(a["lat"], 6), round(a["lon"], 6)],
            "b": [round(b["lat"], 6), round(b["lon"], 6)],
            "maxspeed": maxspeed,
            "name": (rtags.get("name") or rtags.get("description") or "")[:60],
        })
    return sorted(zones, key=lambda z: z["id"])


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
    zones = build_zones(elements)
    with open(os.path.join(ASSETS, "speed_zones.json"), "w", encoding="utf-8") as f:
        json.dump(zones, f, separators=(",", ":"), ensure_ascii=False)
    meta = {
        "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d"),
        "count": len(cams),
        "red_light": sum(1 for c in cams if c["type"] == "red_light"),
        "average_speed_zones": len(zones),
        "source": "OpenStreetMap (Overpass API)",
        "license": "ODbL 1.0. Speed camera data © OpenStreetMap contributors",
    }
    with open(os.path.join(ASSETS, "cameras_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)
    with_limit = sum(1 for c in cams if c["maxspeed"] is not None)
    with_dir = sum(1 for c in cams if c["dir"] is not None)
    print(f"fetch_cameras: wrote {len(cams)} cameras for India ({with_limit} with maxspeed, {with_dir} with direction, {meta['red_light']} red-light) and {len(zones)} average-speed zones")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001 - never fail the build over camera data
        print(f"fetch_cameras: error {e}; keeping the committed cameras.json", file=sys.stderr)
    sys.exit(0)
