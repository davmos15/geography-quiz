#!/usr/bin/env python3
"""Build data/borders.json: the land-border adjacency graph for Border hop.

Two independent sources are cross-checked:

  * mledoze/countries' `borders` field (data/source/countries.json), restricted
    to the app's 197 countries (see tools/data/export_aliases.py
    derive_countries() for that filter -- imported, not duplicated).
  * Geometric adjacency derived from app/src/main/assets/geo/admin0_50m.bin
    (Natural Earth 5.1.2 ne_50m_admin_0_countries, already shipped in the
    app): two features are adjacent if a ring of one (outer or hole, any
    part) shares at least one identical edge -- an unordered pair of
    consecutive quantised points -- with a ring of the other. The pipeline
    that writes that file simplifies with shared-arc Douglas-Peucker so
    neighbouring countries keep bit-identical vertices along a shared
    border (data/geo/FORMAT.md, "Shared borders"), which is what makes this
    exact-segment test reliable. A pair that only shares a vertex, with no
    shared edge, is a much weaker signal ("touch at a point") and is
    reported separately -- it matters for a couple of real quadripoints.

Every disagreement between the two sources is resolved by an explicit,
reviewed entry in OVERRIDES below, each with the evidence that decided it.
The build fails loudly if a source refresh produces a disagreement with no
override (or an override that no longer corresponds to a real disagreement),
so a silent drift can never reach data/borders.json.

The final graph is: mledoze borders, restricted to the 197 app countries,
symmetrised, with OVERRIDES applied.

Outputs:
  data/borders.json         the adjacency graph, island list and BFS stats
  data/borders_report.md    the disagreement table, island list and BFS detail

Usage (from the repository root or anywhere):

    python tools/data/build_borders.py           # regenerate both files
    python tools/data/build_borders.py --check   # exit 1 if either is stale

Standard library only.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from collections import defaultdict, deque
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "tools" / "data"))
sys.path.insert(0, str(REPO_ROOT / "tools" / "geodata"))

import export_aliases as ea  # noqa: E402  derive_countries(): the 197-country filter
import geoformat as gf  # noqa: E402  decode(), Quantiser

COUNTRIES_JSON = REPO_ROOT / "data" / "source" / "countries.json"
ADMIN0_BIN = REPO_ROOT / "app" / "src" / "main" / "assets" / "geo" / "admin0_50m.bin"
OUTPUT_JSON = REPO_ROOT / "data" / "borders.json"
OUTPUT_REPORT = REPO_ROOT / "data" / "borders_report.md"

VERSION = 1

SOURCES = [
    {
        "name": "mledoze/countries",
        "url": "https://github.com/mledoze/countries",
        "file": "data/source/countries.json",
        "field": "borders",
        "licence": "ODbL-1.0",
        "version": "not recorded; see data/SOURCES.md (sha256 9c5b9005e0230a4bc218a9a2b3b9640fe25dc34ad774b2f13018284bba15528b)",
        "role": "primary: restricted to the app's 197 countries and symmetrised",
    },
    {
        "name": "Natural Earth",
        "url": "https://www.naturalearthdata.com/",
        "file": "app/src/main/assets/geo/admin0_50m.bin",
        "licence": "Public domain",
        "version": "5.1.2 ne_50m_admin_0_countries (already bundled in the app; see data/SOURCES.md)",
        "role": "cross-check: geometric adjacency from shared quantised border segments",
    },
]

# --------------------------------------------------------------------------
# Reviewed overrides to the mledoze graph (restricted to the 197 app
# countries, symmetrised). Keys are a sorted (cca3, cca3) tuple. The build
# fails if the live disagreement set (computed from the two sources above)
# doesn't match this table exactly -- see apply_overrides(). Full evidence
# is also written to data/borders_report.md.
# --------------------------------------------------------------------------
OVERRIDES: dict[tuple[str, str], dict[str, str]] = {
    # mledoze lists BWA<->ZMB. Natural Earth's 50m geometry agrees only to the
    # extent of a single shared vertex at (25.2607E, 17.7940S) -- the
    # Botswana/Namibia/Zambia/Zimbabwe quadripoint near Kazungula -- not a
    # shared edge; a line this short (effectively zero at 1:50,000,000) can't
    # survive quantisation as a distinct segment. This is the well-known
    # "world's shortest border" / Kazungula ferry crossing; both governments
    # and most reference sources treat it as a genuine, if vanishingly short,
    # land border, so it is kept. (The mirror case, NAM<->ZWE, touches at the
    # same quadripoint and is NOT listed by mledoze; both sources agree there
    # -- see "Touch at a point" in the report -- so it needs no override.)
    ("BWA", "ZMB"): {
        "action": "keep",
        "reason": "Kazungula quadripoint: NE shows a shared vertex only (no edge); "
                   "kept as a genuine short land border per mledoze",
    },
    # mledoze lists ESP<->MAR via the Ceuta and Melilla enclaves (land borders
    # of about 8 km and 10.5 km). Natural Earth's 50m-scale ESP polygon omits
    # Ceuta and Melilla entirely -- no vertex or edge anywhere near them --
    # because a border that short is below what a 1:50,000,000 basemap
    # resolves, not evidence the border doesn't exist.
    ("ESP", "MAR"): {
        "action": "keep",
        "reason": "tiny border lost at 50m: Ceuta/Melilla land borders with Morocco "
                   "are below Natural Earth's 50m resolution",
    },
    # mledoze is asymmetric here: LKA lists IND, but IND does not list LKA.
    # India and Sri Lanka are separated by the Palk Strait; there is no land
    # border (Adam's Bridge is a chain of shoals and sandbanks, not a land
    # crossing). Natural Earth shows no shared edge or even a shared vertex.
    # Treated as a mledoze data error and removed.
    ("IND", "LKA"): {
        "action": "remove",
        "reason": "maritime strait only (Palk Strait), no land border; mledoze entry "
                   "is asymmetric (only LKA lists IND) and NE geometry shows no contact",
    },
    # mledoze's FRA entry lists only metropolitan France's neighbours; it
    # omits overseas territories, even though BRA and SUR each separately
    # list French Guiana ("GUF", not an app country on its own) as a
    # neighbour. Natural Earth's FRA polygon already includes French Guiana
    # as a remote part (see data/geo/FORMAT.md "Remote parts") and shares a
    # substantial, ordinary land border with both Brazil and Suriname there
    # (hundreds of shared edges each, see the report). Added for consistency
    # with the "land borders only" rule and with how BRA/SUR already
    # describe this border.
    ("BRA", "FRA"): {
        "action": "add",
        "reason": "overseas territory: France-Brazil land border via French Guiana, "
                   "omitted from mledoze's FRA entry though BRA lists GUF",
    },
    ("FRA", "SUR"): {
        "action": "add",
        "reason": "overseas territory: France-Suriname land border via French Guiana, "
                   "omitted from mledoze's FRA entry though SUR lists GUF",
    },
}


# --------------------------------------------------------------------------
# mledoze graph
# --------------------------------------------------------------------------

def load_mledoze_graph() -> tuple[set[str], dict[str, set[str]], dict[str, list[str]], list[tuple[str, str]]]:
    """Return (app_set, symmetrised graph, dropped non-197 neighbours, asymmetric pairs)."""
    app_set = {c["cca3"] for c in ea.derive_countries()}
    raw = json.loads(COUNTRIES_JSON.read_text(encoding="utf-8"))
    raw_by_cca3 = {c["cca3"]: c for c in raw}

    raw_graph: dict[str, set[str]] = {}
    dropped: dict[str, list[str]] = {}
    for cca3 in app_set:
        entry = raw_by_cca3.get(cca3)
        if entry is None:
            raise SystemExit(f"app country {cca3} is missing from {COUNTRIES_JSON}")
        borders = entry.get("borders") or []
        in197 = sorted(b for b in borders if b in app_set)
        out197 = sorted(b for b in borders if b not in app_set)
        raw_graph[cca3] = set(in197)
        if out197:
            dropped[cca3] = out197

    asymmetric: list[tuple[str, str]] = []
    sym: dict[str, set[str]] = {c: set(v) for c, v in raw_graph.items()}
    for a, nbrs in raw_graph.items():
        for b in nbrs:
            if a not in raw_graph.get(b, set()):
                asymmetric.append((a, b))
            sym.setdefault(b, set()).add(a)

    return app_set, sym, dropped, sorted(asymmetric)


# --------------------------------------------------------------------------
# Natural Earth geometric graph
# --------------------------------------------------------------------------

def haversine_km(p1: tuple[int, int], p2: tuple[int, int], q: "gf.Quantiser") -> float:
    lon1, lat1 = q.deg(p1)
    lon2, lat2 = q.deg(p2)
    r = 6371.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlmb / 2) ** 2
    return r * 2 * math.asin(min(1.0, math.sqrt(a)))


def _pair_counts(owners_map: dict) -> tuple[dict[tuple[str, str], int], dict[tuple[str, str], list]]:
    counts: dict[tuple[str, str], int] = defaultdict(int)
    members: dict[tuple[str, str], list] = defaultdict(list)
    for key, owners in owners_map.items():
        if len(owners) < 2:
            continue
        ordered = sorted(owners)
        for i in range(len(ordered)):
            for j in range(i + 1, len(ordered)):
                pair = (ordered[i], ordered[j])
                counts[pair] += 1
                members[pair].append(key)
    return counts, members


def load_ne_adjacency():
    """Decode admin0_50m.bin and derive geometric adjacency from shared edges.

    Returns (playable, edge_counts, edge_lengths_km, vertex_counts). Feature
    ids are the app's cca3 for the 197 playable countries and Natural Earth's
    ADM0_A3 for other, non-playable land (Antarctica, Western Sahara,
    dependencies and so on; see data/geo/FORMAT.md "admin0_110m, admin0_50m").
    Those non-playable features are kept in the adjacency computation (so a
    disagreement caused by a third territory would show up as a "touch" or
    missing edge) but excluded from the playable-playable comparison.
    """
    layer = gf.decode(ADMIN0_BIN.read_bytes())
    q = gf.Quantiser(layer.bounds)
    playable = {f.id: bool(f.props[2]) for f in layer.features}

    edge_owners: dict[tuple, set[str]] = defaultdict(set)
    vertex_owners: dict[tuple, set[str]] = defaultdict(set)
    for f in layer.features:
        seen_edges = set()
        for part in f.parts:
            pts = part.points
            n = len(pts)
            for p in pts:
                vertex_owners[p].add(f.id)
            for i in range(n):
                p1, p2 = pts[i], pts[(i + 1) % n]  # rings are not closed in storage; close here
                edge = (p1, p2) if p1 <= p2 else (p2, p1)
                seen_edges.add(edge)
        for edge in seen_edges:
            edge_owners[edge].add(f.id)

    edge_counts, edge_members = _pair_counts(edge_owners)
    vertex_counts, _ = _pair_counts(vertex_owners)
    edge_lengths = {
        pair: sum(haversine_km(a, b, q) for a, b in edges)
        for pair, edges in edge_members.items()
    }
    return playable, edge_counts, edge_lengths, vertex_counts


# --------------------------------------------------------------------------
# Compare, resolve, finalise
# --------------------------------------------------------------------------

def compute_disagreements(mledoze_sym, playable, edge_counts, vertex_counts):
    mledoze_pairs = {tuple(sorted((a, b))) for a, nbrs in mledoze_sym.items() for b in nbrs}
    ne_pairs = {p for p in edge_counts if playable.get(p[0]) and playable.get(p[1])}
    missing_in_ne = sorted(mledoze_pairs - ne_pairs)
    missing_in_mledoze = sorted(ne_pairs - mledoze_pairs)
    touch_only = sorted(
        p for p in vertex_counts
        if playable.get(p[0]) and playable.get(p[1]) and edge_counts.get(p, 0) == 0
    )
    return mledoze_pairs, ne_pairs, missing_in_ne, missing_in_mledoze, touch_only


def apply_overrides(mledoze_sym, missing_in_ne, missing_in_mledoze):
    disagreements = set(missing_in_ne) | set(missing_in_mledoze)
    covered = set(OVERRIDES)
    unexplained = disagreements - covered
    stale = covered - disagreements
    if unexplained:
        raise SystemExit(
            "New mledoze/Natural Earth border disagreement(s) with no reviewed entry "
            f"in OVERRIDES: {sorted(unexplained)}. Investigate and add one (see the "
            "module docstring and the comments above OVERRIDES)."
        )
    if stale:
        raise SystemExit(
            f"OVERRIDES entries no longer match a real disagreement: {sorted(stale)}. "
            "Remove them (the underlying data must have changed)."
        )
    final: dict[str, set[str]] = {c: set(v) for c, v in mledoze_sym.items()}
    for (a, b), decision in sorted(OVERRIDES.items()):
        action = decision["action"]
        if action == "add":
            final.setdefault(a, set()).add(b)
            final.setdefault(b, set()).add(a)
        elif action == "remove":
            final.setdefault(a, set()).discard(b)
            final.setdefault(b, set()).discard(a)
        elif action != "keep":
            raise SystemExit(f"unknown override action {action!r} for {(a, b)}")
    return final


# --------------------------------------------------------------------------
# Graph stats
# --------------------------------------------------------------------------

def bfs(start: str, graph: dict[str, set[str]]) -> dict[str, int]:
    """Breadth-first search with neighbours visited in sorted order, so
    traversal order (and anything derived from it, such as example pairs) is
    deterministic regardless of Python's per-process string hash seed."""
    dist = {start: 0}
    q = deque([start])
    while q:
        cur = q.popleft()
        for nb in sorted(graph.get(cur, ())):
            if nb not in dist:
                dist[nb] = dist[cur] + 1
                q.append(nb)
    return dist


