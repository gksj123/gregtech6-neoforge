#!/usr/bin/env python3
"""Import and compose the CC0 GT6 block textures used by the current port."""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageChops


PORT = Path(__file__).resolve().parents[1]
LEGACY = Path(
    os.environ.get("GT6_LEGACY_HOME", PORT.parent / "gregtech6-legacy")
).expanduser().resolve()
SOURCE = LEGACY / "src/main/resources/assets/gregtech/textures/blocks"
OUTPUT = PORT / "src/main/resources/assets/gregtech/textures/block"


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


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)

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

    print(f"Imported GT6 textures from {SOURCE} into {OUTPUT}")


if __name__ == "__main__":
    main()
