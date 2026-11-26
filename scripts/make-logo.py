"""
Draws the stayline logo (a cut gem, ruby on the left and emerald on the
right, the same mark as the Android app's launcher icon) from geometry and
writes every logo asset, so the mark stays crisp at any size:

- assets/stayline-logo.svg               vector master
- assets/stayline-logo-512.png           large PNG (README, website)
- assets/stayline.ico                    app/installer icon, 16 to 256 px
- crates/tray/ui/icons/stayline-logo.svg    in-app logo (drawn at the size shown)
- crates/tray/ui/icons/stayline-icon-32.png  window/taskbar icon
- assets/installer-dialog.bmp            installer welcome/finish artwork
- assets/installer-banner.bmp            installer top banner
- fastlane/metadata/android/en-US/images/icon.png             store icon
- fastlane/metadata/android/en-US/images/featureGraphic.png   store banner

    python scripts/make-logo.py
"""

import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUBY = (255, 59, 107)
EMERALD = (25, 197, 138)
WHITE = (255, 255, 255)
OBSIDIAN = (11, 10, 15)  # the app's dark background
N = 1024  # design grid; everything below is in these units
C = N / 2
R = N * 0.47  # gem radius, leaving a small margin
TABLE = 0.56  # inner octagon (the table) relative to the gem


def octagon(radius):
    """Flat-topped octagon around the centre, clockwise from the top left."""
    return [
        (C + radius * math.cos(math.radians(-112.5 + 45 * i)), C + radius * math.sin(math.radians(-112.5 + 45 * i)))
        for i in range(8)
    ]


OUTER = octagon(R)
INNER = octagon(R * TABLE)


def facet(i):
    j = (i + 1) % 8
    return [OUTER[i], OUTER[j], INNER[j], INNER[i]]


# Facets lit from the top: (facet index, colour, opacity). Facet 0 is the
# top, then clockwise. Matches the Android launcher icon.
SHADING = [
    (0, WHITE, 0.32),
    (7, WHITE, 0.18),
    (1, WHITE, 0.18),
    (5, (0, 0, 0), 0.16),
    (3, (0, 0, 0), 0.16),
    (4, (0, 0, 0), 0.28),
]
TABLE_TINT = (WHITE, 0.16)


