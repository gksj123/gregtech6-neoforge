#!/usr/bin/env python3
"""Export GT6 mold/faucet material variants and the 5x5 mold shape table."""

from __future__ import annotations

import json
import os
import re
import subprocess
import ast
from pathlib import Path

from inventory_gt6 import (
    extract_machine_registrations,
    matching_delimiter,
    split_top_level_arguments,
)


PORT = Path(__file__).resolve().parents[1]
LEGACY = Path(
    os.environ.get("GT6_LEGACY_HOME", PORT.parent / "gregtech6-legacy")
).expanduser().resolve()
SOURCE = LEGACY / "src/main/java"
LOADER = SOURCE / "gregtech/loaders/b/Loader_MultiTileEntities.java"
MOLD_SOURCE = SOURCE / "gregtech/tileentity/tools/MultiTileEntityMold.java"
INVENTORY = PORT / "migration/materials.json"
PREFIXES = PORT / "migration/prefixes.json"
OUTPUT = PORT / "migration/molds.json"
PORT_DATA = PORT / "src/main/resources/data/gregtech/gt6/molds.json"
RECIPE_DATA = PORT / "src/main/resources/data/gregtech/recipe"

CERAMIC_SHAPE = re.compile(
    r"IL\.Ceramic_([A-Za-z0-9_]+)_Mold_Raw"
    r".*?\"gt\.mold\"\s*,\s*(0b[01_]+)",
    re.DOTALL,
)

SHAPE_PREFIX = {
    "Ingot": "ingot",
    "Billet": "billet",
    "Chunk": "chunkGt",
    "Plate": "plate",
    "Tiny_Plate": "plateTiny",
    "Bolt": "bolt",
    "Rod": "stick",
    "Long_Rod": "stickLong",
    "Item_Casing": "casingSmall",
    "Ring": "ring",
    "Gear": "gearGt",
    "Small_Gear": "gearGtSmall",
    "Sword": "toolHeadRawSword",
    "Pickaxe": "toolHeadRawPickaxe",
    "Spade": "toolHeadRawSpade",
    "Shovel": "toolHeadRawShovel",
    "Universal_Spade": "toolHeadRawUniversalSpade",
    "Axe": "toolHeadRawAxe",
    "Double_Axe": "toolHeadRawAxeDouble",
    "Saw": "toolHeadRawSaw",
    "Hammer": "toolHeadHammer",
    "File": "toolHeadFile",
    "Screwdriver": "toolHeadScrewdriver",
    "Chisel": "toolHeadRawChisel",
    "Arrow": "toolHeadRawArrow",
    "Hoe": "toolHeadRawHoe",
    "Sense": "toolHeadRawSense",
    "Plow": "toolHeadRawPlow",
    "Builderwand": "toolHeadBuilderwand",
    "Nugget": "nugget",
}


