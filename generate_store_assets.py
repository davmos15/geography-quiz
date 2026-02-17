"""Generate Google Play Store assets: icon (512x512) and feature graphic (1024x500)."""

from PIL import Image, ImageDraw, ImageFont
import math
import os

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "store_assets")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Colors from the app theme
TEAL = "#00796B"
TEAL_DARK = "#004D40"
TEAL_LIGHT = "#009688"
WHITE = "#FFFFFF"
ORANGE = "#FF8F00"
ORANGE_LIGHT = "#FFA726"


def draw_globe(draw, cx, cy, radius, outline_color, fill_color, line_width=2.5):
    """Draw a globe with meridians and latitude lines."""
    # Globe filled circle
    draw.ellipse(
        [cx - radius, cy - radius, cx + radius, cy + radius],
        fill=fill_color,
        outline=outline_color,
        width=int(line_width),
    )

    # Vertical meridian (center)
    draw.line([(cx, cy - radius), (cx, cy + radius)], fill=outline_color, width=int(line_width * 0.7))

    # Horizontal equator
    draw.line([(cx - radius, cy), (cx + radius, cy)], fill=outline_color, width=int(line_width * 0.7))

    # Left meridian curve (elliptical arc)
    offset = radius * 0.33
    draw.arc(
        [cx - offset - radius * 0.3, cy - radius, cx - offset + radius * 0.3, cy + radius],
        start=0, end=360,
        fill=outline_color, width=int(line_width * 0.6),
    )

    # Right meridian curve
    draw.arc(
        [cx + offset - radius * 0.3, cy - radius, cx + offset + radius * 0.3, cy + radius],
        start=0, end=360,
        fill=outline_color, width=int(line_width * 0.6),
    )

    # Upper latitude line (arc curving upward)
    lat_offset = radius * 0.45
    draw.arc(
        [cx - radius * 0.92, cy - lat_offset - radius * 0.22,
         cx + radius * 0.92, cy - lat_offset + radius * 0.22],
        start=0, end=360,
        fill=outline_color, width=int(line_width * 0.5),
    )

    # Lower latitude line (arc curving downward)
    draw.arc(
        [cx - radius * 0.92, cy + lat_offset - radius * 0.22,
         cx + radius * 0.92, cy + lat_offset + radius * 0.22],
        start=0, end=360,
        fill=outline_color, width=int(line_width * 0.5),
    )


def draw_question_mark(draw, cx, cy, size, color):
    """Draw a bold question mark centered at (cx, cy)."""
    try:
        font = ImageFont.truetype("arialbd.ttf", int(size))
    except OSError:
        try:
            font = ImageFont.truetype("arial.ttf", int(size))
        except OSError:
            font = ImageFont.load_default()

    text = "?"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    x = cx - tw / 2 - bbox[0]
    y = cy - th / 2 - bbox[1]
    # Shadow for depth
    draw.text((x + 2, y + 2), text, fill="#CC7000", font=font)
    draw.text((x, y), text, fill=color, font=font)


