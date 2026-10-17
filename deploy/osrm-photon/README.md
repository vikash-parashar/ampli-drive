# Self-hosted routing and place search (India)

Car Hub is a paid app, so it must not use the free public servers it used during testing:

| What | Test default (debug builds only) | Why it can't ship |
|---|---|---|
| Routing | `router.project-osrm.org` (OSRM demo) | Demo server: ~1 request/s, no SLA, its usage policy forbids production apps |
| Place search | `photon.komoot.io` | Fair-use public instance, no SLA, can block the app at any time |
| Weather | `api.open-meteo.com` | Free API is **non-commercial only** (see "Weather" below) |
| Map fallback | `tile.openstreetmap.org` | **Removed.** The OSMF tile policy forbids distributed apps without permission |

Since this change, **a release build fails** unless `ROUTING_BASE_URL` and `GEOCODER_URL` are set
(`checkReleaseEndpoints` in `app/build.gradle.kts`). Debug/test builds keep the demo defaults.

This folder runs both servers on one EC2 instance with Docker Compose:

- **OSRM** (`osrm-backend`, car profile, MLD) on the India extract from Geofabrik.
- **Photon** (komoot, Apache-2.0) with the prebuilt India index.
- **Caddy** for HTTPS (Let's Encrypt) and to expose only `/route/v1/driving/*`, `/api` and the
  remote-config file. Access logs drop coordinates and client IPs.

Nothing here creates AWS resources by itself. You run every step.

---

## 1. Sizing

| | RAM at runtime | Disk | Notes |
|---|---|---|---|
| OSRM India, car, MLD | ~6–9 GB | ~12 GB | Preprocessing (`osrm-extract`) peaks at ~20–24 GB |
| Photon India | ~3–5 GB (heap 3 GB + page cache) | ~10–15 GB | Prebuilt dump, no Nominatim import needed |
| Caddy | < 100 MB | – | |

**Recommended instance:** one **r6a.large / r6i.large** (2 vCPU, 16 GB, x86) in **ap-south-1
(Mumbai)**, 80 GB gp3 EBS, an Elastic IP. x86 is the safe choice because not every
`osrm-backend` image tag is published for arm64; if `docker manifest inspect
ghcr.io/project-osrm/osrm-backend:v5.27.1` lists `arm64`, an **r7g.large** (Graviton) is ~15%
cheaper.

The instance cannot preprocess India in 16 GB without help. Either:

- add a **16 GB swap file** for the one-off build (slow, ~1–2 h, but no extra instance), or
- build the graph on a temporary **r6a.2xlarge (64 GB) spot** instance (~20–40 min, well under
  US$1), then copy `data/` across (see 5b).

## 2. Cost estimate (ap-south-1, on-demand, Oct 2026) [VERIFY in the AWS Pricing Calculator]

| Item | Monthly |
|---|---|
| r6a.large on-demand (~US$0.11–0.13/h × 730 h) | ~US$80–95 |
| …or with a 1-year Compute Savings Plan (~35% off) | ~US$52–62 |
| 80 GB gp3 EBS | ~US$7 |
| Elastic IP (attached; public IPv4 is billed) | ~US$3.6 |
| Data out: a route response is ~10–40 KB, a search ~2 KB. 100k routes/day ≈ 60–120 GB/month; the first 100 GB/month to the internet is free | US$0–5 |
| EBS snapshot of `data/` (optional) | ~US$1 |
| One-off graph build on a spot r6a.2xlarge | < US$1 per build |
| **Total** | **~US$90–110 on-demand, ~US$65–75 with a Savings Plan** (≈ ₹5,500–9,200) |

The business plan budgeted ~US$78/month, which matches the Savings Plan figure. At the ₹1,499 launch price per Pro
sale (about ₹1,274 after Play's 15%), roughly 5–8 sales a month cover the server (4–7 at the
later ₹2,500, about ₹2,125 after Play's cut).

Cheaper later: a Hetzner/OVH dedicated box with 32–64 GB RAM runs both for €40–60/month, but
latency from India is worse (EU) and it's outside AWS.

## 3. Prerequisites

1. AWS account, a key pair, and a domain you control (e.g. `ampliapps.in`).
2. Two DNS names: `route.ampliapps.in` and `geo.ampliapps.in`.

## 4. Launch the instance

1. EC2 › Launch instance: **Ubuntu 24.04 LTS (x86_64)**, `r6a.large`, 80 GB **gp3** root volume.
2. Security group: inbound **22** (your IP only), **80** and **443** (0.0.0.0/0, ::/0). Nothing else.
   OSRM (5000) and Photon (2322) are never opened; Caddy reaches them on the Docker network.
3. Allocate an **Elastic IP** and associate it. Create DNS **A records** for both hosts → that IP.
4. SSH in and install Docker:

   ```sh
   sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
   sudo usermod -aG docker $USER && newgrp docker
   ```

## 5. Build the data

```sh
git clone https://github.com/vikash-parashar/ampli-drive.git && cd ampli-drive/deploy/osrm-photon
cp .env.example .env && nano .env          # hosts, ACME email
```

### 5a. On the instance itself (with swap)

```sh
sudo fallocate -l 16G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
./prepare.sh                                # ~1–2 h: download India, extract/partition/customize, Photon index
sudo swapoff /swapfile && sudo rm /swapfile # optional, after the build
```

### 5b. Or on a temporary big spot instance

Launch an `r6a.2xlarge` **spot** instance with a 60 GB disk, install Docker, run `./prepare.sh`,
then copy the result and terminate it:

```sh
rsync -avz --progress data/ ubuntu@route.ampliapps.in:ampli-drive/deploy/osrm-photon/data/
```

## 6. Run

```sh
docker compose up -d --build
docker compose ps                     # osrm should turn "healthy" after ~1–2 min
curl -s "https://route.ampliapps.in/route/v1/driving/77.2090,28.6139;76.6066,28.8955?overview=false" | head -c 300
curl -s "https://geo.ampliapps.in/api/?q=Rohtak&limit=1" | head -c 300
```

## 7. Point the app at it

GitHub › repo › Settings › Secrets and variables › Actions › **Variables**:

| Variable | Value |
|---|---|
| `ROUTING_BASE_URL` | `https://route.ampliapps.in` |
| `GEOCODER_URL` | `https://geo.ampliapps.in/api/` |
| `REMOTE_CONFIG_URL` | `https://route.ampliapps.in/config/carhub.json` |
| `WEATHER_URL` / `WEATHER_API_KEY` | see "Weather" (leave empty until decided: weather alerts are then off) |
| `MAP_FALLBACK_STYLE_URL` | optional, your own style JSON (e.g. PMTiles on S3 + CloudFront) |

Every CI job that builds a **release** (AAB) must pass these as environment variables, or the build
stops with "Release build needs your own servers". A CI smoke test that only checks R8 may set
`ALLOW_UNCONFIGURED_SERVERS=true`; that APK has routing and search switched off (never a demo
server) and must not be published.

### Remote config

`public/config/carhub.json` is served at `https://ROUTE_HOST/config/carhub.json`. The app fetches
it at most every 12 hours with a plain GET (no device or install id, no analytics), caches it, and
falls back to the BuildConfig values. Only `https://` URLs are accepted. To move a server without
an app update, edit the file on the instance; apps pick it up within 12 hours.

```json
{ "schema": 1,
  "routing_base_url": "https://route.ampliapps.in",
  "geocoder_url": "https://geo.ampliapps.in/api/",
  "weather_url": "", "weather_api_key": "",
  "map_fallback_style_url": "" }
```

## 8. Keep it fresh and healthy

- **Monthly data refresh** (OSM changes daily; monthly is plenty for routing):
  `./prepare.sh && docker compose restart osrm photon` (on a spot box per 5b to avoid downtime;
  rsync, then restart, ~1 min of downtime).
- **Updates:** `sudo apt-get upgrade` monthly; `docker compose pull && docker compose up -d`.
- **Monitoring:** a CloudWatch alarm on `StatusCheckFailed` (free tier) and an external uptime
  check on both URLs (e.g. UptimeRobot free).
- **Backups:** the data is rebuildable; snapshot only if you want a fast restore.
- **Abuse:** the endpoints are public by design (the app has no login). If someone scrapes them,
  add Caddy rate limiting (`caddy-ratelimit` plugin) or put CloudFront + AWS WAF rate rules in front.

## Weather (Open-Meteo)

- The **free API (`api.open-meteo.com`) is for non-commercial use only**. Car Hub sells Pro, so it
  is commercial. Release builds therefore have **no weather server** unless you set one.
- Options:
  1. **Open-Meteo customer API** (Standard plan, ~US$29/month [VERIFY on open-meteo.com/en/pricing]):
     `WEATHER_URL=https://customer-api.open-meteo.com/v1/forecast` and `WEATHER_API_KEY=<key>`.
     The key ships inside the APK, so restrict it in the Open-Meteo dashboard if possible and rotate
     via remote config if it leaks. Keep the CC BY 4.0 attribution.
  2. **Self-host Open-Meteo** (open source, AGPL-3.0 server; data from national weather services).
     Needs ~2–4 GB RAM plus regular model downloads (a few GB/day for an India-only area); doable on
     the same box but more upkeep.
  3. **Alternatives:** OpenWeatherMap One Call 3.0 (pay per call after 1,000 free/day, commercial OK),
     WeatherAPI.com (free tier allows commercial use with attribution, paid from ~US$7/month),
     Tomorrow.io (free tier is non-commercial), IMD (no public API for this use). Each needs a small
     client change because the response format differs.

## Licences and attribution

- OSM data: © OpenStreetMap contributors, ODbL. Attribution is already on the map pane.
- OSRM: BSD-2-Clause. Photon: Apache-2.0. Caddy: Apache-2.0.
- Serving routes/search results is a "Produced Work" under ODbL; no share-alike obligation for the
  app, attribution is enough.
