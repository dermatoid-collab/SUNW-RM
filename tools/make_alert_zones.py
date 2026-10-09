"""Builds app/src/main/assets/alert_zones.json from a DPC bulletin GeoJSON.

Source: https://github.com/pcm-dpc/DPC-Bollettini-Criticita-Idrogeologica-Idraulica
(files/geojson/<date>_<time>_today.json), licence CC BY 4.0. Only the zone outlines and
names are kept, simplified (Douglas-Peucker, ~300 m) and stored as integer 1e-4 degrees, so the app
can find a place's alert zone offline. Rerun when the Regions change their zones.

    python3 tools/make_alert_zones.py <today.json> app/src/main/assets/alert_zones.json
"""
import json
import sys

TOLERANCE = 0.003  # degrees, about 300 m


def simplify(points, tol):
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = points[a]
        bx, by = points[b]
        dx, dy = bx - ax, by - ay
        norm = (dx * dx + dy * dy) ** 0.5
        best, index = 0.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / norm if norm else ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
            if d > best:
                best, index = d, i
        if best > tol:
            keep[index] = True
            stack += [(a, index), (index, b)]
    return [p for p, k in zip(points, keep) if k]


def main(src, dst):
    features = json.load(open(src, encoding="utf-8"))["features"]
    zones = []
    for f in features:
        g = f["geometry"]
        polygons = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        rings = []
        for polygon in polygons:
            for ring in polygon:  # outer ring, then holes: even-odd test handles both
                s = simplify([tuple(p[:2]) for p in ring], TOLERANCE)
                if len(s) >= 4:
                    rings.append([v for lon, lat in s for v in (round(lon * 1e4), round(lat * 1e4))])
        zones.append({"name": f["properties"]["Nome zona"], "rings": rings})
    with open(dst, "w", encoding="utf-8") as out:
        json.dump({"source": "DPC, CC BY 4.0", "zones": zones}, out, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