def connected_components(nodes: list[str], graph: dict[str, set[str]]) -> list[list[str]]:
    seen: set[str] = set()
    components: list[list[str]] = []
    for n in nodes:
        if n in seen:
            continue
        comp = []
        q = deque([n])
        seen.add(n)
        while q:
            cur = q.popleft()
            comp.append(cur)
            for nb in sorted(graph.get(cur, ())):
                if nb not in seen:
                    seen.add(nb)
                    q.append(nb)
        components.append(sorted(comp))
    components.sort(key=lambda c: (-len(c), c))
    return components


def hop_stats(nodes: list[str], graph: dict[str, set[str]]):
    pairs_by_hops: dict[int, int] = defaultdict(int)
    examples: dict[int, list[tuple[str, str]]] = defaultdict(list)
    for a in nodes:
        dist = bfs(a, graph)
        for b, d in dist.items():
            if d == 0 or b <= a:
                continue
            pairs_by_hops[d] += 1
            if len(examples[d]) < 3:
                examples[d].append((a, b))
    return pairs_by_hops, examples


# --------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------

def build():
    app_set, mledoze_sym, dropped, asymmetric = load_mledoze_graph()
    playable, edge_counts, edge_lengths, vertex_counts = load_ne_adjacency()

    missing_app = sorted(c for c in app_set if c not in playable or not playable[c])
    if missing_app:
        raise SystemExit(f"app countries missing or non-playable in {ADMIN0_BIN}: {missing_app}")

    mledoze_pairs, ne_pairs, missing_in_ne, missing_in_mledoze, touch_only = compute_disagreements(
        mledoze_sym, playable, edge_counts, vertex_counts
    )
    final = apply_overrides(mledoze_sym, missing_in_ne, missing_in_mledoze)

    nodes = sorted(app_set)
    countries = {c: sorted(final.get(c, set())) for c in nodes}
    islands = sorted(c for c, nbrs in countries.items() if not nbrs)

    components = connected_components(nodes, final)
    pairs_by_hops, examples = hop_stats(nodes, final)
    diameter = max(pairs_by_hops) if pairs_by_hops else 0
    eligible_2_6 = sum(c for h, c in pairs_by_hops.items() if 2 <= h <= 6)

    data = {
        "version": VERSION,
        "licence": "ODbL-1.0",
        "licenceUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
        "attribution": (
            "Contains information from mledoze/countries "
            "(https://github.com/mledoze/countries), made available under the "
            "Open Database License (ODbL) v1.0."
        ),
        "sources": SOURCES,
        "countries": countries,
        "islands": islands,
        "stats": {
            "components": len(components),
            "largest_component_size": len(components[0]) if components else 0,
            "diameter": diameter,
            "pairs_by_hops": {str(h): c for h, c in sorted(pairs_by_hops.items())},
            "eligible_pairs_2_to_6": eligible_2_6,
        },
    }

    context = {
        "app_set": app_set,
        "dropped": dropped,
        "asymmetric": asymmetric,
        "mledoze_pairs": mledoze_pairs,
        "ne_pairs": ne_pairs,
        "missing_in_ne": missing_in_ne,
        "missing_in_mledoze": missing_in_mledoze,
        "touch_only": touch_only,
        "edge_counts": edge_counts,
        "edge_lengths": edge_lengths,
        "vertex_counts": vertex_counts,
        "components": components,
        "pairs_by_hops": pairs_by_hops,
        "examples": examples,
        "countries": countries,
        "islands": islands,
    }
    return data, context


