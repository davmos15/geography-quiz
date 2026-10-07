"""
Generate Google Play Games achievements ZIP for bulk import.

Creates:
  - AchievementsMetadata.csv
  - AchievementsLocalizations.csv
  - AchievementsIconsMappings.csv  (note: singular "Icon" per Google docs)
  - 512x512 PNG icons for each achievement
  - achievements_import.zip containing everything
"""

import csv
import io
import math
import os
import re
import sys
import zipfile
from pathlib import Path

if "--check" not in sys.argv:
    # Drawing needs Pillow; `--check` only validates the data and runs without it.
    from PIL import Image, ImageDraw, ImageFont

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "store_assets", "achievements")

# Bundled OFL font (see tools/fonts/README.md). No fallback to system fonts.
FONT_BOLD = Path(__file__).resolve().parent / "tools" / "fonts" / "Lato-Bold.ttf"


def load_bold_font(size):
    """Load the bundled bold font, failing clearly if it is missing."""
    if not FONT_BOLD.is_file():
        raise SystemExit(
            f"Font not found: {FONT_BOLD}. "
            "Run `python tools/fonts/fetch_fonts.py` to restore the bundled fonts."
        )
    return ImageFont.truetype(str(FONT_BOLD), int(size))

# ── Achievement data ───────────────────────────────────────────────────────
# The `Achievement` enum in Achievement.kt is the single source of truth for ids,
# titles, descriptions and tiers. This script only adds an icon theme per id and
# fails if the two drift apart. `--check` validates without drawing anything
# (no Pillow needed), and also checks the README count; CI runs it.

REPO_ROOT = Path(__file__).resolve().parent
ACHIEVEMENT_KT = (
    REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "geoquiz" / "app"
    / "domain" / "model" / "Achievement.kt"
)
README = REPO_ROOT / "README.md"

# Icon theme per achievement id (drawing only).
THEMES = {
    "first_steps": "star",
    "world_traveler": "globe",
    "perfectionist": "trophy",
    "speed_demon": "clock",
    "region_master": "map",
    "alphabet_soup": "letter",
    "century_club": "hundred",
    "half_way_there": "globe",
    "geography_buff": "book",
    "explorer": "compass",
    "quick_study": "clock",
    "island_hopper": "island",
    "pattern_finder": "pattern",
    "world_scholar": "globe",
    "length_master": "ruler",
    "vowel_hunter": "letter",
    "continental": "map",
    "letter_collector": "letter",
    "ultimate_geographer": "globe",
    "speed_master": "clock",
    "pattern_master": "pattern",
    "subregion_explorer": "map",
    "capital_beginner": "capital",
    "capital_expert": "capital",
    "world_capitals": "capital",
    "capital_speed_run": "clock",
    "capital_scholar": "book",
    "capital_master": "trophy",
    "flag_spotter": "flag",
    "color_expert": "palette",
    "rainbow": "rainbow",
    "flag_perfectionist": "flag",
    "vexillologist": "flag",
    "flag_master": "trophy",
    "flawless": "diamond",
    "sharp_mind": "diamond",
    "survivor": "shield",
    "nerves_of_steel": "shield",
}

ENUM_ENTRY_RE = re.compile(
    r'^\s*[A-Z_]+\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*AchievementTier\.(BRONZE|SILVER|GOLD)\s*\)',
    re.MULTILINE,
)
README_COUNT_RE = re.compile(r"(\d+) achievements")


def load_achievements():
    """Parse Achievement.kt into (id, title, description, tier, theme) tuples, in enum order."""
    entries = ENUM_ENTRY_RE.findall(ACHIEVEMENT_KT.read_text(encoding="utf-8"))
    if not entries:
        raise SystemExit(f"No achievements parsed from {ACHIEVEMENT_KT}")
    ids = [e[0] for e in entries]
    if len(set(ids)) != len(ids):
        raise SystemExit("Duplicate achievement ids in Achievement.kt")
    missing = [i for i in ids if i not in THEMES]
    stale = sorted(set(THEMES) - set(ids))
    if missing or stale:
        raise SystemExit(f"THEMES out of sync with Achievement.kt. Missing: {missing}. Not in enum: {stale}.")
    return [(aid, title, desc, tier, THEMES[aid]) for aid, title, desc, tier in entries]


