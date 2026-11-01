#!/usr/bin/env python3
"""Build a tiny diagnostic pack for the EywaPortal custom-font lookup.

Install: python -m pip install cairosvg
Run: python tools/build-font-test-pack.py
Output: build/eywaportal-font-test.zip
"""
from __future__ import annotations

import json
import zipfile
from pathlib import Path

import cairosvg

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "eywaportal-font-test.zip"
TEMP = ROOT / "build" / "eywaportal-font-test"
NAMESPACE = "eywaportal"
GLYPH = "\uf500"


def main() -> None:
    font_dir = TEMP / "assets" / NAMESPACE / "font"
    texture_dir = TEMP / "assets" / NAMESPACE / "textures" / "gui"
    font_dir.mkdir(parents=True, exist_ok=True)
    texture_dir.mkdir(parents=True, exist_ok=True)

    # Deliberately unmistakable 16x16 test glyph: magenta background, white X.
    svg = """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">
      <rect width="16" height="16" fill="#ff00ff"/>
      <path d="M2 2 L14 14 M14 2 L2 14" stroke="#ffffff" stroke-width="2"/>
    </svg>"""
    cairosvg.svg2png(bytestring=svg.encode("utf-8"),
                     write_to=str(texture_dir / "font_test.png"),
                     output_width=16, output_height=16)

    provider = {
        "providers": [{
            "type": "bitmap",
            "file": f"{NAMESPACE}:gui/font_test.png",
            "height": 16,
            "ascent": 16,
            "chars": [GLYPH]
        }]
    }
    (font_dir / "portal.json").write_text(
        json.dumps(provider, ensure_ascii=True, indent=2) + "\n",
        encoding="utf-8"
    )
    (TEMP / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "min_format": [97, 1],
            "max_format": 97,
            "description": "EywaPortal font lookup diagnostic (U+F500)"
        }
    }, indent=2) + "\n", encoding="utf-8")
    (TEMP / "TEST.txt").write_text(
        "Temporarily enable this pack on Java Edition, then run:\n"
        '/tellraw @s {"text":"\\uF500","font":"eywaportal:portal"}\n\n'
        "Expected: a magenta 16x16 square with a white X, not a missing-glyph square.\n"
        "If this works, font id/path and U+F500 load; the issue is in the main pack or its merge.\n"
        "If this does not work, inspect pack loading/version and the client resource-pack log.\n",
        encoding="utf-8"
    )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(TEMP.rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(TEMP).as_posix())
    print(f"Created {OUT}")


if __name__ == "__main__":
    main()
