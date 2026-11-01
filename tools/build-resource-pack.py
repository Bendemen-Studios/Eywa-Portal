#!/usr/bin/env python3
"""Build the Java custom-font overlay resource pack for EywaPortal.

Install: python -m pip install cairosvg
Output: build/eywaportal-java-resourcepack.zip
"""
from __future__ import annotations

import json
import math
import zipfile
from pathlib import Path

import cairosvg

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "eywaportal-java-resourcepack.zip"
WIDTH, HEIGHT = 512, 288
FRAME_COUNT = 8


def make_svg(frame: int) -> str:
    t = frame / (FRAME_COUNT - 1)
    dark = 0.12 + 0.78 * t
    core = 18 + 125 * t
    ring = 236 - 19 * t
    # The overlay is transparent at the start and closes into a luminous Eywa core.
    roots = []
    for i in range(18):
        angle = (math.tau * i / 18) + t * 0.45
        x1 = 256 + math.cos(angle) * (ring + 35)
        y1 = 144 + math.sin(angle) * (ring * 0.43 + 18)
        x2 = 256 + math.cos(angle + 0.38) * (ring - 35)
        y2 = 144 + math.sin(angle + 0.38) * (ring * 0.43 - 8)
        cx = 256 + math.cos(angle + 0.18) * (ring + 18)
        cy = 144 + math.sin(angle + 0.18) * (ring * 0.43 + 10)
        color = "#55ffff" if i % 3 else "#bd83ff"
        opacity = 0.38 + 0.45 * t
        roots.append(
            f'<path d="M{x1:.1f},{y1:.1f} Q{cx:.1f},{cy:.1f} {x2:.1f},{y2:.1f}" '
            f'fill="none" stroke="{color}" stroke-width="{1.5 + 2*t:.1f}" '
            f'stroke-linecap="round" opacity="{opacity:.2f}"/>'
        )
    specks = []
    for i in range(74):
        angle = math.tau * ((i * 37) % 74) / 74
        radius = 70 + ((i * 29) % 160)
        x = 256 + math.cos(angle + t * 0.6) * radius
        y = 144 + math.sin(angle + t * 0.6) * radius * 0.48
        size = 0.9 + (i % 4) * 0.55
        color = "#9bffff" if i % 4 else "#c49aff"
        specks.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{size:.1f}" fill="{color}" opacity="{0.2 + t*0.7:.2f}"/>')

    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">
  <defs>
    <radialGradient id="core">
      <stop offset="0" stop-color="#eaffff" stop-opacity="{0.22 + t*0.78:.2f}"/>
      <stop offset="0.45" stop-color="#43f4ff" stop-opacity="{0.08 + t*0.48:.2f}"/>
      <stop offset="1" stop-color="#8c45ff" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="vignette">
      <stop offset="0" stop-color="#02050c" stop-opacity="0"/>
      <stop offset="0.48" stop-color="#02050c" stop-opacity="{dark*0.22:.2f}"/>
      <stop offset="1" stop-color="#02050c" stop-opacity="{dark:.2f}"/>
    </radialGradient>
  </defs>
  <rect width="512" height="288" fill="#02050c" opacity="{dark:.3f}"/>
  <ellipse cx="256" cy="144" rx="{core*1.9:.1f}" ry="{core*1.9*0.56:.1f}" fill="url(#core)"/>
  {''.join(roots)}
  {''.join(specks)}
  <ellipse cx="256" cy="144" rx="{ring:.1f}" ry="{ring*0.43:.1f}" fill="none" stroke="#4cf7ff" stroke-width="{2+4*t:.1f}" opacity="{0.38+0.45*t:.2f}"/>
  <ellipse cx="256" cy="144" rx="{ring-13:.1f}" ry="{(ring-13)*0.43:.1f}" fill="none" stroke="#a36cff" stroke-width="2" opacity="{0.2+0.5*t:.2f}"/>
  <circle cx="256" cy="144" r="{core:.1f}" fill="url(#core)"/>
  <circle cx="256" cy="144" r="{max(3,core*0.16):.1f}" fill="#eaffff" opacity="{0.15+0.85*t:.2f}"/>
  <rect width="512" height="288" fill="url(#vignette)"/>
</svg>'''


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    temp = ROOT / "build" / "eywaportal-pack"
    assets = temp / "assets" / "eywaportal"
    font_dir = assets / "font"
    texture_dir = assets / "textures" / "gui"
    font_dir.mkdir(parents=True, exist_ok=True)
    texture_dir.mkdir(parents=True, exist_ok=True)

    providers = []
    for frame in range(FRAME_COUNT):
        name = f"portal_{frame}"
        png_path = texture_dir / f"{name}.png"
        cairosvg.svg2png(bytestring=make_svg(frame).encode("utf-8"), write_to=str(png_path),
                         output_width=WIDTH, output_height=HEIGHT)
        providers.append({
            "type": "bitmap",
            "file": f"eywaportal:gui/{name}.png",
            "height": HEIGHT,
            "ascent": HEIGHT // 2,
            "chars": [chr(0xF500 + frame)]
        })

    (font_dir / "portal.json").write_text(
        json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8"
    )

    # Keep an easy-to-read mapping alongside the generated font and textures.
    mapping_lines = [
        "EYWAPORTAL UNICODE MAPPING (JAVA)",
        "===============================",
        "File                         Unicode   Character",
    ]
    for frame in range(FRAME_COUNT):
        glyph = chr(0xF500 + frame)
        mapping_lines.append(f"portal_{frame}.png              U+{ord(glyph):04X}    {glyph}")
    (temp / "unicode_mapping.txt").write_text("\\n".join(mapping_lines) + "\\n", encoding="utf-8")
    (temp / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": 64,
            "min_format": [0, 0],
            "max_format": [999, 0],
            "description": "EywaPortal - fullscreen EywaMC portal transition"
        }
    }, indent=2) + "\n", encoding="utf-8")
    (temp / "README.txt").write_text(
        "EywaPortal Java overlay resource pack\n\n"
        "Merge the contents of this pack into your existing Java resource pack. "
        "Do not replace your existing pack.mcmeta; merge the assets/ folder and keep "
        "the pack.mcmeta appropriate for your Minecraft client version.\n"
        "The pack adds assets/eywaportal/font/portal.json and eight fullscreen overlay textures.\n"
        "After merging, ensure your server/RSPM sends the merged pack and clients accept it.\n"
        "Bedrock clients cannot use this Java custom-font overlay; they use the fallback effects.\n",
        encoding="utf-8"
    )

    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(temp.rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(temp).as_posix())
    print(f"Created {OUT}")


if __name__ == "__main__":
    main()
