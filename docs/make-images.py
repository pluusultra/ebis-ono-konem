"""Баннер для README и коллаж виджетов из превью Android-виджетов.
   python docs/make-images.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONTS = ROOT / "desktop/src/main/resources/fonts"
HAY, DOT, INK, PAPER, RED = (255, 212, 71), (232, 186, 50), (27, 19, 16), (255, 248, 230), (229, 56, 59)


def dots(img, color, step=22, r=2):
    d = ImageDraw.Draw(img)
    for y in range(step // 2, img.height, step):
        for x in range(step // 2, img.width, step):
            d.ellipse((x - r, y - r, x + r, y + r), fill=color)


def brutal(d, box, radius, fill, off=8, width=4):
    x0, y0, x1, y1 = box
    d.rounded_rectangle((x0 + off, y0 + off, x1 + off, y1 + off), radius=radius, fill=INK)
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=INK, width=width)


def banner():
    w, h = 1600, 480
    img = Image.new("RGB", (w, h), HAY)
    dots(img, DOT)
    d = ImageDraw.Draw(img)
    icon = Image.open(ROOT / "desktop/icons/icon.png").convert("RGBA").resize((210, 210), Image.LANCZOS)
    brutal(d, (110, 115, 360, 365), 48, PAPER, off=12, width=6)
    img.paste(icon, (130, 135), icon)
    title = ImageFont.truetype(str(FONTS / "unbounded-900.ttf"), 92)
    d.text((430, 105), "Ебись оно", font=title, fill=INK)
    d.text((430, 210), "конём", font=title, fill=INK)
    sub = ImageFont.truetype(str(FONTS / "onest-500.ttf"), 34)
    d.text((432, 335), "Трекер задач, который ноет, пока не сделаешь", font=sub, fill=(74, 56, 38))
    tag = ImageFont.truetype(str(FONTS / "jbmono-700.ttf"), 24)
    x = 432
    for text, bg, fg in [("Android", PAPER, INK), ("Windows", PAPER, INK), ("Linux", PAPER, INK), ("open source", RED, (255, 255, 255))]:
        tw = d.textlength(text, font=tag)
        brutal(d, (x, 395, x + tw + 32, 437), 10, bg, off=4, width=3)
        d.text((x + 16, 400), text, font=tag, fill=fg)
        x += tw + 50
    img.save(ROOT / "docs/banner.png", optimize=True)


def widgets():
    src = ROOT / "app/src/main/res/drawable-nodpi"
    im = {n: Image.open(src / f"widget_preview_{n}.png").convert("RGBA") for n in ["today", "random", "counter", "streak", "hot", "quick"]}
    pad = 40
    # Ряд 1: today + random (960×450). Ряд 2: counter + streak (450×450) + столбик hot/quick (960×192).
    w = pad * 3 + 960 * 2
    h = pad * 3 + 450 * 2
    img = Image.new("RGBA", (w, h), PAPER + (255,))
    dots(img, (240, 228, 200, 255), step=26, r=2)
    img.alpha_composite(im["today"], (pad, pad))
    img.alpha_composite(im["random"], (pad * 2 + 960, pad))
    y = pad * 2 + 450
    img.alpha_composite(im["counter"], (pad, y))
    img.alpha_composite(im["streak"], (pad + 450 + 60, y))
    img.alpha_composite(im["hot"], (pad * 2 + 960, y + 20))
    img.alpha_composite(im["quick"], (pad * 2 + 960, y + 450 - 192 - 20))
    img.convert("RGB").save(ROOT / "docs/screenshots/android-widgets.png", optimize=True)


banner()
widgets()
print("ok")
