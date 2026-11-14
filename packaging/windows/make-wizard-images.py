"""Картинки мастера установки Inno Setup: большая слева (приветствие/финал) и маленькая в шапке.
Рисуются из иконки приложения и шрифта Unbounded в трёх масштабах (100/150/200%), Inno сам выберет по DPI.
   python packaging/windows/make-wizard-images.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
ICON = Image.open(ROOT / "desktop/icons/icon.png").convert("RGBA")
FONT = ROOT / "desktop/src/main/resources/fonts/unbounded-900.ttf"
HAY, INK, PAPER, RED = (255, 212, 71), (27, 19, 16), (255, 248, 230), (229, 56, 59)


def large(scale: float) -> Image.Image:
    w, h = round(164 * scale), round(314 * scale)
    img = Image.new("RGB", (w, h), HAY)
    d = ImageDraw.Draw(img)
    step = max(6, round(12 * scale))
    for y in range(step // 2, h, step):  # точечный узор, как фон в шапке приложения
        for x in range(step // 2, w, step):
            r = max(1, round(scale))
            d.ellipse((x - r / 2, y - r / 2, x + r / 2, y + r / 2), fill=(232, 186, 50))
    # «Брутальная» карточка с иконкой: чёрная рамка и сдвинутая тень.
    cs = round(112 * scale)
    cx, cy = (w - cs) // 2, round(36 * scale)
    off, bw = round(6 * scale), max(2, round(3 * scale))
    d.rounded_rectangle((cx + off, cy + off, cx + cs + off, cy + cs + off), radius=round(22 * scale), fill=INK)
    d.rounded_rectangle((cx, cy, cx + cs, cy + cs), radius=round(22 * scale), fill=PAPER, outline=INK, width=bw)
    ic = ICON.resize((round(cs * 0.8),) * 2, Image.LANCZOS)
    img.paste(ic, (cx + (cs - ic.width) // 2, cy + (cs - ic.height) // 2), ic)
    # Название в три строки.
    y = cy + cs + round(28 * scale)
    for i, word in enumerate(["ЕБИСЬ", "ОНО", "КОНЁМ"]):
        size = round((24 if i < 2 else 25) * scale)
        f = ImageFont.truetype(str(FONT), size)
        tw = d.textlength(word, font=f)
        d.text(((w - tw) / 2, y), word, font=f, fill=INK)
        y += round(size * 1.18)
    # Красная плашка-подпись.
    f = ImageFont.truetype(str(FONT), round(9 * scale))
    label = "ТРЕКЕР ХЕРНИ"
    tw = d.textlength(label, font=f)
    px, py = round(8 * scale), round(4 * scale)
    bx, by = (w - tw) / 2 - px, y + round(14 * scale)
    d.rounded_rectangle((bx, by, bx + tw + 2 * px, by + f.size + 2 * py + round(2 * scale)), radius=round(6 * scale), fill=RED, outline=INK, width=max(1, round(2 * scale)))
    d.text((bx + px, by + py), label, font=f, fill=(255, 255, 255))
    return img


def small(scale: float) -> Image.Image:
    w, h = round(55 * scale), round(58 * scale)
    img = Image.new("RGB", (w, h), (255, 255, 255))
    ic = ICON.resize((min(w, h),) * 2, Image.LANCZOS)
    img.paste(ic, ((w - ic.width) // 2, (h - ic.height) // 2), ic)
    return img


for pct in (100, 150, 200):
    s = pct / 100
    large(s).save(OUT / f"wizard-{pct}.bmp")
    small(s).save(OUT / f"wizard-small-{pct}.bmp")
print("ok:", OUT)