def check_readme(count):
    counts = {int(n) for n in README_COUNT_RE.findall(README.read_text(encoding="utf-8"))}
    if counts != {count}:
        raise SystemExit(f"README.md achievement count {sorted(counts) or 'missing'} != {count} in Achievement.kt")


ACHIEVEMENTS = load_achievements()

TIER_POINTS = {"BRONZE": 5, "SILVER": 15, "GOLD": 30}

TIER_COLORS = {
    "BRONZE": {"ring": "#CD7F32", "ring_dark": "#8B5A2B", "bg": "#3E2723", "glow": "#D4956A"},
    "SILVER": {"ring": "#C0C0C0", "ring_dark": "#808080", "bg": "#263238", "glow": "#B0BEC5"},
    "GOLD":   {"ring": "#FFD700", "ring_dark": "#B8860B", "bg": "#1A237E", "glow": "#FFE082"},
}



def hex_to_rgb(hex_color):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def draw_circle_gradient(draw, cx, cy, radius, color_inner, color_outer):
    """Draw a radial gradient circle."""
    r1, g1, b1 = hex_to_rgb(color_inner)
    r2, g2, b2 = hex_to_rgb(color_outer)
    for r in range(int(radius), 0, -1):
        t = r / radius
        cr = int(r1 * (1 - t) + r2 * t)
        cg = int(g1 * (1 - t) + g2 * t)
        cb = int(b1 * (1 - t) + b2 * t)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(cr, cg, cb))


