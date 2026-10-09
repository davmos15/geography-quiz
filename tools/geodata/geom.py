"""Geometry helpers for the GeoQuiz geodata pipeline (standard library only).

Everything here works on plain tuples of floats (lon, lat) in degrees, or on
int tuples once coordinates are quantised. Nothing depends on dict or set
iteration order of strings, so results do not change with PYTHONHASHSEED.

Main pieces:

* ``Topology``: TopoJSON-style shared arcs for a whole polygon layer. Rings are
  cut at junctions (points where the neighbouring vertices differ between the
  rings that use them), each distinct arc is simplified once, and rings are
  rebuilt from the simplified arcs. Neighbouring countries therefore keep an
  identical border after simplification: no gaps and no overlaps.
* ``douglas_peucker``: classic simplification with a minimum point rule.
* ``dissolve``: merges several features into one by cancelling the border
  segments they share (used for Somaliland into Somalia, Northern Cyprus into
  Cyprus).
* ``assign_holes``: groups shapefile rings into (outer, holes) polygons.
* distance and area helpers used by the remote-part and tap-zone rules.
"""

from __future__ import annotations

import math
from typing import Iterable, Sequence

Pt = tuple[float, float]
Ring = list[Pt]

EARTH_RADIUS_KM = 6371.0088


# --------------------------------------------------------------------------
# Basic ring helpers
# --------------------------------------------------------------------------

def open_ring(points: Sequence[Pt]) -> Ring:
    """Drop consecutive duplicates and the closing point of a shapefile ring."""
    out: Ring = []
    for p in points:
        p = (float(p[0]), float(p[1]))
        if not out or out[-1] != p:
            out.append(p)
    while len(out) > 1 and out[0] == out[-1]:
        out.pop()
    return out


def signed_area(ring: Sequence[Sequence[float]]) -> float:
    """Planar shoelace area; positive means counter-clockwise (lon east, lat north)."""
    n = len(ring)
    s = 0.0
    for i in range(n):
        x1, y1 = ring[i - 1]
        x2, y2 = ring[i]
        s += x1 * y2 - x2 * y1
    return s / 2.0


def bbox(points: Iterable[Sequence[float]]) -> tuple[float, float, float, float]:
    xs = []
    ys = []
    for x, y in points:
        xs.append(x)
        ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def point_in_ring(pt: Sequence[float], ring: Sequence[Sequence[float]]) -> bool:
    """Even-odd ray casting test."""
    x, y = pt
    inside = False
    n = len(ring)
    for i in range(n):
        x1, y1 = ring[i]
        x2, y2 = ring[i - 1]
        if (y1 > y) != (y2 > y):
            xi = (x2 - x1) * (y - y1) / (y2 - y1) + x1
            if x < xi:
                inside = not inside
    return inside


def vertex_mean(ring: Sequence[Sequence[float]]) -> Pt:
    n = len(ring)
    return (sum(p[0] for p in ring) / n, sum(p[1] for p in ring) / n)