def draw(scale: int) -> Image.Image:
    """Renders the logo at N*scale pixels (scale for supersampling)."""
    s = scale
    size = N * s
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # Two-tone gem: draw the gem as a mask, colour each half.
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).polygon([(x * s, y * s) for x, y in OUTER], fill=255)
    halves = Image.new("RGBA", (size, size), RUBY + (255,))
    ImageDraw.Draw(halves).rectangle((size // 2, 0, size, size), fill=EMERALD + (255,))
    img.paste(halves, (0, 0), mask)

    def tint(points, colour, alpha):
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(layer).polygon([(x * s, y * s) for x, y in points], fill=colour + (round(alpha * 255),))
        img.alpha_composite(layer)

    for i, colour, alpha in SHADING:
        tint(facet(i), colour, alpha)
    tint(INNER, *TABLE_TINT)
    return img


def svg() -> str:
    def path(points):
        return "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in points) + " Z"

    hex_ = lambda c: "#%02x%02x%02x" % c
    layers = "\n".join(
        f'  <path d="{path(facet(i))}" fill="{hex_(colour)}" fill-opacity="{alpha}"/>' for i, colour, alpha in SHADING
    )
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {N} {N}">
  <defs>
    <clipPath id="gem"><path d="{path(OUTER)}"/></clipPath>
  </defs>
  <g clip-path="url(#gem)">
    <rect width="{N // 2}" height="{N}" fill="{hex_(RUBY)}"/>
    <rect x="{N // 2}" width="{N // 2}" height="{N}" fill="{hex_(EMERALD)}"/>
  </g>
{layers}
  <path d="{path(INNER)}" fill="#ffffff" fill-opacity="{TABLE_TINT[1]}"/>
</svg>
"""


def font(size, weight=800):
    """Manrope (ExtraBold by default), the app's font, falling back to the system's."""
    manrope = os.path.join(ROOT, "crates", "tray", "ui", "fonts", "Manrope.ttf")
    try:
        f = ImageFont.truetype(manrope, size)
        f.set_variation_by_axes([weight])
        return f
    except (OSError, ValueError):
        pass
    for name in ("segoeuib.ttf", "arialbd.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default(size)


def installer_art(at):
    """WiX UI bitmaps: the dialog image fills the welcome and finish pages
    (text is drawn right of x=164), the banner tops the other pages."""
    dialog = Image.new("RGB", (493, 312), WHITE)
    d = ImageDraw.Draw(dialog)
    d.rectangle((0, 0, 163, 312), fill=OBSIDIAN)
    # A soft emerald glow behind the gem, as in the app.
    glow = Image.new("RGBA", (164, 312), (0, 0, 0, 0))
    for r in range(70, 0, -2):
        alpha = int(38 * (1 - r / 70) ** 1.6)
        ImageDraw.Draw(glow).ellipse((82 - r, 128 - r, 82 + r, 128 + r), fill=EMERALD + (alpha,))
    dialog.paste(glow, (0, 0), glow)
    logo = at(84)
    dialog.paste(logo, ((164 - 84) // 2, 86), logo)
    f = font(17)
    text = "Stayline VPN"
    w = d.textlength(text, font=f)
    d.text(((164 - w) / 2, 188), text, font=f, fill=WHITE)

    banner = Image.new("RGB", (493, 58), WHITE)
    small = at(40)
    banner.paste(small, (493 - 40 - 12, 9), small)

    dialog.save(os.path.join(ROOT, "assets", "installer-dialog.bmp"))
    banner.save(os.path.join(ROOT, "assets", "installer-banner.bmp"))


def glow(size, centre, radius, colour, strength):
    """A soft radial glow, as behind the gem in the apps."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = centre
    for r in range(radius, 0, -3):
        alpha = int(strength * (1 - r / radius) ** 1.8)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=colour + (alpha,))
    return layer


def store_art(at):
    """F-Droid's listing: the launcher icon (gem on obsidian) and a banner."""
    out = os.path.join(ROOT, "fastlane", "metadata", "android", "en-US", "images")
    os.makedirs(out, exist_ok=True)

    icon = Image.new("RGBA", (512, 512), OBSIDIAN + (255,))
    icon.alpha_composite(glow((512, 512), (256, 256), 240, EMERALD, 70))
    gem = at(300)
    icon.alpha_composite(gem, (106, 106))
    icon.convert("RGB").save(os.path.join(out, "icon.png"), optimize=True)

    banner = Image.new("RGBA", (1024, 500), OBSIDIAN + (255,))
    banner.alpha_composite(glow((1024, 500), (250, 250), 260, EMERALD, 60))
    banner.alpha_composite(glow((1024, 500), (900, 40), 260, RUBY, 28))
    gem = at(260)
    banner.alpha_composite(gem, (120, 120))
    d = ImageDraw.Draw(banner)
    room = 1024 - 450 - 48

    def fitted(text, size, weight=800):
        """The largest font up to `size` at which `text` fits the room."""
        while size > 12 and d.textlength(text, font=font(size, weight)) > room:
            size -= 2
        return font(size, weight)

    d.text((450, 160), "Stayline VPN", font=fitted("Stayline VPN", 76), fill=WHITE)
    tagline = "The VPN that stays connected"
    d.text((454, 268), tagline, font=fitted(tagline, 32, 600), fill=(156, 149, 168))
    banner.convert("RGB").save(os.path.join(out, "featureGraphic.png"), optimize=True)


def main():
    master = draw(scale=2).resize((N, N), Image.Resampling.LANCZOS)

    def at(px):
        return master.resize((px, px), Image.Resampling.LANCZOS)

    icons = os.path.join(ROOT, "crates", "tray", "ui", "icons")
    for path in (os.path.join(ROOT, "assets", "stayline-logo.svg"), os.path.join(icons, "stayline-logo.svg")):
        with open(path, "w", newline="\n") as f:
            f.write(svg())
    at(512).save(os.path.join(ROOT, "assets", "stayline-logo-512.png"), optimize=True)
    # The app scales bitmaps without smoothing, so the window icon is
    # pre-rendered at the size Windows shows it; in-app logos use the SVG.
    at(32).save(os.path.join(icons, "stayline-icon-32.png"), optimize=True)
    # Every icon size rendered smoothly on its own, not left to the ICO writer.
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    images = [at(s) for s in sizes]
    images[-1].save(
        os.path.join(ROOT, "assets", "stayline.ico"),
        sizes=[(s, s) for s in sizes],
        append_images=images[:-1],
    )
    installer_art(at)
    store_art(at)
    print("wrote the SVG master, 512 px PNG, application icon, in-app logo, installer and store artwork")


if __name__ == "__main__":
    main()
