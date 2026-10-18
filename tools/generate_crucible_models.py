#!/usr/bin/env python3
"""Generate the small crucible fill-level models and multipart blockstate."""

from __future__ import annotations

import json
from pathlib import Path


PROJECT = Path(__file__).resolve().parents[1]
BLOCK_MODELS = PROJECT / "src/main/resources/assets/gregtech/models/block"
BLOCKSTATES = PROJECT / "src/main/resources/assets/gregtech/blockstates"


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parts = [{"apply": {"model": "gregtech:block/small_crucible"}}]
    for level in range(1, 9):
        y = round(2.0 + level * 13.5 / 8.0, 4)
        model = {
            "render_type": "minecraft:translucent",
            "textures": {
                "content": "gregtech:block/crucible_content",
            },
            "elements": [
                {
                    "from": [2, y, 2],
                    "to": [14, round(y + 0.02, 4), 14],
                    "faces": {
                        "up": {
                            "texture": "#content",
                            "tintindex": 1,
                        },
                    },
                },
            ],
        }
        write_json(BLOCK_MODELS / f"small_crucible_fill_{level}.json", model)
        parts.append({
            "when": {"fill": str(level)},
            "apply": {"model": f"gregtech:block/small_crucible_fill_{level}"},
        })

    write_json(BLOCKSTATES / "small_crucible.json", {"multipart": parts})
    write_json(
        BLOCKSTATES / "crucible.json",
        {
            "variants": {
                f"fill={level}": {"model": "gregtech:block/crucible"}
                for level in range(9)
            },
        },
    )


if __name__ == "__main__":
    main()