def git_value(*args: str) -> str:
    try:
        return subprocess.check_output(
            ["git", *args],
            cwd=LEGACY,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def transform(mask: int, variant: int, mirror: bool) -> int:
    """Apply the source's bit layout (row * 5 + column).

    GT6 1.7.10: MultiTileEntityMold.java:375-405 rotates source (x,z) to
    (z, 4-x). The original static table also expands the mirrored variants.
    """
    result = 0
    for z in range(5):
        for x in range(5):
            if variant == 0:
                source_x, source_z = x, z
            elif variant == 1:
                source_x, source_z = 4 - z, x
            elif variant == 2:
                source_x, source_z = 4 - x, 4 - z
            else:
                source_x, source_z = z, 4 - x
            if mirror:
                source_x = 4 - source_x
            if mask & (1 << (source_x * 5 + source_z)):
                result |= 1 << (x * 5 + z)
    return result


def evaluate_integer(expression: str, variables: dict[str, int]) -> int:
    tree = ast.parse(expression.strip(), mode="eval")

    def evaluate(node: ast.AST) -> int:
        if isinstance(node, ast.Expression):
            return evaluate(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, int):
            return node.value
        if isinstance(node, ast.Name):
            return variables[node.id]
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
            value = evaluate(node.operand)
            return value if isinstance(node.op, ast.UAdd) else -value
        if isinstance(node, ast.BinOp):
            left = evaluate(node.left)
            right = evaluate(node.right)
            if isinstance(node.op, ast.Add):
                return left + right
            if isinstance(node.op, ast.Sub):
                return left - right
            if isinstance(node.op, ast.Mult):
                return left * right
            if isinstance(node.op, (ast.Div, ast.FloorDiv)):
                return left // right
        raise ValueError(f"Unsupported mold expression: {expression}")

    return evaluate(tree)


def evaluate_mask(expression: str, variables: dict[str, int]) -> int:
    compact = re.sub(r"\s+", "", expression)
    binary = re.fullmatch(r"0b[01_]+", compact)
    if binary:
        return int(compact.replace("_", ""), 2)
    result = 0
    cursor = 0
    for match in re.finditer(r"B\[", compact):
        if match.start() < cursor:
            continue
        open_bracket = match.end() - 1
        close_bracket = matching_delimiter(compact, open_bracket, "[", "]")
        if close_bracket < 0:
            raise ValueError(f"Unclosed B[] in {expression}")
        index = evaluate_integer(compact[open_bracket + 1 : close_bracket], variables)
        result |= 1 << index
        cursor = close_bracket + 1
    return result


def parse_initial_mold_table(text: str) -> tuple[dict[int, str], dict[int, int]]:
    start = text.find("Map<Integer, OreDictPrefix> TEMP_MOLD_RECIPES")
    end = text.find(
        "for (Entry<Integer, OreDictPrefix> tEntry : TEMP_MOLD_RECIPES.entrySet())",
        start,
    )
    if start < 0 or end < 0:
        raise AssertionError("Unable to locate GT6 initial mold recipe table")
    source = re.sub(r"//[^\n]*", "", text[start:end])
    masks: dict[int, str] = {}
    lines: dict[int, int] = {}
    base_line = text.count("\n", 0, start) + 1

    def parse_region(region_start: int, region_end: int, variables: dict[str, int]) -> None:
        position = region_start
        while position < region_end:
            while position < region_end and source[position].isspace():
                position += 1
            if position >= region_end:
                return
            if source.startswith("for", position):
                open_paren = source.find("(", position)
                close_paren = matching_delimiter(source, open_paren, "(", ")")
                header = source[open_paren + 1 : close_paren]
                header_match = re.fullmatch(
                    r"\s*int\s+([A-Za-z_]\w*)\s*=\s*(-?\d+)\s*;\s*"
                    r"\1\s*<\s*(-?\d+)\s*;\s*\1\+\+\s*",
                    header,
                )
                body_open = source.find("{", close_paren)
                body_close = matching_delimiter(source, body_open, "{", "}")
                if header_match is None or body_close < 0:
                    raise AssertionError(f"Unsupported source mold loop: {header}")
                variable, lower, upper = header_match.groups()
                for value in range(int(lower), int(upper)):
                    nested = dict(variables)
                    nested[variable] = value
                    parse_region(body_open + 1, body_close, nested)
                position = body_close + 1
                continue
            call = "TEMP_MOLD_RECIPES.put("
            if source.startswith(call, position):
                open_paren = position + len(call) - 1
                close_paren = matching_delimiter(source, open_paren, "(", ")")
                arguments = split_top_level_arguments(source[open_paren + 1 : close_paren])
                if len(arguments) == 2:
                    prefix_match = re.search(r"OP\.([A-Za-z0-9_]+)", arguments[1])
                    if prefix_match is not None:
                        mask = evaluate_mask(arguments[0], variables)
                        masks[mask & 0x1FFFFFF] = prefix_match.group(1)
                        lines[mask & 0x1FFFFFF] = base_line + source.count("\n", 0, position)
                position = close_paren + 1
                continue
            semicolon = source.find(";", position)
            loop = source.find("for", position + 1)
            if loop >= 0 and (semicolon < 0 or loop < semicolon):
                position = loop
            else:
                position = semicolon + 1 if semicolon >= 0 else region_end

    parse_region(0, len(source), {})
    return masks, lines


def extract_shapes() -> tuple[list[dict[str, object]], list[dict[str, object]]]:
    text = LOADER.read_text(encoding="utf-8")
    prefixes = {
        row["id"]: row
        for row in json.loads(PREFIXES.read_text(encoding="utf-8"))
    }
    canonical: list[dict[str, object]] = []
    unresolved: list[dict[str, object]] = []
    source_masks, source_lines = parse_initial_mold_table(
        MOLD_SOURCE.read_text(encoding="utf-8")
    )
    transformed_prefixes: dict[int, str] = {}
    for source_mask, source_prefix in source_masks.items():
        for variant in range(4):
            for mirrored in (False, True):
                transformed_prefixes[transform(source_mask, variant, mirrored)] = source_prefix
    for match in CERAMIC_SHAPE.finditer(text):
        shape = match.group(1)
        prefix = SHAPE_PREFIX.get(shape)
        mask = int(match.group(2).replace("_", ""), 2)
        line = text.count("\n", 0, match.start()) + 1
        if prefix is None or prefix not in prefixes or int(prefixes[prefix]["amount"]) <= 0:
            unresolved.append(
                {
                    "name": shape.lower(),
                    "mask": mask,
                    "prefix": prefix or "",
                    "source_line": line,
                    "reason": "shape prefix is unmapped or has no source item amount",
                }
            )
            continue
        if "nugget" != prefix and transformed_prefixes.get(mask) != prefix:
            unresolved.append(
                {
                    "name": shape.lower(),
                    "mask": mask,
                    "prefix": prefix,
                    "source_line": line,
                    "reason": "ceramic mask does not resolve to the expected GT6 prefix",
                }
            )
            continue
        canonical.append(
            {
                "name": shape.lower(),
                "mask": mask,
                "prefix": prefix,
                "source_line": line,
            }
        )

    shape_by_mask = {int(row["mask"]): str(row["name"]) for row in canonical}
    variants: dict[int, dict[str, object]] = {}
    for source_mask, prefix in source_masks.items():
        source_shape = shape_by_mask.get(source_mask, prefix)
        source_line = source_lines.get(source_mask, 0)
        for variant in range(4):
            for mirrored in (False, True):
                mask = transform(source_mask, variant, mirrored)
                variants[mask] = {
                    "mask": mask,
                    "prefix": prefix,
                    "source_shape": source_shape,
                    "source_line": source_line,
                }
    return canonical, sorted(variants.values(), key=lambda row: int(row["mask"]))


def extract_variants(
    rows: list[dict[str, object]],
    category: str,
    text: str,
    registry_min: int,
    registry_max: int,
) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for row in rows:
        if row.get("category") != category or not row.get("material_slug"):
            continue
        expression = str(row.get("registry_id_expression", "")).strip()
        if not expression.isdigit() or not registry_min <= int(expression) <= registry_max:
            continue
        source_line = int(row["line"])
        line_text = text.splitlines()[source_line - 1]
        recipe = casting_recipe_spec(category, line_text, str(row["material_slug"]))
        result.append(
            {
                "display_name": row["display_name"],
                "material": row["material_slug"],
                "source_line": source_line,
                "acid_proof": "NBT_ACIDPROOF, T" in line_text,
                **recipe,
            }
        )
    return result


def casting_recipe_spec(
    category: str,
    line_text: str,
    material: str,
) -> dict[str, object]:
    if category == "Crucibles Faucets":
        return {
            "ingredient_item": "gregtech:meta_plate",
            "ingredient_material": material,
            "ingredient_prefix": "plate",
            "ingredient_count": 3,
        }
    if "'B', Blocks.stone" in line_text:
        return {
            "ingredient_item": "minecraft:stone",
            "ingredient_material": "",
            "ingredient_prefix": "",
            "ingredient_count": 5,
        }
    if "OP.plate.dat(MT.Graphene)" in line_text:
        return {
            "ingredient_item": "gregtech:meta_plate",
            "ingredient_material": "graphene",
            "ingredient_prefix": "plate",
            "ingredient_count": 5,
        }
    if "OP.gem.dat(ANY.Quartz)" in line_text:
        return {
            "ingredient_item": "gregtech:meta_gem",
            "ingredient_material": "milkyquartz",
            "ingredient_prefix": "gem",
            "ingredient_count": 5,
        }
    if "OP.stone.dat(aMat)" in line_text:
        return {
            "ingredient_item": "gregtech:meta_stone",
            "ingredient_material": material,
            "ingredient_prefix": "stone",
            "ingredient_count": 5,
        }
    if "OP.plate.dat(aMat)" in line_text:
        return {
            "ingredient_item": "gregtech:meta_plate",
            "ingredient_material": material,
            "ingredient_prefix": "plate",
            "ingredient_count": 5,
        }
    return {
        "ingredient_item": "",
        "ingredient_material": "",
        "ingredient_prefix": "",
        "ingredient_count": 0,
    }


def validate(
    canonical: list[dict[str, object]],
    molds: list[dict[str, object]],
    faucets: list[dict[str, object]],
) -> None:
    names = {row["name"] for row in canonical}
    required = {"ingot", "plate", "tiny_plate", "bolt", "rod", "gear", "small_gear", "nugget"}
    if not required.issubset(names):
        raise AssertionError(f"Mold shape extraction missing {sorted(required - names)}")
    if len(canonical) < 30:
        raise AssertionError(f"Too few source mold shapes: {len(canonical)}")
    if len(molds) < 30:
        raise AssertionError(f"Too few mold material variants: {len(molds)}")
    if not any(row["material"] == "steel" for row in molds):
        raise AssertionError("Steel mold variant was not extracted")
    if len(faucets) < 30:
        raise AssertionError(f"Too few faucet material variants: {len(faucets)}")
    recipe_rows = sum(int(row["ingredient_count"]) > 0 for row in molds + faucets)
    if recipe_rows != 77:
        raise AssertionError(f"Expected 77 casting recipes, got {recipe_rows}")


def write_casting_recipes(
    molds: list[dict[str, object]],
    faucets: list[dict[str, object]],
) -> int:
    RECIPE_DATA.mkdir(parents=True, exist_ok=True)
    written = 0
    for kind, rows, result_item, pattern in (
        ("mold", molds, "gregtech:mold", ["M M", "MMM"]),
        ("faucet", faucets, "gregtech:faucet", ["F F", " F "]),
    ):
        for row in rows:
            if int(row["ingredient_count"]) <= 0:
                continue
            material = str(row["ingredient_material"])
            ingredient: dict[str, object]
            if material:
                ingredient = {
                    "type": "neoforge:components",
                    "items": row["ingredient_item"],
                    "components": {
                        "gregtech:material_part": {
                            "material": material,
                            "prefix": row["ingredient_prefix"],
                        }
                    },
                }
            else:
                ingredient = {"item": row["ingredient_item"]}
            key = "M" if kind == "mold" else "F"
            recipe = {
                "type": "minecraft:crafting_shaped",
                "category": "misc",
                "key": {key: ingredient},
                "pattern": pattern,
                "result": {
                    "count": 1,
                    "id": result_item,
                    "components": {
                        "gregtech:casting_part": {
                            "wall_material": row["material"],
                            "source_line": row["source_line"],
                            "shape_mask": 0,
                            "acid_proof": row["acid_proof"],
                        }
                    },
                },
            }
            target = RECIPE_DATA / f"{kind}_{row['material']}_{row['source_line']}.json"
            target.write_text(
                json.dumps(recipe, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
            written += 1
    return written


def write_ceramic_mold_recipes(
    canonical: list[dict[str, object]],
) -> int:
    RECIPE_DATA.mkdir(parents=True, exist_ok=True)
    written = 0
    for row in canonical:
        mask = int(row["mask"])
        source_line = int(row["source_line"])
        part = {
            "wall_material": "ceramic",
            "source_line": source_line,
            "shape_mask": mask,
            "acid_proof": False,
        }
        recipe = {
            "type": "minecraft:smelting",
            "category": "misc",
            "cookingtime": 200,
            "experience": 0.0,
            "ingredient": {
                "type": "neoforge:components",
                "items": "gregtech:ceramic_mold_raw",
                "components": {"gregtech:casting_part": part},
            },
            "result": {
                "count": 1,
                "id": "gregtech:mold",
                "components": {"gregtech:casting_part": part},
            },
        }
        target = RECIPE_DATA / f"ceramic_mold_{row['name']}.json"
        target.write_text(
            json.dumps(recipe, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        written += 1
    return written


def main() -> None:
    text = LOADER.read_text(encoding="utf-8")
    registrations, _energy = extract_machine_registrations()
    molds = extract_variants(
        registrations,
        "Molds",
        text,
        1050,
        1099,
    )
    faucets = extract_variants(registrations, "Crucibles Faucets", text, 1700, 1799)
    canonical, masks = extract_shapes()
    validate(canonical, molds, faucets)
    recipes_written = write_casting_recipes(molds, faucets)
    ceramic_recipes_written = write_ceramic_mold_recipes(canonical)
    payload = {
        "format": 1,
        "source_repository": "gregtech6-legacy",
        "source_commit": git_value("rev-parse", "HEAD"),
        "source_files": [
            "java/gregtech/loaders/b/Loader_MultiTileEntities.java",
            "java/gregtech/tileentity/tools/MultiTileEntityMold.java",
        ],
        "mold_variants": molds,
        "faucet_variants": faucets,
        "canonical_shapes": canonical,
        "mask_lookup": masks,
        "default_unknown_prefix": "nugget",
    }
    encoded = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    PORT_DATA.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(encoded, encoding="utf-8")
    PORT_DATA.write_text(encoded, encoding="utf-8")
    print(
        json.dumps(
            {
                "mold_variants": len(molds),
                "faucet_variants": len(faucets),
                "canonical_shapes": len(canonical),
                "mask_lookup": len(masks),
                "generated_recipes": recipes_written,
                "generated_ceramic_recipes": ceramic_recipes_written,
                "output": str(OUTPUT),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
