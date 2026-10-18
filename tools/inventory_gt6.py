#!/usr/bin/env python3
"""Create a reproducible inventory of the legacy GT6 content that must be ported."""

from __future__ import annotations

import ast
import json
import re
import subprocess
from collections import Counter
from pathlib import Path


PORT = Path(__file__).resolve().parents[1]
REPO = PORT.parent
SOURCE = REPO / "src/main/java"
RESOURCES = REPO / "src/main/resources"
OUTPUT = PORT / "migration"
PORT_DATA = PORT / "src/main/resources/data/gregtech/gt6"


MATERIAL_CALL = re.compile(
    r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*(-?\d{1,5})\s*,\s*\"([^\"]+)\""
    r"(?:\s*,\s*\"([^\"]+)\")?"
)
PREFIX_CALL = re.compile(r"\b(\w+)\s*=\s*create\(\"([^\"]+)\"\s*,\s*\"([^\"]+)\"")
CLASS_REFERENCE = re.compile(r"([\w.$]+)\.class")
ENERGY_ACCEPTED = re.compile(r"NBT_ENERGY_ACCEPTED\s*,\s*TD\.Energy\.(\w+)")
ENERGY_EMITTED = re.compile(r"NBT_ENERGY_EMITTED\s*,\s*TD\.Energy\.(\w+)")
RECIPE_MAP = re.compile(r"NBT_RECIPEMAP\s*,\s*RM\.(\w+)")
FUEL_MAP = re.compile(r"NBT_FUELMAP\s*,\s*FM\.(\w+)")
MATERIAL_ASSIGNMENT = re.compile(r"\baMat\s*=\s*([^;]+)")
CLASS_ASSIGNMENT = re.compile(r"\baClass\s*=\s*([\w.$]+)\.class")
NUMERIC_PROPERTY = {
    "input": r"NBT_INPUT\s*,\s*(\d+)",
    "input_min": r"NBT_INPUT_MIN\s*,\s*(\d+)",
    "input_max": r"NBT_INPUT_MAX\s*,\s*(\d+)",
    "efficiency": r"NBT_EFFICIENCY\s*,\s*(\d+)",
    "parallel": r"NBT_PARALLEL\s*,\s*(\d+)",
    "output": r"NBT_OUTPUT\s*,\s*(\d+)",
}
BOOLEAN_PROPERTY = {
    "parallel_duration": r"NBT_PARALLEL_DURATION\s*,\s*([TF])",
    "cheap_overclocking": r"NBT_CHEAP_OVERCLOCKING\s*,\s*([TF])",
    "no_constant_power": r"NBT_NO_CONSTANT_POWER\s*,\s*([TF])",
}
RECIPE_CALL = re.compile(
    r"\.(addRecipe2?|addFakeRecipe|addHiddenRecipe|addAlloy|setTarget_?|addRecipeMap|addRecipeMap)\s*\("
)
HANDLER_CALL = re.compile(
    r"\bRM\.([A-Za-z0-9_]+)\s*\.add\s*\(\s*new\s+(RecipeMapHandler[A-Za-z0-9_]+)\s*\("
)
SMASH_CALL = re.compile(r"\bRM\.smash\s*\(")
ITEM_STACK_CALL = re.compile(
    r"ST\.make\(\s*(Blocks|Items)\.([A-Za-z0-9_]+)\s*,\s*([^,]+)\s*,\s*([^)]+)\)"
)
U_CONSTANTS = {
    f"U{index}" if index > 1 else "U": 648648000 if index == 1 else 648648000 // index
    for index in range(1, 1441)
}
U_CONSTANTS["U"] = 648648000


