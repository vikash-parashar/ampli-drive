#!/usr/bin/env bash
# One-off (and monthly refresh) data preparation. Run on the instance, or on a bigger temporary
# instance and copy ./data across (see README, "Build the graph on a bigger box").
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] && set -a && . ./.env && set +a

OSRM_VERSION=${OSRM_VERSION:-v5.27.1}
PBF_URL=${PBF_URL:-https://download.geofabrik.de/asia/india-latest.osm.pbf}
PHOTON_DUMP_URL=${PHOTON_DUMP_URL:-https://download1.graphhopper.com/public/extracts/by-country-code/in/photon-db-in-latest.tar.bz2}

mkdir -p data/osrm data/photon

echo "== 1/3 India extract from Geofabrik (~1.5 GB)"
curl -fL --retry 3 -o data/osrm/india-latest.osm.pbf.tmp "$PBF_URL"
curl -fsSL "$PBF_URL.md5" | awk '{print $1"  data/osrm/india-latest.osm.pbf.tmp"}' | md5sum -c -
mv data/osrm/india-latest.osm.pbf.tmp data/osrm/india-latest.osm.pbf

echo "== 2/3 OSRM car profile: extract, partition, customize (needs ~24 GB RAM incl. swap)"
img="ghcr.io/project-osrm/osrm-backend:$OSRM_VERSION"
docker run --rm -t -v "$PWD/data/osrm:/data" "$img" osrm-extract -p /opt/car.lua /data/india-latest.osm.pbf
docker run --rm -t -v "$PWD/data/osrm:/data" "$img" osrm-partition /data/india-latest.osrm
docker run --rm -t -v "$PWD/data/osrm:/data" "$img" osrm-customize /data/india-latest.osrm
rm -f data/osrm/india-latest.osm.pbf

echo "== 3/3 Photon India index (prebuilt dump)"
curl -fL --retry 3 "$PHOTON_DUMP_URL" | tar -xj -C data/photon
ls data/photon

echo "Done. Start with: docker compose up -d --build"
