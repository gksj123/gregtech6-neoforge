#!/usr/bin/env python3
"""Extract repeated GT6 gameplay properties into normalized JSON.

This tool intentionally separates evaluated values from source expressions.
It handles literals, simple arithmetic, common material references, heat/qual
setters, steal() inheritance, and constructor heat parameters. Anything more
dynamic remains unresolved instead of being assigned a fabricated value.
"""

from __future__ import annotations

import ast
import json
import re
from collections import Counter
from pathlib import Path

from inventory_gt6 import (
    MATERIAL_CALL,
    OUTPUT,
    SOURCE,
    evaluate_java_integer,
    matching_delimiter,
    split_top_level_arguments,
)


PORT = Path(__file__).resolve().parents[1]
PORT_DATA = PORT / "src/main/resources/data/gregtech/gt6"
MATERIAL_SOURCE = SOURCE / "gregapi/data/MT.java"
CS_SOURCE = SOURCE / "gregapi/data/CS.java"
LOADER_SOURCE = SOURCE / "gregtech/loaders/b/Loader_MultiTileEntities.java"

METHOD_DECL = re.compile(
    r"\bOreDictMaterial\s+([A-Za-z_][A-Za-z0-9_]*)\s*\("
)
SFX_CALL = re.compile(r"UT\.Sounds\.send\s*\(")
SFX_REFERENCE = re.compile(r"\bSFX\.([A-Za-z0-9_]+)")
METHOD_SCOPE = re.compile(
    r"\b(?:public|protected|private)\s+[\w<>\[\].?, ]+\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)\s*\("
)
DEFAULT_MATERIAL = {
    "melting_kelvin": 1000,
    "boiling_kelvin": 3000,
    "plasma_kelvin": 10000,
    "tool_types": 0,
    "tool_speed": 1.0,
    "tool_durability": 0,
    "tool_quality": 0,
    "density_g_cm3": 1.0,
}


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    PORT_DATA.mkdir(parents=True, exist_ok=True)
    existing_materials = json.loads((OUTPUT / "materials.json").read_text(encoding="utf-8"))
    materials = extract_material_gameplay(existing_materials)
    sound_constants = extract_sound_constants()
    block_types = extract_block_types()
    class_paths = {
        path.stem: path
        for path in SOURCE.rglob("*.java")
    }
    sound_profiles = extract_sound_profiles(class_paths, sound_constants)
    registry = json.loads((OUTPUT / "multitile_registry.json").read_text(encoding="utf-8"))
    machines = enrich_machines(registry, block_types, sound_profiles)
    validate_extraction(materials, machines)

    material_counts = Counter(row["heat_confidence"] for row in materials)
    tool_counts = Counter(row["tool_confidence"] for row in materials)
    density_counts = Counter(row["density_confidence"] for row in materials)
    material_json = json.dumps(materials, ensure_ascii=False, indent=2) + "\n"
    machine_json = json.dumps(machines, ensure_ascii=False, indent=2) + "\n"
    sound_json = json.dumps(sound_profiles, ensure_ascii=False, indent=2) + "\n"

    (OUTPUT / "materials_gameplay.json").write_text(material_json, encoding="utf-8")
    (OUTPUT / "machines_gameplay.json").write_text(machine_json, encoding="utf-8")
    (OUTPUT / "sound_profiles.json").write_text(sound_json, encoding="utf-8")
    (PORT_DATA / "materials_gameplay.json").write_text(material_json, encoding="utf-8")
    (PORT_DATA / "machines_gameplay.json").write_text(machine_json, encoding="utf-8")
    (PORT_DATA / "sound_profiles.json").write_text(sound_json, encoding="utf-8")

    summary = {
        "materials": len(materials),
        "materials_with_resolved_heat": sum(
            row["heat_confidence"] != "default_unverified" for row in materials
        ),
        "heat_confidence": dict(sorted(material_counts.items())),
        "materials_with_tool_stats": sum(
            row["tool_types"] > 0 and row["tool_confidence"] != "default_unverified"
            for row in materials
        ),
        "tool_confidence": dict(sorted(tool_counts.items())),
        "density_confidence": dict(sorted(density_counts.items())),
        "machines": len(machines),
        "machines_with_known_block_sound": sum(
            bool(row["block_sound"]["step_sound"]) for row in machines
        ),
        "sound_profiles": len(sound_profiles),
    }
    (OUTPUT / "gameplay_properties_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, indent=2))


def validate_extraction(
    materials: list[dict[str, object]],
    machines: list[dict[str, object]],
) -> None:
    by_name = {row["name"]: row for row in materials}
    expected = {
        "Iron": (1811, 3134, 6.0, 256, 2, 7.874),
        "Copper": (1357, 2835, 4.0, 64, 0, 8.96),
        "Steel": (2046, 3134, 6.0, 512, 2, 1.0),
        "Stone": (1100, 2200, 2.0, 16, 1, 1.0),
        "Water": (273, 373, None, None, None, 1.0),
    }
    for name, values in expected.items():
        row = by_name.get(name)
        if row is None:
            raise AssertionError(f"missing source material {name}")
        actual = (
            row["melting_kelvin"],
            row["boiling_kelvin"],
            row["tool_speed"],
            row["tool_durability"],
            row["tool_quality"],
            row["density_g_cm3"],
        )
        for actual_value, expected_value in zip(actual, values):
            if expected_value is not None and actual_value != expected_value:
                raise AssertionError(
                    f"{name}: expected {values}, got {actual}"
                )

    crusher = next(
        row for row in machines
        if row["display_name"] == "Large Crusher"
    )
    if crusher["hardness"] != 12.5 or crusher["resistance"] != 12.5:
        raise AssertionError("Large Crusher hardness/resistance extraction regressed")
    if crusher["block_sound"]["step_sound"] != "metal":
        raise AssertionError("Large Crusher block sound extraction regressed")


def extract_material_gameplay(
    existing_materials: list[dict[str, object]],
) -> list[dict[str, object]]:
    text = MATERIAL_SOURCE.read_text(encoding="utf-8")
    constructor_heat = extract_constructor_heat_parameters(text)
    constructor_density = extract_constructor_density_parameters(text)
    source_calls = list(MATERIAL_CALL.finditer(text))
    material_by_key = {
        (int(material["id"]), str(material["name"])): material
        for material in existing_materials
    }
    parsed_by_key: dict[tuple[int, str], dict[str, object]] = {}
    symbol_stats: dict[str, dict[str, object]] = {}

    for match in source_calls:
        constructor, material_id, name, symbol = match.groups()
        open_paren = text.find("(", match.start())
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        arguments = split_top_level_arguments(text[open_paren + 1 : close_paren])
        chain_start = close_paren + 1
        chain_end = find_expression_end(text, chain_start)
        chain = text[chain_start:chain_end]
        field_name = declaration_field(text, match.start())

        stats = dict(DEFAULT_MATERIAL)
        heat_confidence = "default_unverified"
        tool_confidence = "default_unverified"
        density_confidence = "default_unverified"
        heat_expression = ""
        tool_expression = ""
        density_expression = ""

        heat_indices = constructor_heat.get(constructor)
        if heat_indices:
            melt_index, boil_index = heat_indices
            melt = evaluate_value(
                argument(arguments, melt_index),
                symbol_stats,
            )
            boil = evaluate_value(
                argument(arguments, boil_index),
                symbol_stats,
            )
            if melt is not None:
                stats["melting_kelvin"] = melt
                stats["boiling_kelvin"] = boil if boil is not None else melt * 2
                stats["plasma_kelvin"] = stats["boiling_kelvin"] * 100
                heat_confidence = "constructor_parameter"
                heat_expression = ", ".join(
                    argument(arguments, index) for index in heat_indices
                )

        density_index = constructor_density.get(constructor)
        if density_index is not None:
            density = evaluate_value(argument(arguments, density_index), symbol_stats)
            if density is not None:
                stats["density_g_cm3"] = density
                density_confidence = "constructor_parameter"
                density_expression = argument(arguments, density_index)

        (
            heat_confidence,
            tool_confidence,
            density_confidence,
            heat_expression,
            tool_expression,
            density_expression,
        ) = apply_chain(
            chain,
            stats,
            symbol_stats,
            heat_confidence,
            tool_confidence,
            density_confidence,
            heat_expression,
            tool_expression,
            density_expression,
        )

        key = (int(material_id), name)
        if key in material_by_key:
            row = {
                "id": int(material_id),
                "name": name,
                "slug": slug(name),
                "symbol": symbol or "",
                "source": str(MATERIAL_SOURCE.relative_to(SOURCE.parent)),
                "source_line": text.count("\n", 0, match.start()) + 1,
                "constructor": constructor,
                **stats,
                "heat_expression": heat_expression,
                "tool_expression": tool_expression,
                "density_expression": density_expression,
                "heat_confidence": heat_confidence,
                "tool_confidence": tool_confidence,
                "density_confidence": density_confidence,
            }
            previous = parsed_by_key.get(key)
            if previous is None or confidence_rank(row) > confidence_rank(previous):
                parsed_by_key[key] = row
        symbol_stats[name] = stats
        if field_name:
            symbol_stats[field_name] = stats

    apply_wrapper_field_chains(
        text,
        material_by_key,
        parsed_by_key,
        symbol_stats,
    )
    reapply_material_chains(
        text,
        material_by_key,
        parsed_by_key,
        symbol_stats,
    )

    results: list[dict[str, object]] = []
    for material in existing_materials:
        key = (int(material["id"]), str(material["name"]))
        if key in parsed_by_key:
            results.append(parsed_by_key[key])
            continue
        results.append(
            {
                "id": int(material["id"]),
                "name": str(material["name"]),
                "slug": slug(str(material["name"])),
                "symbol": str(material.get("symbol", "")),
                "source": str(MATERIAL_SOURCE.relative_to(SOURCE.parent)),
                "source_line": 0,
                "constructor": str(material.get("constructor", "")),
                **DEFAULT_MATERIAL,
                "heat_expression": "",
                "tool_expression": "",
                "density_expression": "",
                "heat_confidence": "default_unverified",
                "tool_confidence": "default_unverified",
                "density_confidence": "default_unverified",
            }
        )
    return results


def extract_constructor_heat_parameters(text: str) -> dict[str, tuple[int, int]]:
    result: dict[str, tuple[int, int]] = {}
    for match in METHOD_DECL.finditer(text):
        method_name = match.group(1)
        open_paren = text.find("(", match.start())
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        params = split_top_level_arguments(text[open_paren + 1 : close_paren])
        melt_index = next(
            (index for index, param in enumerate(params) if "ameltingpoint" in param.lower()),
            None,
        )
        boil_index = next(
            (index for index, param in enumerate(params) if "aboilingpoint" in param.lower()),
            None,
        )
        if melt_index is not None:
            result[method_name] = (
                melt_index,
                boil_index if boil_index is not None else melt_index,
            )
    return result


def extract_constructor_density_parameters(text: str) -> dict[str, int]:
    result: dict[str, int] = {}
    for match in METHOD_DECL.finditer(text):
        method_name = match.group(1)
        open_paren = text.find("(", match.start())
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        params = split_top_level_arguments(text[open_paren + 1 : close_paren])
        density_index = next(
            (
                index
                for index, param in enumerate(params)
                if "agrampercubiccentimeter" in param.lower()
                or "adensity" in param.lower()
            ),
            None,
        )
        if density_index is not None:
            result[method_name] = density_index
    return result


def declaration_field(text: str, call_start: int) -> str:
    line_start = text.rfind("\n", 0, call_start) + 1
    prefix = text[line_start:call_start]
    assignments = list(re.finditer(r"([A-Za-z_][A-Za-z0-9_]*)\s*=\s*", prefix))
    return assignments[-1].group(1) if assignments else ""


def find_expression_end(text: str, start: int) -> int:
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
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif depth == 0 and char in ",;\n":
            return index
    return len(text)


def iter_chain_calls(chain: str) -> list[tuple[str, str]]:
    result: list[tuple[str, str]] = []
    cursor = 0
    while True:
        match = re.search(
            r"\.\s*(heat|qual|steal|setDensity|stealStatsElement)\s*\(",
            chain[cursor:],
        )
        if match is None:
            break
        method = match.group(1)
        open_paren = cursor + match.end() - 1
        close_paren = matching_delimiter(chain, open_paren, "(", ")")
        if close_paren < 0:
            break
        result.append((method, chain[open_paren + 1 : close_paren]))
        cursor = close_paren + 1
    return result


def apply_chain(
    chain: str,
    stats: dict[str, object],
    symbols: dict[str, dict[str, object]],
    heat_confidence: str,
    tool_confidence: str,
    density_confidence: str,
    heat_expression: str,
    tool_expression: str,
    density_expression: str,
) -> tuple[str, str, str, str, str, str]:
    for method, method_arguments in iter_chain_calls(chain):
        if method == "heat":
            heat_expression = method_arguments
            values = split_top_level_arguments(method_arguments)
            if len(values) == 1:
                target = values[0].strip()
                referenced = symbols.get(target)
                if referenced is not None:
                    copy_stats(stats, referenced)
                    heat_confidence = "stolen_reference"
                else:
                    melt = evaluate_value(target, symbols)
                    if melt is not None:
                        stats["melting_kelvin"] = melt
                        stats["boiling_kelvin"] = melt * 2
                        stats["plasma_kelvin"] = stats["boiling_kelvin"] * 100
                        heat_confidence = "explicit_chain"
            elif len(values) >= 2:
                melt = evaluate_value(values[0], symbols)
                boil = evaluate_value(values[1], symbols)
                plasma = evaluate_value(values[2], symbols) if len(values) >= 3 else None
                if melt is not None and boil is not None:
                    stats["melting_kelvin"] = melt
                    stats["boiling_kelvin"] = boil
                    stats["plasma_kelvin"] = plasma if plasma is not None else boil * 100
                    heat_confidence = "explicit_chain"
        elif method == "qual":
            tool_expression = method_arguments
            values = split_top_level_arguments(method_arguments)
            if len(values) == 1:
                target = values[0].strip()
                referenced = symbols.get(target)
                if referenced is not None:
                    copy_tool_stats(stats, referenced)
                    tool_confidence = "stolen_reference"
                else:
                    quality = evaluate_value(target, symbols)
                    if quality is not None:
                        stats["tool_quality"] = int(quality)
                        tool_confidence = "explicit_chain"
            elif len(values) >= 4:
                evaluated = [evaluate_value(value, symbols) for value in values[:4]]
                if all(value is not None for value in evaluated):
                    stats["tool_types"] = int(evaluated[0])
                    stats["tool_speed"] = float(evaluated[1])
                    stats["tool_durability"] = int(evaluated[2])
                    stats["tool_quality"] = int(evaluated[3])
                    tool_confidence = "explicit_chain"
        elif method == "steal":
            referenced = symbols.get(method_arguments.strip())
            if referenced is not None:
                copy_stats(stats, referenced)
                copy_tool_stats(stats, referenced)
                heat_confidence = "stolen_reference"
                tool_confidence = "stolen_reference"
                density_confidence = "stolen_reference"
        elif method == "setDensity":
            density_expression = method_arguments
            density = evaluate_value(method_arguments, symbols)
            if density is not None:
                stats["density_g_cm3"] = density
                density_confidence = "explicit_chain"
        elif method == "stealStatsElement":
            referenced = symbols.get(method_arguments.strip())
            if referenced is not None:
                stats["density_g_cm3"] = referenced["density_g_cm3"]
                density_confidence = "stolen_reference"
    return (
        heat_confidence,
        tool_confidence,
        density_confidence,
        heat_expression,
        tool_expression,
        density_expression,
    )


def apply_wrapper_field_chains(
    text: str,
    material_by_key: dict[tuple[int, str], dict[str, object]],
    parsed_by_key: dict[tuple[int, str], dict[str, object]],
    symbols: dict[str, dict[str, object]],
) -> None:
    helper_materials = extract_helper_materials(text)
    pattern = re.compile(
        r"\b([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"
        r"([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*\)"
    )
    for match in pattern.finditer(text):
        field_name, helper = match.groups()
        key = helper_materials.get(helper)
        if key is None or key not in material_by_key:
            continue
        open_paren = text.find("(", match.start())
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        chain_end = find_expression_end(text, close_paren + 1)
        chain = text[close_paren + 1 : chain_end]
        if not chain.strip():
            continue
        existing = parsed_by_key.get(key)
        if existing is None:
            continue
        stats = {
            key_name: existing[key_name]
            for key_name in DEFAULT_MATERIAL
        }
        heat_confidence = str(existing["heat_confidence"])
        tool_confidence = str(existing["tool_confidence"])
        density_confidence = str(existing["density_confidence"])
        heat_expression = str(existing["heat_expression"])
        tool_expression = str(existing["tool_expression"])
        density_expression = str(existing["density_expression"])
        (
            heat_confidence,
            tool_confidence,
            density_confidence,
            heat_expression,
            tool_expression,
            density_expression,
        ) = apply_chain(
            chain,
            stats,
            symbols,
            heat_confidence,
            tool_confidence,
            density_confidence,
            heat_expression,
            tool_expression,
            density_expression,
        )
        existing.update(stats)
        existing["heat_confidence"] = heat_confidence
        existing["tool_confidence"] = tool_confidence
        existing["density_confidence"] = density_confidence
        existing["heat_expression"] = heat_expression
        existing["tool_expression"] = tool_expression
        existing["density_expression"] = density_expression
        existing["source_line"] = text.count("\n", 0, match.start()) + 1
        symbols[field_name] = stats


def extract_helper_materials(text: str) -> dict[str, tuple[int, str]]:
    result: dict[str, tuple[int, str]] = {}
    for match in METHOD_DECL.finditer(text):
        method_name = match.group(1)
        open_paren = text.find("(", match.start())
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        open_brace = text.find("{", close_paren)
        if open_brace < 0:
            continue
        close_brace = matching_delimiter(text, open_brace, "{", "}")
        if close_brace < 0:
            continue
        body = text[open_brace + 1 : close_brace]
        material_call = MATERIAL_CALL.search(body)
        if material_call is None:
            continue
        _, material_id, name, _ = material_call.groups()
        result[method_name] = (int(material_id), name)
    return result


def reapply_material_chains(
    text: str,
    material_by_key: dict[tuple[int, str], dict[str, object]],
    parsed_by_key: dict[tuple[int, str], dict[str, object]],
    symbols: dict[str, dict[str, object]],
) -> None:
    for match in MATERIAL_CALL.finditer(text):
        _, material_id, name, _ = match.groups()
        key = (int(material_id), name)
        if key not in material_by_key or key not in parsed_by_key:
            continue
        open_paren = text.find("(", match.start())
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        chain_end = find_expression_end(text, close_paren + 1)
        chain = text[close_paren + 1 : chain_end]
        existing = parsed_by_key[key]
        stats = {key_name: existing[key_name] for key_name in DEFAULT_MATERIAL}
        heat_confidence = str(existing["heat_confidence"])
        tool_confidence = str(existing["tool_confidence"])
        density_confidence = str(existing["density_confidence"])
        heat_expression = str(existing["heat_expression"])
        tool_expression = str(existing["tool_expression"])
        density_expression = str(existing["density_expression"])
        (
            heat_confidence,
            tool_confidence,
            density_confidence,
            heat_expression,
            tool_expression,
            density_expression,
        ) = apply_chain(
            chain,
            stats,
            symbols,
            heat_confidence,
            tool_confidence,
            density_confidence,
            heat_expression,
            tool_expression,
            density_expression,
        )
        existing.update(stats)
        existing["heat_confidence"] = heat_confidence
        existing["tool_confidence"] = tool_confidence
        existing["density_confidence"] = density_confidence
        existing["heat_expression"] = heat_expression
        existing["tool_expression"] = tool_expression
        existing["density_expression"] = density_expression
        field_name = declaration_field(text, match.start())
        symbols[name] = stats
        if field_name:
            symbols[field_name] = stats


def evaluate_value(
    expression: str,
    symbols: dict[str, dict[str, object]],
) -> float | None:
    if expression is None:
        return None
    value = expression.strip()
    if not value or value in {"null", "NF", "NI"}:
        return None
    value = value.replace("CS.C", "273")
    reference = re.fullmatch(r"([A-Za-z_][A-Za-z0-9_]*)\.m(MeltingPoint|BoilingPoint|PlasmaPoint)", value)
    if reference:
        material = symbols.get(reference.group(1))
        if material is None:
            return None
        key = {
            "MeltingPoint": "melting_kelvin",
            "BoilingPoint": "boiling_kelvin",
            "PlasmaPoint": "plasma_kelvin",
        }[reference.group(2)]
        return float(material[key])

    def replace_reference(match: re.Match[str]) -> str:
        material = symbols.get(match.group(1))
        if material is None:
            raise ValueError("unknown material")
        key = {
            "MeltingPoint": "melting_kelvin",
            "BoilingPoint": "boiling_kelvin",
            "PlasmaPoint": "plasma_kelvin",
        }[match.group(2)]
        return str(material[key])

    try:
        value = re.sub(
            r"\b([A-Za-z_][A-Za-z0-9_]*)\.m(MeltingPoint|BoilingPoint|PlasmaPoint)",
            replace_reference,
            value,
        )
    except ValueError:
        return None
    value = re.sub(r"\([a-zA-Z]+\)", "", value).replace("L", "").replace("F", "")
    try:
        tree = ast.parse(value, mode="eval")
    except SyntaxError:
        return None

    def evaluate(node: ast.AST) -> float:
        if isinstance(node, ast.Expression):
            return evaluate(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return float(node.value)
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
                return left / right
            if isinstance(node.op, ast.Mod):
                return left % right
        raise ValueError("unsupported expression")

    try:
        return evaluate(tree)
    except (TypeError, ValueError, ZeroDivisionError):
        return None


def argument(arguments: list[str], index: int) -> str:
    return arguments[index].strip() if 0 <= index < len(arguments) else ""


def copy_stats(target: dict[str, object], source: dict[str, object]) -> None:
    for key in ("melting_kelvin", "boiling_kelvin", "plasma_kelvin", "density_g_cm3"):
        target[key] = source[key]


def copy_tool_stats(target: dict[str, object], source: dict[str, object]) -> None:
    for key in ("tool_types", "tool_speed", "tool_durability", "tool_quality"):
        target[key] = source[key]


def confidence_rank(row: dict[str, object]) -> int:
    ranks = {
        "default_unverified": 0,
        "constructor_parameter": 1,
        "stolen_reference": 2,
        "explicit_chain": 3,
    }
    return ranks.get(str(row["heat_confidence"]), 0) + ranks.get(
        str(row["tool_confidence"]),
        0,
    ) + ranks.get(
        str(row["density_confidence"]),
        0,
    )


def extract_sound_constants() -> dict[str, str]:
    text = CS_SOURCE.read_text(encoding="utf-8")
    start = text.find("public static class SFX")
    open_brace = text.find("{", start)
    close_brace = matching_delimiter(text, open_brace, "{", "}")
    if start < 0 or close_brace < 0:
        return {}
    block = text[open_brace + 1 : close_brace]
    values: dict[str, str] = {}
    unresolved: dict[str, str] = {}
    for match in re.finditer(
        r"\b([A-Z][A-Z0-9_]+)\s*=\s*(\"(?:[^\"\\]|\\.)*\"|[A-Z][A-Z0-9_]+)",
        block,
    ):
        field, value = match.groups()
        if value.startswith('"'):
            values[field] = value[1:-1]
        else:
            unresolved[field] = value
    changed = True
    while changed:
        changed = False
        for field, target in list(unresolved.items()):
            if target in values:
                values[field] = values[target]
                del unresolved[field]
                changed = True
    return values


def extract_block_types() -> dict[str, dict[str, object]]:
    text = LOADER_SOURCE.read_text(encoding="utf-8")
    result: dict[str, dict[str, object]] = {}
    for match in re.finditer(
        r"\b(a[A-Za-z0-9_]+)\s*=\s*MultiTileEntityBlock\.getOrCreate\s*\(",
        text,
    ):
        open_paren = text.find("(", match.end() - 1)
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        args = split_top_level_arguments(text[open_paren + 1 : close_paren])
        sound = next(
            (
                sound_match.group(1)
                for arg in args
                if (sound_match := re.search(r"Block\.soundType([A-Za-z0-9_]+)", arg))
            ),
            "",
        )
        tool = next(
            (
                tool_match.group(1)
                for arg in args
                if (tool_match := re.search(r"\bTOOL_([A-Za-z0-9_]+)", arg))
            ),
            "",
        )
        result[match.group(1)] = {
            "vanilla_material": args[2].strip() if len(args) > 2 else "",
            "step_sound": sound[:1].lower() + sound[1:] if sound else "",
            "tool_class": tool.lower(),
            "source": str(LOADER_SOURCE.relative_to(SOURCE.parent)),
        }
    return result


def extract_sound_profiles(
    class_paths: dict[str, Path],
    sound_constants: dict[str, str],
) -> list[dict[str, object]]:
    profiles: list[dict[str, object]] = []
    for class_name, path in sorted(class_paths.items()):
        text = path.read_text(encoding="utf-8", errors="replace")
        events: list[dict[str, object]] = []
        for match in SFX_CALL.finditer(text):
            open_paren = text.find("(", match.start())
            close_paren = matching_delimiter(text, open_paren, "(", ")")
            if close_paren < 0:
                continue
            args = split_top_level_arguments(text[open_paren + 1 : close_paren])
            if not args:
                continue
            sound_expression = args[0].strip()
            sounds = SFX_REFERENCE.findall(sound_expression)
            if not sounds:
                continue
            volume = (
                evaluate_value(args[1], {})
                if len(args) > 1 and not args[1].strip().startswith("this")
                else 1.0
            )
            pitch = (
                evaluate_value(args[2], {})
                if len(args) > 2 and not args[2].strip().startswith("this")
                else None
            )
            scope_match = list(METHOD_SCOPE.finditer(text[: match.start()]))
            scope = scope_match[-1].group(1) if scope_match else "<static>"
            events.append(
                {
                    "method": scope,
                    "sound_expression": sound_expression,
                    "sound_ids": [
                        sound_constants.get(name, name)
                        for name in sounds
                    ],
                    "volume": volume if volume is not None else 1.0,
                    "pitch": pitch,
                    "line": text.count("\n", 0, match.start()) + 1,
                }
            )
        if events:
            profiles.append(
                {
                    "class": class_name,
                    "source": str(path.relative_to(SOURCE.parent)),
                    "events": events,
                }
            )
    return profiles


def enrich_machines(
    registry: list[dict[str, object]],
    block_types: dict[str, dict[str, object]],
    sound_profiles: list[dict[str, object]],
) -> list[dict[str, object]]:
    text = LOADER_SOURCE.read_text(encoding="utf-8")
    profile_by_class = {profile["class"]: profile for profile in sound_profiles}
    result: list[dict[str, object]] = []
    for row in registry:
        line = int(row["line"])
        line_text = text.splitlines()[line - 1] if 0 < line <= text.count("\n") + 1 else ""
        block_type = ""
        registration_match = re.search(r"aRegistry\.add\s*\(", line_text)
        if registration_match:
            open_paren = line_text.find("(", registration_match.start())
            close_paren = matching_delimiter(line_text, open_paren, "(", ")")
            if close_paren >= 0:
                args = split_top_level_arguments(line_text[open_paren + 1 : close_paren])
                block_type = args[7].strip() if len(args) > 7 else ""
        sound = block_types.get(block_type, {})
        class_name = str(row.get("class", ""))
        profile = profile_by_class.get(class_name, {})
        result.append(
            {
                **row,
                "hardness": extract_nbt_number(line_text, "NBT_HARDNESS"),
                "resistance": extract_nbt_number(line_text, "NBT_RESISTANCE"),
                "block_type": block_type,
                "block_sound": {
                    "step_sound": sound.get("step_sound", ""),
                    "tool_class": sound.get("tool_class", ""),
                    "vanilla_material": sound.get("vanilla_material", ""),
                },
                "sound_events": profile.get("events", []),
            }
        )
    return result


def extract_nbt_number(text: str, tag: str) -> float | None:
    match = re.search(rf"{tag}\s*,\s*(-?\d+(?:\.\d+)?)F?", text)
    return float(match.group(1)) if match else None


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


if __name__ == "__main__":
    main()
