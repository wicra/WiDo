# Usage : python3 scripts/icons/make_icons.py app/src/main/res docs/assets
#
# Génère l'icône et la bannière de WiDo : le mot « WiDo » en Plus Jakarta Sans extra-grasse,
# blanc, sur le fond du style Verre (noir #0B0B12 éclairé de halos bleu et violet).
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

RES = sys.argv[1]
FONT = RES + "/font/plus_jakarta_sans.ttf"

def halos(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    img = np.zeros((h, w, 3), np.float32)
    img[:] = (11, 11, 18)
    def glow(color, cx, cy, rx, ry, strength):
        d = np.sqrt(((x - cx * w) / (rx * w)) ** 2 + ((y - cy * h) / (ry * h)) ** 2)
        a = np.clip(1 - d / 0.7, 0, 1) ** 1.6 * strength
        for i in range(3):
            img[..., i] = img[..., i] * (1 - a) + color[i] * a
    glow((30, 91, 255), 0.12, 0.08, 0.95, 0.95, 0.95)
    glow((122, 43, 224), 0.95, 0.95, 0.9, 0.9, 0.9)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")

def wordmark(canvas, width_frac):
    w, h = canvas.size
    size = 200
    font = ImageFont.truetype(FONT, size)
    font.set_variation_by_axes([800])
    def measure(s):
        widths = [font.getlength(c) for c in "WiDo"]
        return sum(widths) - 0.035 * s * 3, widths
    total, _ = measure(size)
    size = int(size * width_frac * w / total)
    font = ImageFont.truetype(FONT, size)
    font.set_variation_by_axes([800])
    widths = [font.getlength(c) for c in "WiDo"]
    total = sum(widths) - 0.035 * size * 3
    ascent, descent = font.getmetrics()
    bbox = font.getbbox("WiDo")
    text_h = bbox[3] - bbox[1]
    x = (w - total) / 2
    y = (h - text_h) / 2 - bbox[1]
    draw = ImageDraw.Draw(canvas)
    for c, cw in zip("WiDo", widths):
        draw.text((x, y), c, font=font, fill=(255, 255, 255, 255))
        x += cw - 0.035 * size
    return canvas

def rounded(img, radius_frac):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), int(w * radius_frac), fill=255)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out

S = 4  # suréchantillonnage, puis réduction : bords nets
# Icônes classiques (anciens Android) : carré arrondi.
for folder, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    big = rounded(wordmark(halos(px * S, px * S), 0.74), 0.22)
    big.resize((px, px), Image.LANCZOS).save(f"{RES}/mipmap-{folder}/ic_launcher.png", optimize=True)
# Icône adaptative : fond à halos, premier plan transparent avec le mot dans la zone sûre.
halos(432 * 2, 432 * 2).resize((432, 432), Image.LANCZOS).save(f"{RES}/drawable-nodpi/ic_launcher_background.png", optimize=True)
fg = wordmark(Image.new("RGBA", (432 * S, 432 * S), (0, 0, 0, 0)), 0.52)
fg.resize((432, 432), Image.LANCZOS).save(f"{RES}/drawable-nodpi/ic_launcher_foreground.png", optimize=True)
# Bannière de l'écran d'accueil Android TV.
banner = wordmark(halos(640 * 2, 360 * 2), 0.5)
banner.resize((640, 360), Image.LANCZOS).convert("RGB").save(f"{RES}/drawable-xhdpi/banner.png", optimize=True)
# Images de la documentation.
DOCS = sys.argv[2]
wordmark(halos(1280, 720), 0.5).convert("RGB").save(f"{DOCS}/banner.png", optimize=True)
big = rounded(wordmark(halos(512 * 2, 512 * 2), 0.74), 0.22)
big.resize((512, 512), Image.LANCZOS).save(f"{DOCS}/icon.png", optimize=True)