def draw_globe_symbol(draw, cx, cy, size, color):
    """Draw a simple globe wireframe."""
    r = size // 2
    lw = max(2, size // 25)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=lw)
    draw.line([(cx, cy - r), (cx, cy + r)], fill=color, width=lw)
    draw.line([(cx - r, cy), (cx + r, cy)], fill=color, width=lw)
    # Meridian arcs
    draw.arc([cx - r * 0.4, cy - r, cx + r * 0.4, cy + r], 0, 360, fill=color, width=max(1, lw - 1))
    draw.arc([cx - r * 0.7, cy - r, cx + r * 0.7, cy + r], 0, 360, fill=color, width=max(1, lw - 1))
    # Latitude arcs
    draw.arc([cx - r, cy - r * 0.35 - r * 0.15, cx + r, cy - r * 0.35 + r * 0.15], 0, 360, fill=color, width=max(1, lw - 1))
    draw.arc([cx - r, cy + r * 0.35 - r * 0.15, cx + r, cy + r * 0.35 + r * 0.15], 0, 360, fill=color, width=max(1, lw - 1))


def draw_flag_symbol(draw, cx, cy, size, color):
    """Draw a flag on a pole."""
    lw = max(2, size // 20)
    pole_x = cx - size * 0.25
    # Pole
    draw.line([(pole_x, cy - size * 0.45), (pole_x, cy + size * 0.45)], fill=color, width=lw)
    # Flag rectangle
    flag_top = cy - size * 0.45
    flag_bottom = cy
    flag_right = cx + size * 0.35
    draw.rectangle([pole_x, flag_top, flag_right, flag_bottom], outline=color, width=lw)
    # Stripe
    mid = (flag_top + flag_bottom) / 2
    draw.line([(pole_x, mid), (flag_right, mid)], fill=color, width=max(1, lw - 1))


def draw_shield_symbol(draw, cx, cy, size, color):
    """Draw a shield shape."""
    lw = max(2, size // 20)
    s = size * 0.45
    points = [
        (cx - s, cy - s * 0.8),
        (cx + s, cy - s * 0.8),
        (cx + s, cy + s * 0.1),
        (cx, cy + s),
        (cx - s, cy + s * 0.1),
    ]
    draw.polygon(points, outline=color, fill=None)
    # Inner cross
    draw.line([(cx, cy - s * 0.5), (cx, cy + s * 0.4)], fill=color, width=lw)
    draw.line([(cx - s * 0.4, cy - s * 0.15), (cx + s * 0.4, cy - s * 0.15)], fill=color, width=lw)


def draw_trophy_symbol(draw, cx, cy, size, color):
    """Draw a trophy cup."""
    lw = max(2, size // 20)
    w = size * 0.35
    h = size * 0.3
    # Cup
    draw.arc([cx - w, cy - h - size * 0.1, cx + w, cy + h - size * 0.1], 0, 180, fill=color, width=lw + 1)
    draw.line([(cx - w, cy - size * 0.1), (cx + w, cy - size * 0.1)], fill=color, width=lw)
    # Handles
    draw.arc([cx - w - size * 0.15, cy - size * 0.15, cx - w + size * 0.05, cy + size * 0.1], 90, 270, fill=color, width=lw)
    draw.arc([cx + w - size * 0.05, cy - size * 0.15, cx + w + size * 0.15, cy + size * 0.1], 270, 90, fill=color, width=lw)
    # Stem
    draw.line([(cx, cy + h - size * 0.1), (cx, cy + h + size * 0.05)], fill=color, width=lw)
    # Base
    draw.line([(cx - size * 0.2, cy + h + size * 0.05), (cx + size * 0.2, cy + h + size * 0.05)], fill=color, width=lw + 1)


def draw_diamond_symbol(draw, cx, cy, size, color):
    """Draw a diamond shape."""
    lw = max(2, size // 20)
    s = size * 0.4
    points = [(cx, cy - s), (cx + s * 0.7, cy), (cx, cy + s), (cx - s * 0.7, cy)]
    draw.polygon(points, outline=color, fill=None, width=lw)
    # Facet lines
    flw = max(1, size // 30)
    draw.line([(cx - s * 0.7, cy), (cx + s * 0.7, cy)], fill=color, width=flw)
    draw.line([(cx, cy - s), (cx - s * 0.3, cy)], fill=color, width=flw)
    draw.line([(cx, cy - s), (cx + s * 0.3, cy)], fill=color, width=flw)


def draw_star_symbol(draw, cx, cy, size, color):
    """Draw a 5-pointed star."""
    lw = max(2, size // 20)
    r_outer = size * 0.45
    r_inner = size * 0.18
    points = []
    for i in range(10):
        angle = math.radians(i * 36 - 90)
        r = r_outer if i % 2 == 0 else r_inner
        points.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    draw.polygon(points, outline=color, fill=color)


def draw_clock_symbol(draw, cx, cy, size, color):
    """Draw a clock face."""
    lw = max(2, size // 20)
    r = size * 0.42
    # Circle
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=lw)
    # Hour ticks
    for i in range(12):
        angle = math.radians(i * 30 - 90)
        x1 = cx + r * 0.82 * math.cos(angle)
        y1 = cy + r * 0.82 * math.sin(angle)
        x2 = cx + r * 0.95 * math.cos(angle)
        y2 = cy + r * 0.95 * math.sin(angle)
        draw.line([(x1, y1), (x2, y2)], fill=color, width=max(1, lw - 1))
    # Hands - hour pointing at 10
    h_angle = math.radians(300 - 90)
    draw.line([(cx, cy), (cx + r * 0.45 * math.cos(h_angle), cy + r * 0.45 * math.sin(h_angle))],
              fill=color, width=lw + 1)
    # Minute hand pointing at 2
    m_angle = math.radians(60 - 90)
    draw.line([(cx, cy), (cx + r * 0.65 * math.cos(m_angle), cy + r * 0.65 * math.sin(m_angle))],
              fill=color, width=lw)
    # Center dot
    draw.ellipse([cx - lw, cy - lw, cx + lw, cy + lw], fill=color)


def draw_compass_symbol(draw, cx, cy, size, color):
    """Draw a compass rose."""
    lw = max(2, size // 20)
    r = size * 0.42
    # Outer circle
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=lw)
    # Cardinal points (N, S, E, W arrows)
    for angle_deg in [0, 90, 180, 270]:
        angle = math.radians(angle_deg - 90)
        # Arrow point
        px = cx + r * 0.85 * math.cos(angle)
        py = cy + r * 0.85 * math.sin(angle)
        # Arrow base left/right
        perp = angle + math.pi / 2
        bx = cx + r * 0.15 * math.cos(angle)
        by = cy + r * 0.15 * math.sin(angle)
        lx = bx + r * 0.15 * math.cos(perp)
        ly = by + r * 0.15 * math.sin(perp)
        rx = bx - r * 0.15 * math.cos(perp)
        ry = by - r * 0.15 * math.sin(perp)
        if angle_deg in [0, 270]:  # N and W filled
            draw.polygon([(px, py), (lx, ly), (rx, ry)], fill=color)
        else:
            draw.polygon([(px, py), (lx, ly), (rx, ry)], outline=color, width=max(1, lw - 1))


def draw_book_symbol(draw, cx, cy, size, color):
    """Draw an open book."""
    lw = max(2, size // 20)
    w = size * 0.4
    h = size * 0.35
    # Spine
    draw.line([(cx, cy - h), (cx, cy + h)], fill=color, width=lw)
    # Left page
    draw.arc([cx - w * 1.5, cy - h, cx + w * 0.1, cy + h], 160, 360, fill=color, width=lw)
    # Right page
    draw.arc([cx - w * 0.1, cy - h, cx + w * 1.5, cy + h], 180, 20, fill=color, width=lw)
    # Page lines (left)
    for i in range(3):
        y = cy - h * 0.3 + i * h * 0.35
        draw.line([(cx - w * 0.8, y), (cx - w * 0.15, y)], fill=color, width=max(1, lw - 2))
    # Page lines (right)
    for i in range(3):
        y = cy - h * 0.3 + i * h * 0.35
        draw.line([(cx + w * 0.15, y), (cx + w * 0.8, y)], fill=color, width=max(1, lw - 2))


def draw_map_symbol(draw, cx, cy, size, color):
    """Draw a folded map."""
    lw = max(2, size // 20)
    w = size * 0.4
    h = size * 0.35
    # Three panel folded map
    x1 = cx - w
    x2 = cx - w * 0.33
    x3 = cx + w * 0.33
    x4 = cx + w
    # Left panel (slightly angled)
    draw.polygon([(x1, cy - h * 0.8), (x2, cy - h), (x2, cy + h), (x1, cy + h * 0.8)], outline=color, width=lw)
    # Center panel
    draw.polygon([(x2, cy - h), (x3, cy - h * 0.8), (x3, cy + h * 0.8), (x2, cy + h)], outline=color, width=lw)
    # Right panel
    draw.polygon([(x3, cy - h * 0.8), (x4, cy - h), (x4, cy + h), (x3, cy + h * 0.8)], outline=color, width=lw)
    # Map pin in center
    pin_cy = cy - h * 0.1
    pr = size * 0.06
    draw.ellipse([cx - pr, pin_cy - pr, cx + pr, pin_cy + pr], fill=color)


def draw_island_symbol(draw, cx, cy, size, color):
    """Draw a palm tree on an island."""
    lw = max(2, size // 20)
    # Island (arc/mound)
    iw = size * 0.4
    draw.arc([cx - iw, cy + size * 0.05, cx + iw, cy + size * 0.55], 180, 360, fill=color, width=lw + 1)
    # Palm trunk (curved line)
    trunk_points = []
    for i in range(20):
        t = i / 19
        x = cx - size * 0.05 + t * size * 0.08
        y = cy + size * 0.1 - t * size * 0.45
        trunk_points.append((x, y))
    for i in range(len(trunk_points) - 1):
        draw.line([trunk_points[i], trunk_points[i + 1]], fill=color, width=lw)
    # Palm fronds (lines radiating from top)
    top = trunk_points[-1]
    for angle_deg in [-150, -120, -60, -30, 0]:
        angle = math.radians(angle_deg)
        ex = top[0] + size * 0.2 * math.cos(angle)
        ey = top[1] + size * 0.2 * math.sin(angle)
        draw.line([top, (ex, ey)], fill=color, width=max(1, lw - 1))


def draw_letter_symbol(draw, cx, cy, size, color):
    """Draw ABC letters."""
    font = load_bold_font(size * 0.55)
    text = "Aa"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text((cx - tw // 2 - bbox[0], cy - th // 2 - bbox[1]), text, fill=color, font=font)


def draw_pattern_symbol(draw, cx, cy, size, color):
    """Draw a pattern grid symbol."""
    lw = max(2, size // 25)
    s = size * 0.35
    # Grid of dots and lines
    for row in range(3):
        for col in range(3):
            x = cx + (col - 1) * s * 0.7
            y = cy + (row - 1) * s * 0.7
            r = size * 0.04
            if (row + col) % 2 == 0:
                draw.ellipse([x - r, y - r, x + r, y + r], fill=color)
            else:
                draw.rectangle([x - r, y - r, x + r, y + r], fill=color)
    # Connecting lines
    draw.line([(cx - s * 0.7, cy - s * 0.7), (cx + s * 0.7, cy + s * 0.7)], fill=color, width=lw)
    draw.line([(cx + s * 0.7, cy - s * 0.7), (cx - s * 0.7, cy + s * 0.7)], fill=color, width=lw)


def draw_hundred_symbol(draw, cx, cy, size, color):
    """Draw '100' text."""
    font = load_bold_font(size * 0.5)
    text = "100"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text((cx - tw // 2 - bbox[0], cy - th // 2 - bbox[1]), text, fill=color, font=font)


def draw_ruler_symbol(draw, cx, cy, size, color):
    """Draw a ruler."""
    lw = max(2, size // 20)
    w = size * 0.12
    h = size * 0.42
    # Ruler body
    draw.rectangle([cx - w, cy - h, cx + w, cy + h], outline=color, width=lw)
    # Tick marks
    for i in range(9):
        y = cy - h + (i + 1) * (2 * h / 10)
        tick_len = w * 0.7 if i % 2 == 0 else w * 0.4
        draw.line([(cx - w, y), (cx - w + tick_len, y)], fill=color, width=max(1, lw - 1))


def draw_capital_symbol(draw, cx, cy, size, color):
    """Draw a capitol building dome."""
    lw = max(2, size // 20)
    w = size * 0.38
    h = size * 0.2
    # Base
    draw.rectangle([cx - w, cy + h * 0.5, cx + w, cy + h * 1.2], outline=color, width=lw)
    # Columns
    for x_off in [-0.6, -0.2, 0.2, 0.6]:
        x = cx + w * x_off
        draw.line([(x, cy - h * 0.3), (x, cy + h * 0.5)], fill=color, width=lw)
    # Dome
    draw.arc([cx - w * 0.5, cy - h * 1.8, cx + w * 0.5, cy - h * 0.3], 180, 360, fill=color, width=lw + 1)
    # Pediment (triangle roof)
    draw.line([(cx - w, cy - h * 0.3), (cx + w, cy - h * 0.3)], fill=color, width=lw)


def draw_palette_symbol(draw, cx, cy, size, color):
    """Draw a paint palette."""
    lw = max(2, size // 20)
    r = size * 0.4
    # Palette shape (oval)
    draw.ellipse([cx - r, cy - r * 0.75, cx + r, cy + r * 0.75], outline=color, width=lw)
    # Paint blobs
    blob_r = size * 0.055
    for angle_deg, dist in [(45, 0.55), (90, 0.5), (135, 0.55), (180, 0.45), (0, 0.45)]:
        angle = math.radians(angle_deg)
        bx = cx + r * dist * math.cos(angle)
        by = cy + r * 0.75 * dist * math.sin(angle)
        draw.ellipse([bx - blob_r, by - blob_r, bx + blob_r, by + blob_r], fill=color)
    # Thumb hole
    draw.ellipse([cx + r * 0.15, cy + r * 0.1, cx + r * 0.4, cy + r * 0.35], outline=color, width=lw)


def draw_rainbow_symbol(draw, cx, cy, size, color):
    """Draw a rainbow arc."""
    lw = max(3, size // 15)
    r = size * 0.42
    # Multiple concentric arcs
    for i, offset in enumerate([0, 0.12, 0.24]):
        arc_r = r - r * offset
        draw.arc([cx - arc_r, cy - arc_r * 0.6, cx + arc_r, cy + arc_r * 1.2],
                 200, 340, fill=color, width=lw)


def create_achievement_icon(achievement_id, title, tier, theme, size=512):
    """Create a 512x512 achievement icon."""
    ss = 2
    s = size * ss
    colors = TIER_COLORS[tier]

    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx, cy = s // 2, s // 2
    outer_r = s // 2 - s // 20

    # Background circle with gradient
    draw_circle_gradient(draw, cx, cy, outer_r, colors["bg"], "#000000")

    # Tier-colored ring
    ring_width = s // 14
    draw.ellipse(
        [cx - outer_r, cy - outer_r, cx + outer_r, cy + outer_r],
        outline=colors["ring"], width=ring_width
    )
    # Inner ring highlight
    inner_ring_r = outer_r - ring_width
    draw.ellipse(
        [cx - inner_ring_r, cy - inner_ring_r, cx + inner_ring_r, cy + inner_ring_r],
        outline=colors["ring_dark"], width=max(2, s // 100)
    )

    # Central symbol
    symbol_color = colors["glow"]
    symbol_size = int(s * 0.38)
    symbol_cy = cy - s // 30  # slightly above center to leave room for title

    DRAW_FUNCTIONS = {
        "globe": draw_globe_symbol,
        "flag": draw_flag_symbol,
        "shield": draw_shield_symbol,
        "trophy": draw_trophy_symbol,
        "diamond": draw_diamond_symbol,
        "star": draw_star_symbol,
        "clock": draw_clock_symbol,
        "compass": draw_compass_symbol,
        "book": draw_book_symbol,
        "map": draw_map_symbol,
        "island": draw_island_symbol,
        "letter": draw_letter_symbol,
        "pattern": draw_pattern_symbol,
        "hundred": draw_hundred_symbol,
        "ruler": draw_ruler_symbol,
        "capital": draw_capital_symbol,
        "palette": draw_palette_symbol,
        "rainbow": draw_rainbow_symbol,
    }
    draw_fn = DRAW_FUNCTIONS.get(theme, draw_star_symbol)
    draw_fn(draw, cx, symbol_cy, symbol_size, symbol_color)

    # Tier label at the bottom
    tier_font = load_bold_font(s * 0.055)

    tier_text = tier
    bbox = draw.textbbox((0, 0), tier_text, font=tier_font)
    tw = bbox[2] - bbox[0]
    draw.text(
        (cx - tw // 2 - bbox[0], cy + int(s * 0.32)),
        tier_text, fill=colors["ring"], font=tier_font
    )

    # Downsample
    img = img.resize((size, size), Image.LANCZOS)
    return img


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"Generating achievements ZIP for {len(ACHIEVEMENTS)} achievements...")

    # ── Generate icons ──────────────────────────────────────────────────
    icon_filenames = {}
    for i, (aid, title, desc, tier, theme) in enumerate(ACHIEVEMENTS):
        fname = f"{aid}.png"
        icon_filenames[aid] = fname
        icon = create_achievement_icon(aid, title, tier, theme)
        icon_path = os.path.join(OUTPUT_DIR, fname)
        icon.save(icon_path, "PNG")
        print(f"  [{i+1}/{len(ACHIEVEMENTS)}] {fname} ({tier})")

    # ── AchievementsMetadata.csv ────────────────────────────────────────
    # Columns (NO header): Name, Description, Incremental value, Steps Needed, Initial State, Points, List Order
    metadata_buf = io.StringIO()
    writer = csv.writer(metadata_buf)
    for i, (aid, title, desc, tier, theme) in enumerate(ACHIEVEMENTS):
        points = TIER_POINTS[tier]
        initial_state = "Revealed"
        writer.writerow([title, desc, "False", "", initial_state, points, i + 1])
    metadata_csv = metadata_buf.getvalue()
    with open(os.path.join(OUTPUT_DIR, "AchievementsMetadata.csv"), "w", newline="", encoding="utf-8") as f:
        f.write(metadata_csv)
    print(f"  AchievementsMetadata.csv ({len(ACHIEVEMENTS)} rows)")

    # ── AchievementsLocalizations.csv ───────────────────────────────────
    # Columns (NO header): Name, Localized name, Localized description, locale
    # Default locale comes from Metadata; this file is for NON-default locales only.
    # Empty file since we only support the default locale (en-US).
    with open(os.path.join(OUTPUT_DIR, "AchievementsLocalizations.csv"), "w", newline="", encoding="utf-8") as f:
        pass  # empty file
    print(f"  AchievementsLocalizations.csv (empty - default locale only)")

    # ── AchievementsIconsMappings.csv ────────────────────────────────────
    # Columns (NO header): Name, icon filename
    mapping_buf = io.StringIO()
    writer = csv.writer(mapping_buf)
    for aid, title, desc, tier, theme in ACHIEVEMENTS:
        writer.writerow([title, icon_filenames[aid]])
    mapping_csv = mapping_buf.getvalue()
    with open(os.path.join(OUTPUT_DIR, "AchievementsIconsMappings.csv"), "w", newline="", encoding="utf-8") as f:
        f.write(mapping_csv)
    print(f"  AchievementsIconsMappings.csv ({len(ACHIEVEMENTS)} rows)")

    # ── Create ZIP ──────────────────────────────────────────────────────
    zip_path = os.path.join(os.path.dirname(__file__), "store_assets", "achievements_import.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        # CSVs
        zf.write(os.path.join(OUTPUT_DIR, "AchievementsMetadata.csv"), "AchievementsMetadata.csv")
        zf.write(os.path.join(OUTPUT_DIR, "AchievementsLocalizations.csv"), "AchievementsLocalizations.csv")
        zf.write(os.path.join(OUTPUT_DIR, "AchievementsIconsMappings.csv"), "AchievementsIconsMappings.csv")
        # Icons
        for aid in icon_filenames:
            fname = icon_filenames[aid]
            zf.write(os.path.join(OUTPUT_DIR, fname), fname)

    zip_size = os.path.getsize(zip_path)
    print(f"\n  achievements_import.zip: {zip_size / 1024:.0f} KB ({len(ACHIEVEMENTS)} achievements + 3 CSVs)")

    # ── Summary ─────────────────────────────────────────────────────────
    bronze = sum(1 for a in ACHIEVEMENTS if a[3] == "BRONZE")
    silver = sum(1 for a in ACHIEVEMENTS if a[3] == "SILVER")
    gold = sum(1 for a in ACHIEVEMENTS if a[3] == "GOLD")
    total_points = sum(TIER_POINTS[a[3]] for a in ACHIEVEMENTS)
    print(f"\n  Summary:")
    print(f"    Bronze: {bronze} ({bronze * 5} pts)")
    print(f"    Silver: {silver} ({silver * 15} pts)")
    print(f"    Gold:   {gold} ({gold * 30} pts)")
    print(f"    Total:  {len(ACHIEVEMENTS)} achievements, {total_points} points")
    print(f"\n  Output: {zip_path}")


if __name__ == "__main__":
    check_readme(len(ACHIEVEMENTS))
    if "--check" in sys.argv:
        print(f"OK: {len(ACHIEVEMENTS)} achievements, themes and README in sync with Achievement.kt")
    else:
        main()