def render_json(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def render_report(data: dict, ctx: dict) -> str:
    lines: list[str] = []
    add = lines.append

    add("# Border hop adjacency build report")
    add("")
    add("Generated by `tools/data/build_borders.py`. Do not edit by hand; rerun the")
    add("script after a `data/source/countries.json` or map asset refresh.")
    add("")
    add("## Sources")
    add("")
    for src in data["sources"]:
        add(f"- **{src['name']}** ({src['role']}) -- {src['file']}, licence {src['licence']}, "
            f"version {src['version']}")
    add("")

    add("## Disagreements and resolutions")
    add("")
    add(f"{len(ctx['mledoze_pairs'])} mledoze pairs, {len(ctx['ne_pairs'])} Natural Earth "
        f"playable-playable pairs, "
        f"{len(ctx['missing_in_ne']) + len(ctx['missing_in_mledoze'])} disagreement(s), "
        "each resolved by a reviewed entry in `OVERRIDES`:")
    add("")
    add("| Pair | mledoze says | Natural Earth says | Resolution | Reason |")
    add("|---|---|---|---|---|")
    all_disagreements = sorted(set(ctx["missing_in_ne"]) | set(ctx["missing_in_mledoze"]))
    for pair in all_disagreements:
        a, b = pair
        decision = OVERRIDES[pair]
        if pair in ctx["missing_in_ne"]:
            mledoze_says = "yes"
            vcount = ctx["vertex_counts"].get(pair, 0)
            ne_says = f"no shared edge (shared vertex x{vcount})" if vcount else "no shared edge or vertex"
        else:
            mledoze_says = "no"
            ecount = ctx["edge_counts"].get(pair, 0)
            elen = ctx["edge_lengths"].get(pair, 0.0)
            ne_says = f"{ecount} shared edges, approx. {elen:.0f} km"
        add(f"| {a}-{b} | {mledoze_says} | {ne_says} | {decision['action']} | {decision['reason']} |")
    add("")

    add("### Touch at a point (shared vertex only, weaker signal)")
    add("")
    add("Pairs whose Natural Earth geometry shares a vertex but no edge. Reported for")
    add("transparency; only `BWA-ZMB` above is treated as a border (per its override).")
    add("")
    add("| Pair | mledoze says | Note |")
    add("|---|---|---|")
    for pair in ctx["touch_only"]:
        a, b = pair
        in_mledoze = pair in ctx["mledoze_pairs"]
        note = "resolved above (kept)" if pair in OVERRIDES else "agrees with mledoze: not a border"
        add(f"| {a}-{b} | {'yes' if in_mledoze else 'no'} | {note} |")
    add("")

    add("### Neighbours dropped mapping mledoze borders to the app's 197 countries")
    add("")
    add("Codes in a country's mledoze `borders` field that are not one of the app's 197")
    add("countries (so cannot become an edge in this graph). None of these drop a")
    add("border between two app countries; each is a dependency, disputed territory kept")
    add("separate per decision D32, or (Hong Kong/Macao) part of China in the app.")
    add("")
    add("| Country | Dropped codes |")
    add("|---|---|")
    for c, codes in sorted(ctx["dropped"].items()):
        add(f"| {c} | {', '.join(codes)} |")
    add("")

    add("### Asymmetric mledoze entries")
    add("")
    if ctx["asymmetric"]:
        add("mledoze entries where A lists B but B does not list A (symmetrised by union")
        add("before resolution; see the override above if the asymmetry indicated an error):")
        add("")
        for a, b in ctx["asymmetric"]:
            add(f"- {a} lists {b}, but {b} does not list {a}")
    else:
        add("None found.")
    add("")

    add("## Final graph")
    add("")
    total_edges = sum(len(v) for v in data["countries"].values()) // 2
    add(f"- {len(data['countries'])} countries, {total_edges} land-border edges, "
        f"{len(data['islands'])} islands (no land border in the final graph)")
    add(f"- {data['stats']['components']} connected components, largest "
        f"{data['stats']['largest_component_size']} countries, diameter "
        f"{data['stats']['diameter']} hops")
    add("")

    add("## Islands (no land border in the final graph)")
    add("")
    add(", ".join(data["islands"]))
    add("")

    add("## Components")
    add("")
    add("| Size | Countries |")
    add("|---|---|")
    for comp in ctx["components"]:
        add(f"| {len(comp)} | {', '.join(comp)} |")
    add("")

    add("## BFS hop statistics")
    add("")
    add("Unordered country pairs by shortest path length (hops) in the final graph.")
    add("")
    add("| Hops | Pairs | Example pairs |")
    add("|---|---|---|")
    for h in sorted(ctx["pairs_by_hops"]):
        examples = ", ".join(f"{a}-{b}" for a, b in ctx["examples"][h])
        add(f"| {h} | {ctx['pairs_by_hops'][h]} | {examples} |")
    add("")
    add(f"Eligible pairs for a 2-6 hop question (`eligible_pairs_2_to_6`): "
        f"**{data['stats']['eligible_pairs_2_to_6']}**")
    add("")

    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--check",
        action="store_true",
        help="Do not write; exit 1 if data/borders.json or data/borders_report.md is stale.",
    )
    args = parser.parse_args(argv)

    data, ctx = build()
    json_text = render_json(data)
    report_text = render_report(data, ctx)

    if args.check:
        stale = []
        for path, text in ((OUTPUT_JSON, json_text), (OUTPUT_REPORT, report_text)):
            current = path.read_bytes().decode("utf-8").replace("\r\n", "\n") if path.exists() else None
            if current != text:
                stale.append(path.relative_to(REPO_ROOT).as_posix())
        if stale:
            print(
                "Out of date: " + ", ".join(stale) + "\nRun: python tools/data/build_borders.py",
                file=sys.stderr,
            )
            return 1
        print("data/borders.json and data/borders_report.md are up to date.")
        return 0

    OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_JSON.write_bytes(json_text.encode("utf-8"))
    OUTPUT_REPORT.write_bytes(report_text.encode("utf-8"))
    print(
        f"Wrote {OUTPUT_JSON.relative_to(REPO_ROOT).as_posix()} "
        f"({len(data['countries'])} countries, "
        f"{sum(len(v) for v in data['countries'].values()) // 2} edges, "
        f"{len(data['islands'])} islands) and "
        f"{OUTPUT_REPORT.relative_to(REPO_ROOT).as_posix()}."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