def create_icon(size=512):
    """Create the 512x512 Play Store icon."""
    # Use supersampling for smoother results
    ss = 2  # supersample factor
    s = size * ss
    img = Image.new("RGBA", (s, s), TEAL)
    draw = ImageDraw.Draw(img)

    # Subtle radial gradient effect - draw concentric circles
    cx, cy = s // 2, s // 2
    for i in range(s // 2, 0, -1):
        t = i / (s // 2)
        r = int(0 * (1 - t) + 0 * t)
        g = int(77 * t + 121 * (1 - t))
        b = int(64 * t + 107 * (1 - t))
        color = f"#{r:02x}{g:02x}{b:02x}"
        draw.ellipse([cx - i, cy - i, cx + i, cy + i], fill=color)

    # Rounded square mask for Play Store style
    radius = s // 2
    globe_radius = int(s * 0.35)

    # Draw globe
    draw_globe(draw, cx, cy - int(s * 0.02), globe_radius, TEAL_DARK, WHITE, line_width=s * 0.015)

    # Draw question mark on the globe
    draw_question_mark(draw, cx + int(s * 0.02), cy - int(s * 0.02), s * 0.32, ORANGE)

    # Downsample
    img = img.resize((size, size), Image.LANCZOS)

    # Add rounded corners (Play Store icons have ~20% radius)
    corner_radius = int(size * 0.20)
    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([0, 0, size, size], radius=corner_radius, fill=255)
    img.putalpha(mask)

    return img


def create_feature_graphic(width=1024, height=500):
    """Create the 1024x500 feature graphic."""
    ss = 2
    w, h = width * ss, height * ss
    img = Image.new("RGB", (w, h))
    draw = ImageDraw.Draw(img)

    # Gradient background (teal dark to teal)
    for x in range(w):
        t = x / w
        r = int(0 * (1 - t) + 0 * t)
        g = int(64 * (1 - t) + 121 * t)
        b = int(53 * (1 - t) + 107 * t)
        draw.line([(x, 0), (x, h)], fill=(r, g, b))

    # Decorative faint globe outlines in background
    for bx, by, br, alpha in [
        (w * 0.1, h * 0.15, h * 0.3, 30),
        (w * 0.85, h * 0.8, h * 0.25, 20),
        (w * 0.5, h * 0.9, h * 0.15, 15),
    ]:
        overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ov_draw = ImageDraw.Draw(overlay)
        ov_draw.ellipse(
            [int(bx - br), int(by - br), int(bx + br), int(by + br)],
            outline=(255, 255, 255, alpha), width=int(h * 0.01),
        )
        img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
        draw = ImageDraw.Draw(img)

    # Main globe on the left side
    globe_cx = int(w * 0.22)
    globe_cy = int(h * 0.5)
    globe_r = int(h * 0.35)
    draw_globe(draw, globe_cx, globe_cy, globe_r, TEAL_DARK, WHITE, line_width=h * 0.012)
    draw_question_mark(draw, globe_cx + int(h * 0.01), globe_cy, h * 0.28, ORANGE)

    # App title text
    try:
        title_font = ImageFont.truetype("arialbd.ttf", int(h * 0.16))
    except OSError:
        title_font = ImageFont.truetype("arial.ttf", int(h * 0.16))

    try:
        subtitle_font = ImageFont.truetype("arial.ttf", int(h * 0.055))
    except OSError:
        subtitle_font = ImageFont.load_default()

    title_x = int(w * 0.42)

    # "Geography" on first line
    draw.text(
        (title_x, int(h * 0.24)),
        "Geography",
        fill=WHITE, font=title_font,
    )
    # "Quiz" on second line
    draw.text(
        (title_x, int(h * 0.44)),
        "Quiz",
        fill=ORANGE_LIGHT, font=title_font,
    )

    # Subtitle
    draw.text(
        (title_x, int(h * 0.72)),
        "How many countries can you name?",
        fill="#B2DFDB", font=subtitle_font,
    )

    # Downsample
    img = img.resize((width, height), Image.LANCZOS)
    return img


if __name__ == "__main__":
    print("Generating Play Store icon (512x512)...")
    icon = create_icon(512)
    icon_path = os.path.join(OUTPUT_DIR, "play_store_icon_512.png")
    icon.save(icon_path, "PNG")
    print(f"  Saved: {icon_path}")

    print("Generating feature graphic (1024x500)...")
    feature = create_feature_graphic(1024, 500)
    feature_path = os.path.join(OUTPUT_DIR, "feature_graphic_1024x500.png")
    feature.save(feature_path, "PNG")
    print(f"  Saved: {feature_path}")

    # Verify sizes
    for path in [icon_path, feature_path]:
        size_kb = os.path.getsize(path) / 1024
        img = Image.open(path)
        print(f"  {os.path.basename(path)}: {img.size[0]}x{img.size[1]}, {size_kb:.0f} KB")

    print("\nDone! Files in:", OUTPUT_DIR)
