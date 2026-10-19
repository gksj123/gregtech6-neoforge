#!/usr/bin/env python3
"""Import and compose the CC0 GT6 block textures used by the current port."""

from __future__ import annotations

import os
import json
import shutil
from pathlib import Path

from PIL import Image, ImageChops


PORT = Path(__file__).resolve().parents[1]
LEGACY = Path(
    os.environ.get("GT6_LEGACY_HOME", PORT.parent / "gregtech6-legacy")
).expanduser().resolve()
SOURCE = LEGACY / "src/main/resources/assets/gregtech/textures/blocks"
OUTPUT = PORT / "src/main/resources/assets/gregtech/textures/block"
ITEM_SOURCE = LEGACY / "src/main/resources/assets/gregtech/textures/items/materialicons/METALLIC"
ITEM_OUTPUT = PORT / "src/main/resources/assets/gregtech/textures/item/material"
ITEM_MODEL_OUTPUT = PORT / "src/main/resources/assets/gregtech/models/item"

MOLD_OUTPUT_PREFIXES = (
    "ingot", "billet", "chunkGt", "plate", "plateTiny", "bolt", "stick",
    "stickLong", "casingSmall", "ring", "gearGt", "gearGtSmall",
    "toolHeadRawSword", "toolHeadRawPickaxe", "toolHeadRawSpade",
    "toolHeadRawShovel", "toolHeadRawUniversalSpade", "toolHeadRawAxe",
    "toolHeadRawAxeDouble", "toolHeadRawSaw", "toolHeadHammer",
    "toolHeadFile", "toolHeadScrewdriver", "toolHeadRawChisel",
    "toolHeadRawArrow", "toolHeadRawHoe", "toolHeadRawSense",
    "toolHeadRawPlow", "toolHeadBuilderwand", "nugget",
)


def compose(
    base: Path,
    overlay: Path | None,
    output: str,
    tint: tuple[int, int, int] | None = None,
) -> None:
    image = Image.open(base).convert("RGBA")
    if tint is not None:
        image = ImageChops.multiply(
            image,
            Image.new("RGBA", image.size, (*tint, 255)),
        )
    if overlay is not None and overlay.is_file():
        overlay_image = Image.open(overlay).convert("RGBA")
        # A few GT6 overlays are animated atlases. The static base is still the
        # correct source texture; animations are added as a separate renderer pass.
        if overlay_image.size == image.size:
            image = Image.alpha_composite(image, overlay_image)
    image.save(OUTPUT / output, optimize=True)


def compose_item(base: Path, overlay: Path | None, output: str) -> None:
    image = Image.open(base).convert("RGBA")
    if overlay is not None and overlay.is_file():
        overlay_image = Image.open(overlay).convert("RGBA")
        if overlay_image.size == image.size:
            image = Image.alpha_composite(image, overlay_image)
    ITEM_OUTPUT.mkdir(parents=True, exist_ok=True)
    image.save(ITEM_OUTPUT / output, optimize=True)


