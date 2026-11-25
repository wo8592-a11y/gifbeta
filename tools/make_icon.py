"""정보채널 앱 아이콘을 밀도별로 다시 그립니다.

밝은 남색 그라데이션 위에, 여러 사람이 올린 글이 겹쳐 보이는
카드와 말풍선을 올립니다. 실행: py -3 tools/make_icon.py
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SIZE = 1024

# Adaptive icon safe zone is the center 66%. Stay inside ~22%–78%.
BLUE = (37, 99, 235, 255)
BLUE_SOFT = (147, 197, 253, 255)
INK = (30, 41, 59, 255)
WHITE = (255, 255, 255, 255)
WHITE_SOFT = (255, 255, 255, 230)


def gradient() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    start = (125, 211, 252)
    mid = (59, 130, 246)
    end = (67, 56, 202)
    for y in range(SIZE):
        for x in range(SIZE):
            t = (x * 0.85 + y * 1.15) / (2 * (SIZE - 1))
            t = max(0.0, min(1.0, t))
            if t < 0.5:
                color = _mix(start, mid, t / 0.5)
            else:
                color = _mix(mid, end, (t - 0.5) / 0.5)
            pixels[x, y] = (*color, 255)

    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse((-80, -160, 760, 680), fill=(255, 255, 255, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    image.alpha_composite(glow)
    return image


def _mix(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def star(draw: ImageDraw.ImageDraw, cx: float, cy: float, outer: float, inner: float, fill: tuple[int, int, int, int]) -> None:
    points: list[tuple[float, float]] = []
    for i in range(8):
        radius = outer if i % 2 == 0 else inner
        angle = math.radians(-90 + i * 45)
        points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    draw.polygon(points, fill=fill)


def symbol() -> Image.Image:
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    art = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(art)

    back_box = (214, 268, 620, 690)
    front_box = (392, 360, 812, 748)
    shadow_draw.rounded_rectangle(back_box, radius=78, fill=(15, 23, 42, 90))
    shadow_draw.rounded_rectangle(front_box, radius=72, fill=(15, 23, 42, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))

    back = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    back_draw = ImageDraw.Draw(back)
    back_draw.rounded_rectangle(back_box, radius=78, fill=WHITE_SOFT)
    back_draw.polygon([(268, 620), (360, 620), (250, 742)], fill=WHITE_SOFT)
    back_draw.rounded_rectangle((292, 360, 500, 402), radius=16, fill=BLUE_SOFT)
    back_draw.rounded_rectangle((292, 432, 430, 468), radius=14, fill=(191, 219, 254, 255))
    back = back.rotate(14, resample=Image.Resampling.BICUBIC, center=(417, 479))

    draw.rounded_rectangle(front_box, radius=72, fill=WHITE)
    draw.ellipse((448, 432, 548, 532), fill=BLUE)
    draw.rounded_rectangle((590, 446, 748, 486), radius=14, fill=INK)
    draw.rounded_rectangle((590, 508, 700, 540), radius=12, fill=BLUE_SOFT)
    draw.rounded_rectangle((448, 620, 610, 676), radius=28, fill=BLUE)
    draw.ellipse((650, 628, 698, 676), fill=(219, 234, 254, 255))
    draw.ellipse((714, 628, 762, 676), fill=(191, 219, 254, 255))

    art = art.rotate(-7, resample=Image.Resampling.BICUBIC, center=(602, 554))

    spark = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    spark_draw = ImageDraw.Draw(spark)
    star(spark_draw, 760, 292, 42, 16, WHITE)
    star(spark_draw, 692, 246, 16, 6, (255, 255, 255, 220))

    layer.alpha_composite(shadow)
    layer.alpha_composite(back)
    layer.alpha_composite(art)
    layer.alpha_composite(spark)
    return layer


def content_bounds(image: Image.Image) -> tuple[int, int, int, int]:
    alpha = image.getchannel("A")
    mask = alpha.point(lambda value: 255 if value > 12 else 0)
    bbox = mask.getbbox()
    if bbox is None:
        raise SystemExit("아이콘 전경이 비어 있습니다.")
    return bbox


def save_scaled(image: Image.Image, folder: str, name: str, px: int) -> None:
    destination = RES / folder
    destination.mkdir(parents=True, exist_ok=True)
    image.resize((px, px), Image.Resampling.LANCZOS).save(destination / name, optimize=True)


def circle_mask(image: Image.Image) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, image.size[0] - 1, image.size[1] - 1), fill=255)
    rounded = Image.new("RGBA", image.size, (0, 0, 0, 0))
    rounded.paste(image, mask=mask)
    return rounded


def main() -> None:
    foreground = symbol()
    left, top, right, bottom = content_bounds(foreground)
    print(
        "foreground bounds %:",
        round(left / SIZE * 100, 1),
        round(top / SIZE * 100, 1),
        round(right / SIZE * 100, 1),
        round(bottom / SIZE * 100, 1),
    )

    background = gradient()
    legacy = background.copy()
    legacy.alpha_composite(foreground)

    preview_dir = ROOT / "tools"
    preview_dir.mkdir(parents=True, exist_ok=True)
    legacy.resize((512, 512), Image.Resampling.LANCZOS).save(preview_dir / "icon_preview.png")
    circle_mask(legacy).resize((512, 512), Image.Resampling.LANCZOS).save(preview_dir / "icon_circle_preview.png")

    legacy_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    foreground_sizes = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }
    for folder, px in legacy_sizes.items():
        save_scaled(legacy, folder, "ic_launcher.png", px)
        save_scaled(circle_mask(legacy), folder, "ic_launcher_round.png", px)
    for folder, px in foreground_sizes.items():
        save_scaled(foreground, folder, "ic_launcher_foreground.png", px)
    print("icons written under", RES)


if __name__ == "__main__":
    main()