def git_value(*args: str) -> str:
    try:
        return subprocess.check_output(["git", *args], cwd=REPO, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def package_counts() -> dict[str, int]:
    counts: Counter[str] = Counter()
    for path in SOURCE.rglob("*.java"):
        relative = path.relative_to(SOURCE)
        parts = relative.parts
        key = "/".join(parts[:3]) if len(parts) >= 3 else "/".join(parts[:-1])
        counts[key] += 1
    return dict(sorted(counts.items()))


def extension_counts(root: Path) -> dict[str, int]:
    counts: Counter[str] = Counter()
    for path in root.rglob("*"):
        if path.is_file():
            counts[path.suffix.lower() or "<none>"] += 1
    return dict(sorted(counts.items()))


def extract_materials() -> list[dict[str, object]]:
    text = (SOURCE / "gregapi/data/MT.java").read_text(encoding="utf-8")
    materials: dict[tuple[int, str], dict[str, object]] = {}
    for match in MATERIAL_CALL.finditer(text):
        constructor, material_id, name, symbol = match.groups()
        color = extract_material_color(text, match.start())
        key = (int(material_id), name)
        existing = materials.get(key)
        materials[key] = {
            "id": int(material_id),
            "name": name,
            "symbol": symbol or "",
            "constructor": constructor,
            "color": color if color is not None else (existing or {}).get("color", ""),
        }
    return sorted(materials.values(), key=lambda item: (item["id"], item["name"]))


def extract_material_color(text: str, match_start: int) -> str:
    open_paren = text.find("(", match_start)
    end = matching_delimiter(text, open_paren, "(", ")")
    if end < 0:
        return ""
    call = text[open_paren : end + 1]
    direct = re.search(
        r"setRGBa\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})",
        call,
    )
    if direct:
        red, green, blue = (int(direct.group(index)) for index in range(1, 4))
    else:
        palette = re.search(
            r"\bSET_[A-Z0-9_]+\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})",
            call,
        )
        if not palette:
            return ""
        red, green, blue = (int(palette.group(index)) for index in range(1, 4))
    if not all(0 <= value <= 255 for value in (red, green, blue)):
        return ""
    return f"#{red:02X}{green:02X}{blue:02X}"


def extract_prefixes() -> list[dict[str, object]]:
    text = (SOURCE / "gregapi/data/OP.java").read_text(encoding="utf-8")
    prefixes: dict[str, dict[str, str]] = {}
    for match in PREFIX_CALL.finditer(text):
        field, prefix_id, category = match.groups()
        amount_expression = ""
        line_end = text.find("\n", match.start())
        declaration = text[match.start() : line_end if line_end >= 0 else len(text)]
        amount_match = re.search(r"\.setMaterialStats\s*\(([^)]*)\)", declaration)
        amount = 0
        if amount_match:
            amount_expression = amount_match.group(1).strip()
            amount = evaluate_java_integer(amount_expression, default=0)
        prefixes[prefix_id] = {
            "field": field,
            "id": prefix_id,
            "category": category,
            "amount": amount,
            "amount_expression": amount_expression,
        }
    return sorted(prefixes.values(), key=lambda item: item["id"])


def evaluate_java_integer(expression: str, default: int | None = None) -> int | None:
    expression = expression.strip().replace("L", "")
    if re.fullmatch(r"-?\d+", expression):
        return int(expression)
    try:
        tree = ast.parse(expression, mode="eval")
    except SyntaxError:
        return default

    def evaluate(node: ast.AST) -> int:
        if isinstance(node, ast.Expression):
            return evaluate(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, int):
            return node.value
        if isinstance(node, ast.Name) and node.id in U_CONSTANTS:
            return U_CONSTANTS[node.id]
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
                return int(left / right)
        raise ValueError(f"Unsupported integer expression: {expression}")

    try:
        return evaluate(tree)
    except (TypeError, ValueError, ZeroDivisionError):
        return default


def extract_machine_registrations() -> tuple[list[dict[str, object]], dict[str, int]]:
    path = SOURCE / "gregtech/loaders/b/Loader_MultiTileEntities.java"
    text = path.read_text(encoding="utf-8")
    rows: list[dict[str, object]] = []
    energy_counts: Counter[str] = Counter()
    search_from = 0
    while True:
        start = text.find("aRegistry.add(", search_from)
        if start < 0:
            break
        open_paren = text.find("(", start)
        end = matching_delimiter(text, open_paren, "(", ")")
        if end < 0:
            break
        raw = text[start : end + 1]
        search_from = end + 1
        line_number = text.count("\n", 0, start) + 1
        arguments = split_top_level_arguments(text[open_paren + 1 : end])
        name_expression = arguments[0].strip() if arguments else ""
        category_expression = arguments[1].strip() if len(arguments) > 1 else ""
        registry_id_expression = arguments[2].strip() if len(arguments) > 2 else ""
        class_match = CLASS_REFERENCE.search(raw)
        if not class_match:
            previous_window = text[max(0, start - 8000) : start]
            previous_classes = CLASS_ASSIGNMENT.findall(previous_window)
            if previous_classes:
                class_name = previous_classes[-1]
            else:
                class_name = ""
        else:
            class_name = class_match.group(1)
        accepted = ENERGY_ACCEPTED.search(raw)
        emitted = ENERGY_EMITTED.search(raw)
        recipe_map = RECIPE_MAP.search(raw)
        fuel_map = FUEL_MAP.search(raw)
        material_assignment = MATERIAL_ASSIGNMENT.search(raw)
        if material_assignment:
            material_expression = material_assignment.group(1).strip()
        else:
            previous_window = text[max(0, start - 8000) : start]
            previous_materials = list(MATERIAL_ASSIGNMENT.finditer(previous_window))
            material_expression = previous_materials[-1].group(1).strip() if previous_materials else ""
        material_name = resolve_material_expression(material_expression)
        display_name = resolve_machine_name(name_expression, material_expression)
        numeric = {
            key: int(match.group(1)) if (match := re.search(pattern, raw)) else 0
            for key, pattern in NUMERIC_PROPERTY.items()
        }
        boolean = {
            key: bool(match and match.group(1) == "T")
            for key, pattern in BOOLEAN_PROPERTY.items()
            if (match := re.search(pattern, raw))
        }
        if accepted:
            energy_counts[f"accept:{accepted.group(1)}"] += 1
        if emitted:
            energy_counts[f"emit:{emitted.group(1)}"] += 1
        rows.append(
            {
                "line": line_number,
                "name_expression": name_expression,
                "category": first_string_literal(category_expression),
                "registry_id_expression": registry_id_expression,
                "class": class_name,
                "energy_accepted": accepted.group(1) if accepted else "",
                "energy_emitted": emitted.group(1) if emitted else "",
                "recipe_map": recipe_map.group(1) if recipe_map else "",
                "fuel_map": fuel_map.group(1) if fuel_map else "",
                "material_expression": material_expression,
                "material_name": material_name,
                "material_slug": material_slug(material_name),
                "display_name": display_name,
                **numeric,
                **boolean,
            }
        )
    return rows, dict(sorted(energy_counts.items()))


def matching_delimiter(text: str, start: int, opening: str, closing: str) -> int:
    if start < 0 or text[start] != opening:
        return -1
    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "\"":
                in_string = False
            continue
        if char == "\"":
            in_string = True
        elif char == opening:
            depth += 1
        elif char == closing:
            depth -= 1
            if depth == 0:
                return index
    return -1


def split_top_level_arguments(text: str) -> list[str]:
    arguments: list[str] = []
    start = 0
    depth = 0
    in_string = False
    escaped = False
    for index, char in enumerate(text):
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "\"":
                in_string = False
            continue
        if char == "\"":
            in_string = True
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 0:
            arguments.append(text[start:index])
            start = index + 1
    arguments.append(text[start:])
    return arguments


def first_string_literal(expression: str) -> str:
    match = re.search(r"\"([^\"]*)\"", expression)
    return match.group(1) if match else ""


KINETIC_TIERS = [
    "Wood", "Bronze", "Steel", "Titanium", "Tungsten Steel", "Iridium",
    "Osmium", "Osmium", "Osmium", "Osmium", "Osmium", "Osmium", "Osmium",
    "Osmium", "Osmium", "Osmium",
]
ELECTRIC_TIERS = [
    "Tin Alloy", "Galvanized Steel", "Aluminium", "Stainless Steel", "Chromium",
    "Titanium", "Iridium", "Osmium", "Trinium", "Trinaquadalloy", "Neutronium",
    "Neutronium", "Neutronium", "Neutronium", "Neutronium", "Neutronium",
]
FLUX_TIERS = [
    "Tin", "Lead", "Invar", "Electrum", "Enderium Base", "Enderium",
    "Tungsten Carbide", "Tungsten Carbide", "Tungsten Carbide", "Tungsten Carbide",
    "Tungsten Carbide", "Tungsten Carbide", "Tungsten Carbide", "Tungsten Carbide",
    "Tungsten Carbide", "Tungsten Carbide",
]
HEAT_TIERS = [
    "Stone", "Steel", "Invar", "Titanium", "Tungsten Carbide", "Tungsten",
    "Tungsten", "Tungsten", "Tungsten", "Tungsten", "Tungsten", "Tungsten",
    "Tungsten", "Tungsten", "Tungsten", "Tungsten",
]
VOLTAGE_NAMES = [
    "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV",
    "PUV1", "PUV2", "PUV3", "PUV4", "PUV5", "XV", "XV",
]


def resolve_machine_name(name_expression: str, material_expression: str) -> str:
    material = resolve_material_expression(material_expression)
    pieces: list[str] = []
    token_pattern = re.compile(
        r"\"([^\"]*)\"|aMat\.getLocal\(\)|aMat\.mNameLocal|VN\[(\d+)\]"
    )
    for match in token_pattern.finditer(name_expression):
        if match.group(1) is not None:
            pieces.append(match.group(1))
        elif match.group(2) is not None:
            index = int(match.group(2))
            pieces.append(VOLTAGE_NAMES[index] if 0 <= index < len(VOLTAGE_NAMES) else "")
        else:
            pieces.append(material)
    result = "".join(pieces).strip()
    return result or "Legacy GregTech Machine"


def resolve_material_expression(expression: str) -> str:
    expression = expression.strip()
    array_match = re.search(r"MT\.DATA\.(Kinetic_T|Electric_T|Flux_T|Heat_T)\[(\d+)]", expression)
    if array_match:
        values = {
            "Kinetic_T": KINETIC_TIERS,
            "Electric_T": ELECTRIC_TIERS,
            "Flux_T": FLUX_TIERS,
            "Heat_T": HEAT_TIERS,
        }[array_match.group(1)]
        index = int(array_match.group(2))
        return values[index] if 0 <= index < len(values) else "Unknown"
    simple = re.search(r"(?:MT|ANY)\.([A-Za-z0-9_.]+)", expression)
    if not simple:
        return ""
    path = simple.group(1)
    if path.startswith("DATA."):
        return ""
    name = path.split(".")[-1]
    aliases = {
        "Pb": "Lead",
        "Sn": "Tin",
        "Ti": "Titanium",
        "W": "Tungsten",
        "Fe": "Iron",
        "Cu": "Copper",
        "Au": "Gold",
        "Ag": "Silver",
        "Ni": "Nickel",
        "Zn": "Zinc",
        "Al": "Aluminium",
        "Cr": "Chromium",
        "Ir": "Iridium",
        "Os": "Osmium",
        "Bz": "Bronze",
        "C": "Carbon",
        "Mo": "Molybdenum",
        "Nb": "Niobium",
        "Ta": "Tantalum",
        "V": "Vanadium",
        "Ad": "Adamantium",
        "HSLA": "HSLA-Steel",
        "Ta4HfC5": "Tantalum Hafnium Carbide",
    }
    if name in aliases:
        return aliases[name]
    name = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", name)
    return name.replace("_", " ").strip()


def material_slug(name: str) -> str:
    aliases = {
        # ANY.Quartz in ANY.java steals the properties of MT.MilkyQuartz.
        "Quartz": "milkyquartz",
        # MT.Os stores its source material under the local name Osmium.
        "Osmium": "osmiumelemental",
    }
    if name in aliases:
        return aliases[name]
    return re.sub(r"[^a-z0-9]", "", name.lower())


def extract_recipe_index() -> tuple[list[dict[str, object]], dict[str, int]]:
    rows: list[dict[str, object]] = []
    counts: Counter[str] = Counter()
    paths = sorted(SOURCE.glob("gregtech/loaders/**/*.java"))
    for path in paths:
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            for match in RECIPE_CALL.finditer(line):
                method = match.group(1)
                counts[method] += 1
                rows.append(
                    {
                        "file": str(path.relative_to(SOURCE)),
                        "line": line_number,
                        "method": method,
                        "source": line.strip()[:500],
                    }
                )
    return rows, dict(sorted(counts.items()))


def extract_recipe_handlers(
    prefixes: list[dict[str, object]],
) -> list[dict[str, object]]:
    prefix_by_field = {str(prefix["field"]): str(prefix["id"]) for prefix in prefixes}
    rows: list[dict[str, object]] = []
    for path in sorted(SOURCE.glob("gregtech/loaders/**/*.java")):
        text = path.read_text(encoding="utf-8")
        for match in HANDLER_CALL.finditer(text):
            recipe_map, handler_type = match.groups()
            open_paren = text.find("(", match.end() - 1)
            close_paren = matching_delimiter(text, open_paren, "(", ")")
            if close_paren < 0:
                continue
            arguments = [
                argument.strip()
                for argument in split_top_level_arguments(text[open_paren + 1 : close_paren])
            ]
            parsed = parse_recipe_handler(handler_type, arguments, prefix_by_field)
            if parsed is None:
                continue
            rows.append(
                {
                    "map": recipe_map,
                    "handler_type": handler_type,
                    "source": str(path.relative_to(SOURCE)),
                    "line": text.count("\n", 0, match.start()) + 1,
                    **parsed,
                }
            )
    return rows


def parse_recipe_handler(
    handler_type: str,
    arguments: list[str],
    prefix_by_field: dict[str, str],
) -> dict[str, object] | None:
    one_input = {
        "input1": 0,
        "input1_count": 1,
        "fluid_input": 2,
        "eut": 3,
        "duration": 4,
        "multiplier": 5,
        "fluid_output": 6,
        "output1": 7,
        "output1_count": 8,
        "additional_input": 9,
        "additional_output": 10,
        "allow_all": 11,
        "pulverized_remains": 12,
        "flat_fluid": 13,
        "condition": 14,
    }
    two_input = {
        "input1": 0,
        "input1_count": 1,
        "input2": 2,
        "input2_count": 3,
        "fluid_input": 4,
        "eut": 5,
        "duration": 6,
        "multiplier": 7,
        "fluid_output": 8,
        "output1": 9,
        "output1_count": 10,
        "output2": 11,
        "output2_count": 12,
        "additional_input": 13,
        "additional_output": 14,
        "allow_all": 15,
        "pulverized_remains": 16,
        "flat_fluid": 17,
        "condition": 18,
    }
    indexes = one_input if len(arguments) == 15 else two_input if len(arguments) == 19 else None
    if indexes is None:
        return None

    def argument(key: str) -> str:
        index = indexes[key]
        return arguments[index] if index < len(arguments) else ""

    inputs: list[dict[str, object]] = []
    for prefix_key, count_key in (("input1", "input1_count"), ("input2", "input2_count")):
        if prefix_key not in indexes:
            continue
        prefix = prefix_by_field.get(argument(prefix_key))
        count = evaluate_java_integer(argument(count_key), default=0) or 0
        if prefix and count > 0:
            inputs.append({"prefix": prefix, "count": count})

    outputs: list[dict[str, object]] = []
    for prefix_key, count_key in (("output1", "output1_count"), ("output2", "output2_count")):
        if prefix_key not in indexes:
            continue
        prefix = prefix_by_field.get(argument(prefix_key))
        count = evaluate_java_integer(argument(count_key), default=0) or 0
        if prefix and count > 0:
            outputs.append({"prefix": prefix, "count": count})

    condition = argument("condition")
    fluids_absent = argument("fluid_input") in {"NF", "null"} and argument(
        "fluid_output"
    ) in {"NF", "null"}
    additions_absent = argument("additional_input") in {"NI", "null"} and argument(
        "additional_output"
    ) in {"NI", "null"}
    condition_supported = is_trivially_supported_condition(condition)
    same_material_supported = (
        handler_type in {"RecipeMapHandlerPrefix", "RecipeMapHandlerPrefixForging"}
        and bool(inputs)
        and bool(outputs)
        and fluids_absent
        and additions_absent
        and condition_supported
    )
    return {
        "inputs": inputs,
        "outputs": outputs,
        "eut": evaluate_java_integer(argument("eut"), default=0) or 0,
        "duration": evaluate_java_integer(argument("duration"), default=0) or 0,
        "multiplier": evaluate_java_integer(argument("multiplier"), default=0) or 0,
        "pulverized_remains": argument("pulverized_remains") == "T",
        "flat_fluid": argument("flat_fluid") == "T",
        "condition": condition,
        "supported": same_material_supported,
    }


def is_trivially_supported_condition(condition: str) -> bool:
    if not condition.strip():
        return True
    normalized = condition.strip()
    if normalized.startswith("new And(") and normalized.endswith(")"):
        normalized = normalized[len("new And(") : -1]
    supported_tokens = {
        "ANTIMATTER.NOT",
        "MT.Empty.NOT",
        "EXPLODES_IN_NONVANILLA_CRAFTING_GRID.NOT",
    }
    tokens = [token.strip() for token in normalized.split(",") if token.strip()]
    return bool(tokens) and all(token in supported_tokens for token in tokens)


def extract_smash_recipes() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    path = SOURCE / "gregtech/loaders/c/Loader_Recipes_Vanilla.java"
    text = path.read_text(encoding="utf-8")
    for match in SMASH_CALL.finditer(text):
        open_paren = text.find("(", match.start())
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        arguments = split_top_level_arguments(text[open_paren + 1 : close_paren])
        if len(arguments) not in {2, 3}:
            continue
        input_stack = parse_direct_item_stack(arguments[0])
        output_stack = parse_direct_item_stack(arguments[1])
        if input_stack is None or output_stack is None:
            continue
        multiplier = evaluate_java_integer(arguments[2], default=1) if len(arguments) == 3 else 1
        output_stack["count"] = int(output_stack["count"]) * max(1, multiplier or 1)
        rows.append(
            {
                "id": f"gt6_smash_{len(rows) + 1}",
                "map": "Crusher",
                "inputs": [input_stack],
                "outputs": [output_stack],
                "eut": 16,
                "duration": 32,
                "source": str(path.relative_to(SOURCE)),
                "line": text.count("\n", 0, match.start()) + 1,
            }
        )
    return rows


def parse_direct_item_stack(expression: str) -> dict[str, object] | None:
    match = ITEM_STACK_CALL.fullmatch(expression.strip())
    if not match:
        return None
    namespace, field, count_expression, metadata = match.groups()
    count = evaluate_java_integer(count_expression, default=0) or 0
    metadata_value = evaluate_java_integer(metadata, default=-1)
    if count <= 0 or metadata_value != 0:
        return None
    names = {
        ("Blocks", "brick_block"): "bricks",
        ("Blocks", "hardened_clay"): "terracotta",
        ("Blocks", "stonebrick"): "stone_bricks",
        ("Blocks", "stone_slab"): "stone_slab",
        ("Blocks", "double_stone_slab"): "smooth_stone_slab",
        ("Items", "brick"): "brick",
        ("Items", "netherbrick"): "nether_brick",
    }
    item_name = names.get((namespace, field), camel_to_snake(field))
    item_namespace = "minecraft"
    return {
        "item": f"{item_namespace}:{item_name}",
        "count": count,
    }


def camel_to_snake(value: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", value).lower()


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    PORT_DATA.mkdir(parents=True, exist_ok=True)
    materials = extract_materials()
    prefixes = extract_prefixes()
    registrations, energy_usage = extract_machine_registrations()
    recipe_rows, recipe_counts = extract_recipe_index()
    recipe_handlers = extract_recipe_handlers(prefixes)
    explicit_recipes = extract_smash_recipes()
    loader_files = sorted(str(path.relative_to(SOURCE)) for path in SOURCE.glob("gregtech/loaders/**/*.java"))

    inventory = {
        "source": {
            "repository": git_value("remote", "get-url", "origin"),
            "commit": git_value("rev-parse", "HEAD"),
            "commit_date": git_value("show", "-s", "--format=%cI", "HEAD"),
            "license": "LGPL-3.0; assets CC0 unless otherwise stated",
        },
        "source_of_truth": [
            "src/main/java",
            "src/main/resources",
        ],
        "secondary_references": [
            "https://gregtech.mechaenetia.com/",
            "https://ftb.fandom.com/wiki/GregTech_6",
            "https://www.mcmod.cn/class/455.html",
        ],
        "counts": {
            "java_files": sum(1 for _ in SOURCE.rglob("*.java")),
            "resource_files": sum(1 for _ in RESOURCES.rglob("*") if _.is_file()),
            "materials_extracted": len(materials),
            "prefixes_extracted": len(prefixes),
            "multitile_registrations": len(registrations),
            "recipe_invocations_indexed": len(recipe_rows),
            "recipe_handlers_extracted": len(recipe_handlers),
            "recipe_handlers_supported_same_material": sum(
                1 for handler in recipe_handlers if handler["supported"]
            ),
            "explicit_item_recipes_extracted": len(explicit_recipes),
            "loader_files": len(loader_files),
        },
        "extensions": {
            "java": extension_counts(SOURCE),
            "resources": extension_counts(RESOURCES),
        },
        "java_packages": package_counts(),
        "energy_usage": energy_usage,
        "recipe_invocations": recipe_counts,
        "loader_files": loader_files,
    }

    (OUTPUT / "content_inventory.json").write_text(
        json.dumps(inventory, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    materials_json = json.dumps(materials, ensure_ascii=False, indent=2) + "\n"
    (OUTPUT / "materials.json").write_text(materials_json, encoding="utf-8")
    (PORT_DATA / "materials.json").write_text(materials_json, encoding="utf-8")
    prefixes_json = json.dumps(prefixes, ensure_ascii=False, indent=2) + "\n"
    (OUTPUT / "prefixes.json").write_text(prefixes_json, encoding="utf-8")
    (PORT_DATA / "prefixes.json").write_text(prefixes_json, encoding="utf-8")
    handlers_json = json.dumps(recipe_handlers, ensure_ascii=False, indent=2) + "\n"
    (OUTPUT / "recipe_handlers.json").write_text(handlers_json, encoding="utf-8")
    (PORT_DATA / "recipe_handlers.json").write_text(handlers_json, encoding="utf-8")
    recipes_json = json.dumps(explicit_recipes, ensure_ascii=False, indent=2) + "\n"
    (OUTPUT / "recipes.json").write_text(recipes_json, encoding="utf-8")
    (PORT_DATA / "recipes.json").write_text(recipes_json, encoding="utf-8")
    with (OUTPUT / "multitile_registry.tsv").open("w", encoding="utf-8") as stream:
        stream.write("line\tregistry_id_expression\tcategory\tclass\tenergy_accepted\tenergy_emitted\tname_expression\n")
        for row in registrations:
            stream.write(
                f"{row['line']}\t{row['registry_id_expression']}\t{row['category']}\t"
                f"{row['class']}\t{row['energy_accepted']}\t{row['energy_emitted']}\t"
                f"{row['name_expression']}\n"
            )
    registrations_json = json.dumps(registrations, ensure_ascii=False, indent=2) + "\n"
    (OUTPUT / "multitile_registry.json").write_text(registrations_json, encoding="utf-8")
    (PORT_DATA / "machines.json").write_text(registrations_json, encoding="utf-8")
    with (OUTPUT / "recipe_index.tsv").open("w", encoding="utf-8") as stream:
        stream.write("file\tline\tmethod\tsource\n")
        for row in recipe_rows:
            stream.write(
                f"{row['file']}\t{row['line']}\t{row['method']}\t{row['source']}\n"
            )

    print(json.dumps(inventory["counts"], indent=2))
    print(f"Wrote inventory to {OUTPUT}")


if __name__ == "__main__":
    main()