def import_material_form_icons() -> None:
    if ITEM_OUTPUT.is_dir():
        for stale in ITEM_OUTPUT.glob("*.png"):
            if stale.stem != "".join(
                char for char in stale.stem.lower() if char.isalnum()
            ):
                stale.unlink()
    for prefix in MOLD_OUTPUT_PREFIXES:
        base = ITEM_SOURCE / f"{prefix}.png"
        if not base.is_file():
            raise FileNotFoundError(f"Missing GT6 material icon: {base}")
        slug = "".join(char for char in prefix.lower() if char.isalnum())
        compose_item(
            base,
            ITEM_SOURCE / f"{prefix}_OVERLAY.png",
            f"{slug}.png",
        )
        model_path = ITEM_MODEL_OUTPUT / f"meta_{slug}.json"
        model = {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": f"gregtech:item/material/{slug}"
            },
        }
        model_path.write_text(
            json.dumps(model, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )


def faces(
    output_prefix: str,
    source_dir: str,
    overlay_dir: str | None,
    names: tuple[str, ...],
    tint: tuple[int, int, int] | None = None,
) -> None:
    for name in names:
        overlay = SOURCE / overlay_dir / f"{name}.png" if overlay_dir else None
        compose(
            SOURCE / source_dir / f"{name}.png",
            overlay,
            f"{output_prefix}_{name}.png",
            tint,
        )


def copy_asset(relative: str, output: str) -> None:
    shutil.copy2(SOURCE / relative, OUTPUT / output)


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    import_material_form_icons()

    compose(
        SOURCE / "materialicons/METALLIC/casingMachine.png",
        SOURCE / "materialicons/METALLIC/casingMachine_OVERLAY.png",
        "machine_casing.png",
        tint=(130, 130, 130),
    )
    (OUTPUT / "basic_machine.png").write_bytes(
        (OUTPUT / "machine_casing.png").read_bytes()
    )

    faces(
        "basic_crusher",
        "machines/basicmachines/crusher/colored",
        "machines/basicmachines/crusher/overlay",
        ("front", "back", "left", "right", "top", "bottom"),
    )
    faces(
        "large_crusher",
        "machines/basicmachines/largecrusher/colored",
        "machines/basicmachines/largecrusher/overlay",
        ("front", "back", "left", "right", "top", "bottom"),
        tint=(100, 100, 160),
    )
    faces(
        "large_crusher_formed",
        "machines/basicmachines/largecrusher/colored",
        "machines/basicmachines/largecrusher/overlay_active",
        ("front", "back", "left", "right", "top", "bottom"),
        tint=(100, 100, 160),
    )

    faces(
        "burning_box",
        "machines/generators/burning_solid/colored",
        "machines/generators/burning_solid/overlay",
        ("front", "back", "left", "right", "top", "bottom"),
        tint=(130, 130, 130),
    )
    for face in ("front", "back", "left", "right", "top", "bottom"):
        copy_asset(
            f"machines/generators/burning_solid/overlay_active/{face}.png",
            f"burning_box_active_{face}.png",
        )
    copy_asset(
        "machines/generators/burning_solid/overlay_active/front.png.mcmeta",
        "burning_box_active_front.png.mcmeta",
    )
    faces(
        "lv_generator",
        "machines/energystorages/battery_electric/colored",
        "machines/energystorages/battery_electric/overlay",
        ("front", "side"),
        tint=(130, 130, 130),
    )
    (OUTPUT / "lv_generator_top.png").write_bytes(
        (OUTPUT / "lv_generator_side.png").read_bytes()
    )

    faces(
        "electric_motor",
        "machines/motors/rotation_electric/colored",
        "machines/motors/rotation_electric/overlay",
        ("front", "back", "side"),
        tint=(130, 130, 130),
    )
    faces(
        "electric_engine",
        "machines/engines/kinetic_electric/colored",
        "machines/engines/kinetic_electric/overlay",
        ("front", "back", "side"),
        tint=(130, 130, 130),
    )
    faces(
        "rotation_engine",
        "machines/engines/kinetic_rotation/colored",
        "machines/engines/kinetic_rotation/overlay",
        ("front", "back", "side"),
        tint=(130, 130, 130),
    )
    compose(
        SOURCE / "machines/tools/crank/colored/front.png",
        SOURCE / "machines/tools/crank/overlay/front.png",
        "manual_crank_front.png",
    )
    (OUTPUT / "manual_crank_side.png").write_bytes(
        (OUTPUT / "machine_casing.png").read_bytes()
    )

    faces(
        "temperature_sensor",
        "machines/redstone/sensors/thermometer/colored",
        "machines/redstone/sensors/thermometer/overlay",
        ("front", "back", "side"),
        tint=(192, 192, 192),
    )

    faces(
        "large_crucible",
        "machines/multiblockmains/crucible/colored_front",
        "machines/multiblockmains/crucible/overlay_front",
        ("top", "side", "bottom"),
        tint=(130, 130, 130),
    )
    compose(
        SOURCE / "machines/multiblockmains/largeboiler/colored_front/side.png",
        SOURCE / "machines/multiblockmains/largeboiler/overlay_front/side.png",
        "large_boiler_front.png",
    )
    compose(
        SOURCE / "machines/multiblockmains/largeboiler/colored/side.png",
        SOURCE / "machines/multiblockmains/largeboiler/overlay/side.png",
        "large_boiler_back.png",
    )
    (OUTPUT / "large_boiler_side.png").write_bytes(
        (OUTPUT / "large_boiler_back.png").read_bytes()
    )
    compose(
        SOURCE / "machines/multiblockmains/largeboiler/colored_front/top.png",
        SOURCE / "machines/multiblockmains/largeboiler/overlay_front/top.png",
        "large_boiler_top.png",
    )
    compose(
        SOURCE / "machines/multiblockmains/largeboiler/colored_front/bottom.png",
        SOURCE / "machines/multiblockmains/largeboiler/overlay_front/bottom.png",
        "large_boiler_bottom.png",
    )
    compose(
        SOURCE / "machines/multiblockparts/metalwalldense/0/colored/side.png",
        SOURCE / "machines/multiblockparts/metalwalldense/0/overlay/side.png",
        "large_boiler_wall.png",
    )
    compose(
        SOURCE / "machines/multiblockparts/heatacceptor/0/colored/side.png",
        SOURCE / "machines/multiblockparts/heatacceptor/0/overlay/side.png",
        "heat_transmitter.png",
    )
    faces(
        "crucible_wall",
        "machines/multiblockparts/metalwall/0/colored",
        "machines/multiblockparts/metalwall/0/overlay",
        ("top", "side", "bottom"),
        tint=(130, 130, 130),
    )
    faces(
        "tungstensteel_wall",
        "machines/multiblockparts/metalwall/0/colored",
        "machines/multiblockparts/metalwall/0/overlay",
        ("top", "side", "bottom"),
        tint=(100, 100, 160),
    )
    compose(
        SOURCE / "machines/multiblockparts/metalwall/1/colored/side.png",
        SOURCE / "machines/multiblockparts/metalwall/1/overlay/side.png",
        "machine_port.png",
        tint=(130, 130, 130),
    )
    compose(
        SOURCE / "machines/multiblockparts/crusherwheels/1/colored/top.png",
        SOURCE / "machines/multiblockparts/crusherwheels/1/overlay/top.png",
        "crusher_wheels.png",
        tint=(100, 100, 160),
    )

    compose(
        SOURCE / "materialicons/METALLIC/blockSolid.png",
        None,
        "small_crucible_wall.png",
    )
    (OUTPUT / "small_crucible_bottom.png").write_bytes(
        (OUTPUT / "small_crucible_wall.png").read_bytes()
    )
    copy_asset("materialicons/METALLIC/molten.png", "crucible_content.png")
    copy_asset("materialicons/METALLIC/molten.png.mcmeta", "crucible_content.png.mcmeta")
    (OUTPUT / "mold_wall.png").write_bytes(
        (OUTPUT / "small_crucible_wall.png").read_bytes()
    )
    (OUTPUT / "mold_inner.png").write_bytes(
        (OUTPUT / "small_crucible_wall.png").read_bytes()
    )
    (OUTPUT / "faucet.png").write_bytes(
        (OUTPUT / "small_crucible_wall.png").read_bytes()
    )
    shutil.copy2(
        LEGACY / "src/main/resources/assets/gregtech/textures/items/materialicons/METALLIC/toolHeadChisel.png",
        PORT / "src/main/resources/assets/gregtech/textures/item/chisel.png",
    )

    print(f"Imported GT6 textures from {SOURCE} into {OUTPUT}")


if __name__ == "__main__":
    main()