def densify_long_edges(ring: list, is_polar, max_dx: int) -> list:
    """Split ring edges that run along a pole into steps of at most max_dx.

    Works on quantised rings. Natural Earth closes Antarctica with edges such
    as (180, -90) to (-180, -90); simplification can also merge pole-edge
    points into one long edge. Splitting them means no segment in any output
    spans 180 degrees of longitude or more, so a reader can treat a long
    segment as an error. is_polar(y) says whether a quantised y is at a pole.
    """
    out = []
    n = len(ring)
    for i in range(n):
        a = ring[i]
        b = ring[(i + 1) % n]
        out.append(a)
        dx = b[0] - a[0]
        if abs(dx) > max_dx and is_polar(a[1]) and is_polar(b[1]):
            steps = -(-abs(dx) // max_dx)
            for k in range(1, steps):
                out.append((a[0] + dx * k // steps, a[1] + (b[1] - a[1]) * k // steps))
    return out


# --------------------------------------------------------------------------
# Polygons from shapefile rings
# --------------------------------------------------------------------------

def assign_holes(rings: list[Ring]) -> tuple[list[tuple[Ring, list[Ring]]], int]:
    """Group rings into (outer, [holes]) using shapefile winding.

    Shapefile outer rings are clockwise, holes counter-clockwise. Each hole is
    given to the smallest outer ring that contains it. A hole that no outer
    contains is promoted to an outer ring (counted in the second return value).
    Output outers keep input order.
    """
    outers: list[int] = []
    holes: list[int] = []
    for i, r in enumerate(rings):
        if len(r) < 3:
            continue
        (outers if signed_area(r) < 0 else holes).append(i)
    boxes = {i: bbox(rings[i]) for i in outers}
    areas = {i: abs(signed_area(rings[i])) for i in outers}
    owner: dict[int, list[int]] = {i: [] for i in outers}
    orphans = 0
    for h in holes:
        hb = bbox(rings[h])
        best = None
        for o in outers:
            ob = boxes[o]
            if ob[0] <= hb[0] and ob[1] <= hb[1] and ob[2] >= hb[2] and ob[3] >= hb[3]:
                # Majority vote over a few hole vertices (holes can touch their outer).
                ring = rings[h]
                step = max(1, len(ring) // 5)
                probes = ring[::step][:5]
                votes = sum(1 for p in probes if point_in_ring(p, rings[o]))
                if votes * 2 >= len(probes) and (best is None or areas[o] < areas[best]):
                    best = o
        if best is None:
            orphans += 1
            outers.append(h)
            owner[h] = []
            continue
        owner[best].append(h)
    polys = []
    for o in outers:
        outer = rings[o]
        if signed_area(outer) > 0:   # promoted hole: make it clockwise like the others
            outer = outer[::-1]
        polys.append((outer, [rings[h] for h in owner[o]]))
    return polys, orphans


def dissolve(rings: list[Ring]) -> tuple[list[Ring], int]:
    """Merge rings of several features by cancelling segments used in both directions.

    All rings must use shapefile winding (outer clockwise, holes counter-
    clockwise). Returns the rebuilt rings (same winding convention) and the
    number of cancelled segment pairs.
    """
    edges: dict[tuple[Pt, Pt], int] = {}
    order: list[tuple[Pt, Pt]] = []
    for r in rings:
        n = len(r)
        for i in range(n):
            e = (r[i], r[(i + 1) % n])
            if e not in edges:
                order.append(e)
            edges[e] = edges.get(e, 0) + 1
    cancelled = 0
    for e in order:
        rev = (e[1], e[0])
        while edges.get(e, 0) > 0 and edges.get(rev, 0) > 0:
            edges[e] -= 1
            edges[rev] -= 1
            cancelled += 1
    outgoing: dict[Pt, list[Pt]] = {}
    for e in order:
        for _ in range(edges.get(e, 0)):
            outgoing.setdefault(e[0], []).append(e[1])
    for k in outgoing:
        outgoing[k].sort()
    result: list[Ring] = []
    starts = sorted(outgoing)
    for s in starts:
        while outgoing.get(s):
            ring = [s]
            cur = outgoing[s].pop(0)
            guard = 0
            while cur != s:
                ring.append(cur)
                nxt = outgoing.get(cur)
                if not nxt:
                    raise ValueError("dissolve: open chain")
                cur = nxt.pop(0)
                guard += 1
                if guard > 10_000_000:
                    raise ValueError("dissolve: runaway chain")
            if len(ring) >= 3:
                result.append(ring)
    return result, cancelled


# --------------------------------------------------------------------------
# Simplification
# --------------------------------------------------------------------------

def _seg_dist2(p: Pt, a: Pt, b: Pt) -> float:
    ax, ay = a
    dx = b[0] - ax
    dy = b[1] - ay
    px = p[0] - ax
    py = p[1] - ay
    l2 = dx * dx + dy * dy
    if l2 == 0.0:
        return px * px + py * py
    t = (px * dx + py * dy) / l2
    if t < 0.0:
        t = 0.0
    elif t > 1.0:
        t = 1.0
    ex = px - t * dx
    ey = py - t * dy
    return ex * ex + ey * ey


def douglas_peucker(points: Sequence[Pt], tol: float) -> list[Pt]:
    """Simplify an open polyline, always keeping both end points."""
    n = len(points)
    if n <= 2 or tol <= 0:
        return list(points)
    tol2 = tol * tol
    keep = [False] * n
    keep[0] = keep[-1] = True
    stack = [(0, n - 1)]
    while stack:
        i, j = stack.pop()
        if j <= i + 1:
            continue
        a = points[i]
        b = points[j]
        best = -1.0
        idx = -1
        for k in range(i + 1, j):
            d = _seg_dist2(points[k], a, b)
            if d > best:
                best = d
                idx = k
        if best > tol2:
            keep[idx] = True
            stack.append((i, idx))
            stack.append((idx, j))
    return [p for p, k in zip(points, keep) if k]


def _farthest(points: Sequence[Pt], origin: Pt) -> int:
    best = -1.0
    idx = 0
    for k, p in enumerate(points):
        d = (p[0] - origin[0]) ** 2 + (p[1] - origin[1]) ** 2
        if d > best:
            best = d
            idx = k
    return idx


def simplify_loop(points: Sequence[Pt], tol: float) -> list[Pt]:
    """Simplify a closed loop given open (first point not repeated).

    The loop is split at the point farthest from the first point and each
    half is simplified, so a loop keeps at least those two points; a third
    point (the one farthest from that chord) is added back if needed, so an
    island never collapses below a triangle here.
    """
    n = len(points)
    if n <= 3 or tol <= 0:
        return list(points)
    far = _farthest(points, points[0])
    if far == 0:
        return list(points)
    first = douglas_peucker(points[: far + 1], tol)
    second = douglas_peucker(list(points[far:]) + [points[0]], tol)
    out = first[:-1] + second[:-1]
    if len(out) < 3:
        a, b = points[0], points[far]
        best = -1.0
        idx = -1
        for k, p in enumerate(points):
            if k in (0, far):
                continue
            d = _seg_dist2(p, a, b)
            if d > best:
                best = d
                idx = k
        if idx >= 0:
            out = [points[i] for i in sorted({0, far, idx})]
    return out


class Topology:
    """Shared-arc topology for the polygon rings of one layer.

    rings: open rings (first point not repeated). After ``simplify`` and
    ``quantise``, ``ring_points(i)`` returns ring i rebuilt from its arcs.
    """

    def __init__(self, rings: list[Ring]):
        self.rings = rings
        nb: dict[Pt, tuple[Pt, Pt]] = {}
        junction: set[Pt] = set()
        for ring in rings:
            n = len(ring)
            for i in range(n):
                p = ring[i]
                a = ring[i - 1]
                b = ring[(i + 1) % n]
                pair = (a, b) if a <= b else (b, a)
                prev = nb.get(p)
                if prev is None:
                    nb[p] = pair
                elif prev != pair:
                    junction.add(p)
        self.junction_count = len(junction)
        arc_index: dict[tuple[Pt, ...], int] = {}
        self.arcs: list[list[Pt]] = []
        self.arc_closed: list[bool] = []
        self.arc_uses: list[int] = []
        # Per ring: list of (arc id, reversed)
        self.ring_arcs: list[list[tuple[int, bool]]] = []
        for ring in rings:
            n = len(ring)
            jidx = [i for i in range(n) if ring[i] in junction]
            refs: list[tuple[int, bool]] = []
            if not jidx:
                key, rev = _canonical_loop(ring)
                aid = arc_index.get(key)
                if aid is None:
                    aid = len(self.arcs)
                    arc_index[key] = aid
                    self.arcs.append(list(key))
                    self.arc_closed.append(True)
                    self.arc_uses.append(0)
                self.arc_uses[aid] += 1
                refs.append((aid, rev))
            else:
                k = jidx[0]
                rot = ring[k:] + ring[:k]
                rot_j = [i - k for i in jidx]
                rot_j.append(n)
                closed = rot + [rot[0]]
                for s, e in zip(rot_j, rot_j[1:]):
                    seg = tuple(closed[s: e + 1])
                    rseg = seg[::-1]
                    if rseg < seg:
                        key, rev = rseg, True
                    else:
                        key, rev = seg, False
                    aid = arc_index.get(key)
                    if aid is None:
                        aid = len(self.arcs)
                        arc_index[key] = aid
                        self.arcs.append(list(key))
                        self.arc_closed.append(False)
                        self.arc_uses.append(0)
                    self.arc_uses[aid] += 1
                    refs.append((aid, rev))
            self.ring_arcs.append(refs)
        self.simplified: list[list] = [list(a) for a in self.arcs]

    # -- simplification -----------------------------------------------------

    def simplify(self, tol: float) -> int:
        """Simplify every arc once. Returns the number of protected arcs.

        Rings that would end up with fewer than 3 distinct points get their
        arcs protected (kept at full resolution) so no ring collapses here.
        """
        out = []
        for arc, closed in zip(self.arcs, self.arc_closed):
            if closed:
                out.append(simplify_loop(arc, tol))
            elif arc[0] == arc[-1]:
                # A loop arc that starts and ends at one junction.
                body = arc[:-1]
                s = simplify_loop(body, tol)
                out.append(s + [s[0]])
            else:
                out.append(douglas_peucker(arc, tol))
        self.simplified = out
        protected = set()
        for refs in self.ring_arcs:
            if len(set(self._assemble(refs))) < 3:
                for aid, _ in refs:
                    protected.add(aid)
        for aid in sorted(protected):
            self.simplified[aid] = list(self.arcs[aid])
        return len(protected)

    def quantise(self, fn) -> None:
        """Map every simplified arc point through fn and drop repeats within arcs."""
        q = []
        for arc, closed in zip(self.simplified, self.arc_closed):
            pts = []
            for p in arc:
                qp = fn(p)
                if not pts or pts[-1] != qp:
                    pts.append(qp)
            if closed:
                while len(pts) > 1 and pts[0] == pts[-1]:
                    pts.pop()
            q.append(pts)
        self.simplified = q

    def _assemble(self, refs: list[tuple[int, bool]]) -> list:
        if len(refs) == 1 and self.arc_closed[refs[0][0]]:
            pts = list(self.simplified[refs[0][0]])
            return pts[::-1] if refs[0][1] else pts
        ring: list = []
        for aid, rev in refs:
            pts = self.simplified[aid]
            if rev:
                pts = pts[::-1]
            for p in pts:
                if not ring or ring[-1] != p:
                    ring.append(p)
        while len(ring) > 1 and ring[0] == ring[-1]:
            ring.pop()
        return ring

    def ring_points(self, i: int) -> list:
        return self._assemble(self.ring_arcs[i])

    def ring_is_shared(self, i: int) -> bool:
        return any(self.arc_uses[aid] > 1 for aid, _ in self.ring_arcs[i])

    def point_counts(self) -> tuple[int, int]:
        """(source vertices in unique arcs, simplified vertices in unique arcs)."""
        return sum(len(a) for a in self.arcs), sum(len(a) for a in self.simplified)


def _canonical_loop(ring: Ring) -> tuple[tuple[Pt, ...], bool]:
    """Rotation and direction independent key for a ring without junctions."""
    n = len(ring)
    k = min(range(n), key=lambda i: ring[i])
    fwd = ring[k:] + ring[:k]
    back = [fwd[0]] + fwd[1:][::-1]
    if n > 1 and back[1] < fwd[1]:
        return tuple(back), True
    return tuple(fwd), False


def clean_int_ring(ring: list) -> list:
    """Remove repeats and A-B-A spikes from a quantised ring."""
    pts = list(ring)
    changed = True
    while changed and len(pts) >= 3:
        changed = False
        out = []
        for p in pts:
            if out and out[-1] == p:
                changed = True
                continue
            out.append(p)
        while len(out) > 1 and out[0] == out[-1]:
            out.pop()
            changed = True
        n = len(out)
        if n >= 3:
            spikes = [i for i in range(n) if out[i - 1] == out[(i + 1) % n]]
            if spikes:
                drop = set()
                for i in spikes:
                    if i not in drop and (i - 1) % n not in drop and (i + 1) % n not in drop:
                        drop.add(i)
                        drop.add((i + 1) % n)
                out = [p for i, p in enumerate(out) if i not in drop]
                changed = True
        pts = out
    return pts


# --------------------------------------------------------------------------
# Distances, areas and interior points
# --------------------------------------------------------------------------

def to_xyz(lon: float, lat: float) -> tuple[float, float, float]:
    lo = math.radians(lon)
    la = math.radians(lat)
    c = math.cos(la)
    return (c * math.cos(lo), c * math.sin(lo), math.sin(la))


def km_to_chord(km: float) -> float:
    return 2.0 * math.sin(km / EARTH_RADIUS_KM / 2.0)


def chord_to_km(ch: float) -> float:
    return 2.0 * EARTH_RADIUS_KM * math.asin(min(1.0, ch / 2.0))


class SphereRing:
    """A ring's vertices on the unit sphere plus their bounding box, for distance tests."""

    MAX_POINTS = 2000

    def __init__(self, ring: Sequence[Pt]):
        step = max(1, len(ring) // self.MAX_POINTS)
        pts = [to_xyz(p[0], p[1]) for p in ring[::step]]
        self.pts = pts
        self.lo = tuple(min(p[i] for p in pts) for i in range(3))
        self.hi = tuple(max(p[i] for p in pts) for i in range(3))

    def box_gap(self, other: "SphereRing") -> float:
        s = 0.0
        for i in range(3):
            g = max(0.0, self.lo[i] - other.hi[i], other.lo[i] - self.hi[i])
            s += g * g
        return math.sqrt(s)

    def min_chord(self, other: "SphereRing", stop_below: float = 0.0) -> float:
        best = float("inf")
        for a in self.pts:
            ax, ay, az = a
            for b in other.pts:
                d = (ax - b[0]) ** 2 + (ay - b[1]) ** 2 + (az - b[2]) ** 2
                if d < best:
                    best = d
            if best <= stop_below * stop_below:
                break
        return math.sqrt(best)


def ring_area_km2(ring: Sequence[Pt]) -> float:
    """Area on the sphere via Lambert cylindrical equal-area coordinates."""
    n = len(ring)
    s = 0.0
    for i in range(n):
        x1 = math.radians(ring[i - 1][0])
        y1 = math.sin(math.radians(ring[i - 1][1]))
        x2 = math.radians(ring[i][0])
        y2 = math.sin(math.radians(ring[i][1]))
        s += x1 * y2 - x2 * y1
    return abs(s) / 2.0 * EARTH_RADIUS_KM * EARTH_RADIUS_KM


def interior_point(outer: Sequence, holes: Sequence[Sequence]) -> tuple[float, float]:
    """A point inside a polygon: the middle of the widest scanline interval.

    Scans a few horizontal lines through the polygon (at the vertex-mean
    latitude and at fractions of the bounding box height) and returns the
    midpoint of the widest inside interval. Works in whatever units the
    coordinates are in (degrees or quantised ints).
    """
    x0, y0, x1, y1 = bbox(outer)
    my = vertex_mean(outer)[1]
    rings = [outer] + list(holes)
    best = None
    for y in [my] + [y0 + (y1 - y0) * f for f in (0.5, 0.3, 0.7, 0.15, 0.85)]:
        xs = []
        for ring in rings:
            n = len(ring)
            for i in range(n):
                ax, ay = ring[i - 1]
                bx, by = ring[i]
                if (ay > y) != (by > y):
                    xs.append(ax + (bx - ax) * (y - ay) / (by - ay))
        xs.sort()
        for a, b in zip(xs[0::2], xs[1::2]):
            w = b - a
            if best is None or w > best[0]:
                best = (w, (a + b) / 2.0, y)
        if best is not None and best[0] > 0:
            # Prefer the first scanline that gives a reasonable interval.
            if best[0] >= (x1 - x0) * 0.25:
                break
    if best is None:
        return vertex_mean(outer)
    return (best[1], best[2])


# --------------------------------------------------------------------------
# Segment intersection count (integer coordinates)
# --------------------------------------------------------------------------

def _orient(a, b, c) -> int:
    v = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    return (v > 0) - (v < 0)


def count_crossings(rings: list[tuple[int, list]], cell: int = 512) -> tuple[int, int, list]:
    """Count proper crossings between segments of quantised rings.

    rings: list of (feature index, ring points). Returns (crossings between
    segments of the same feature, crossings between different features,
    sorted list of the (feature, feature) index pairs involved).
    Segments that touch at an end point or overlap along a shared border are
    not counted; only strict X-shaped crossings are.
    """
    grid: dict[tuple[int, int], list[int]] = {}
    segs: list[tuple] = []
    for fi, ring in rings:
        n = len(ring)
        for i in range(n):
            a = ring[i]
            b = ring[(i + 1) % n]
            if a == b:
                continue
            sid = len(segs)
            segs.append((a, b, fi))
            gx0 = min(a[0], b[0]) // cell
            gx1 = max(a[0], b[0]) // cell
            gy0 = min(a[1], b[1]) // cell
            gy1 = max(a[1], b[1]) // cell
            for gx in range(gx0, gx1 + 1):
                for gy in range(gy0, gy1 + 1):
                    grid.setdefault((gx, gy), []).append(sid)
    same = 0
    other = 0
    pairs: set[tuple[int, int]] = set()
    seen: set[tuple[int, int]] = set()
    for key in sorted(grid):
        ids = grid[key]
        for x in range(len(ids)):
            s1 = segs[ids[x]]
            a, b = s1[0], s1[1]
            for y in range(x + 1, len(ids)):
                s2 = segs[ids[y]]
                c, d = s2[0], s2[1]
                if a in (c, d) or b in (c, d):
                    continue
                o1 = _orient(a, b, c)
                o2 = _orient(a, b, d)
                o3 = _orient(c, d, a)
                o4 = _orient(c, d, b)
                if o1 * o2 < 0 and o3 * o4 < 0:
                    pair = (ids[x], ids[y]) if ids[x] < ids[y] else (ids[y], ids[x])
                    if pair in seen:
                        continue
                    seen.add(pair)
                    pairs.add((min(s1[2], s2[2]), max(s1[2], s2[2])))
                    if s1[2] == s2[2]:
                        same += 1
                    else:
                        other += 1
    return same, other, sorted(pairs)
