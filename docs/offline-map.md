# Offline map (Settings › Offline map)

The head unit has no SIM, so the map should still work when the phone hotspot is off.
Car Hub keeps **one region** of the map on the unit in MapLibre's own offline database.

## What is downloaded

| Region | Bounds (S, W, N, E) | Zoom | Default |
|---|---|---|---|
| Haryana + Delhi NCR | 27.60, 74.40, 31.00, 77.65 | 0–13 | yes |
| Delhi NCR | 28.20, 76.80, 28.95, 77.65 | 0–14 | |
| Chandigarh tricity | 30.55, 76.60, 30.85, 76.95 | 0–14 | |
| 40 km around me | from the current GPS fix | 0–14 | |

The settings card shows the estimated size and the free space before the download starts.

## Source, licence and attribution

- **Tiles:** the same OpenFreeMap vector style that the live map already uses
  (`MapLook.Liberty`, `tiles.openfreemap.org`). The data uses the OpenMapTiles schema and comes from OpenStreetMap.
- **Data licence:** © OpenStreetMap contributors, **ODbL 1.0**. The attribution
  "Map data © OpenStreetMap contributors (ODbL) · OpenMapTiles · OpenFreeMap" is shown on the
  settings card (`x_offline_licence`), and the map keeps its own attribution.
- **Never used:** `tile.openstreetmap.org`. The OSMF tile usage policy forbids bulk or offline
  downloads from it, and the app does not reference it anywhere.

### How the download stays within fair use

The download is kept small and polite:

- **Driver-initiated only.** Nothing downloads in the background.
- **Wi-Fi only.** It stops as soon as the unit leaves Wi-Fi, because the phone hotspot may be on mobile data.
- **Capped size.** `OfflineRegions.TILE_LIMIT` is 20,000 tiles, the zoom stops at 13–14, and there is one region at a time.
- **Downloaded once.** After that, MapLibre's cache serves the tiles.

**To check before a Play release:** read OpenFreeMap's current terms (openfreemap.org) and
confirm that a one-region offline cache like this one is acceptable on its free public instance.
That page could not be fetched from the build environment while this was written, so this has
**not** been verified. If the terms do not allow it, set `OFFLINE_MAP_STYLE_URL` (below).

### Self-hosting later (no paid service)

Set the repository variable `OFFLINE_MAP_STYLE_URL` to a style that points at your own region
file, for example a Protomaps or Planetiler PMTiles extract of north India on any free static
host. The build then downloads from there. No code change is needed.

## What is not offline

- **Route search and turn-by-turn routing** still need internet. They use the `ROUTING_BASE_URL` and `GEOCODER_URL` services.
- **Speed-limit data:** there is no offline speed-limit dataset.
- **Speed cameras** were already offline before this change (`assets/cameras.json`).
