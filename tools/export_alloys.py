#!/usr/bin/env python3
"""Export GT6 crucible alloy configurations from the 1.7.10 source.

GT6 registers an alloy recipe whenever an ``OreDictMaterial`` receives
``setAloy`` or ``uumAloy``. Both helpers end in ``alloySimple()`` and therefore
feed the crucible's component graph. The static block in ``MT.java`` also adds
explicit ``addAlloyingRecipe`` entries.

The exporter resolves only expressions it can prove from source. Dynamic
expressions are emitted as ``unresolved`` and are ignored by the runtime.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
from pathlib import Path

from inventory_gt6 import (
    MATERIAL_CALL,
    evaluate_java_integer,
    matching_delimiter,
    split_top_level_arguments,
)


PORT = Path(__file__).resolve().parents[1]
LEGACY = Path(
    os.environ.get("GT6_LEGACY_HOME", PORT.parent / "gregtech6-legacy")
).expanduser().resolve()
SOURCE = LEGACY / "src/main/java"
MATERIAL_SOURCE = SOURCE / "gregapi/data/MT.java"
INVENTORY = PORT / "migration/materials.json"
OUTPUT = PORT / "migration/alloys.json"
PORT_DATA = PORT / "src/main/resources/data/gregtech/gt6/alloys.json"

ALLOY_CALL = re.compile(r"\.(setAloy|uumAloy)\s*\(")
ADD_ALLOY_CALL = re.compile(
    r"([A-Za-z_][A-Za-z0-9_]*)\s*\.addAlloyingRecipe\s*\("
)
CONFIG_CALL = re.compile(r"new\s+OreDictConfigurationComponent\s*\(")
STACK_CALL = re.compile(r"OM\.stack\s*\(")
ASSIGNMENT = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*=\s*")
MATERIAL_FACTORY = re.compile(
    r"\bOreDictMaterial\s+([A-Za-z_][A-Za-z0-9_]*)\s*\([^)]*\)\s*"
    r"\{[^}]*?\(\s*-?\d{1,5}\s*,\s*\"([^\"]+)\"",
    re.DOTALL,
)
FACTORY_FIELD = re.compile(
    r"\b([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*\)"
)


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


def material_index(text: str) -> tuple[dict[str, str], dict[str, str]]:
    inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    by_name = {str(row["name"]): slug(str(row["name"])) for row in inventory}
    by_field: dict[str, str] = {}
    factory_slugs: dict[str, str] = {}
    for method, name in MATERIAL_FACTORY.findall(text):
        if name in by_name:
            factory_slugs[method] = by_name[name]
    for match in MATERIAL_CALL.finditer(text):
        _constructor, _material_id, name, _symbol = match.groups()
        line_start = text.rfind("\n", 0, match.start()) + 1
        prefix = text[line_start : match.start()]
        assignments = ASSIGNMENT.findall(prefix)
        if not assignments:
            continue
        slug_value = by_name.get(name)
        if slug_value is None:
            continue
        field = assignments[-1]
        by_field[field] = slug_value
        by_field[name] = slug_value

    for field, factory in FACTORY_FIELD.findall(text):
        slug_value = factory_slugs.get(factory)
        if slug_value is not None:
            by_field[field] = slug_value

    # Keep resolving simple field aliases such as ``Cupronickel = Constantan``.
    for _ in range(4):
        changed = False
        for alias, target in re.findall(
            r"\b([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\s*,",
            text,
        ):
            slug_value = by_field.get(target)
            if slug_value is not None and by_field.get(alias) != slug_value:
                by_field[alias] = slug_value
                changed = True
        if not changed:
            break
    return by_name, by_field


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.lower())


def resolve_material(expression: str, fields: dict[str, str]) -> str | None:
    expression = expression.strip()
    if expression.startswith("MT."):
        expression = expression[3:]
    token = expression.rsplit(".", 1)[-1]
    return fields.get(expression) or fields.get(token)


def expression_end(text: str, start: int) -> int:
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
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif depth == 0 and char in ",;\n":
            return index
    return len(text)


def declaration_field(text: str, call_start: int) -> str:
    line_start = text.rfind("\n", 0, call_start) + 1
    prefix = text[line_start:call_start]
    assignments = ASSIGNMENT.findall(prefix)
    return assignments[-1] if assignments else ""


def parse_config(
    arguments: list[str],
    fields: dict[str, str],
    direct_pairs: bool = False,
) -> tuple[int | None, list[dict[str, object]], str]:
    if not arguments:
        return None, [], "missing common divider"
    divider_expression = arguments[0].strip()
    common_divider = evaluate_java_integer(divider_expression)
    components: list[dict[str, object]] = []
    unresolved: list[str] = []
    component_arguments = arguments[1:]
    if direct_pairs:
        if len(component_arguments) % 2 != 0:
            unresolved.append("incomplete material/amount pair")
        component_arguments = [
            f"OM.stack({component_arguments[index]}, {component_arguments[index + 1]})"
            for index in range(0, len(component_arguments) - 1, 2)
        ]
    for raw_argument in component_arguments:
        expression = raw_argument.strip()
        stack_match = STACK_CALL.search(expression)
        if stack_match is None:
            unresolved.append(expression)
            continue
        open_paren = expression.find("(", stack_match.start())
        close_paren = matching_delimiter(expression, open_paren, "(", ")")
        if close_paren < 0:
            unresolved.append(expression)
            continue
        stack_arguments = split_top_level_arguments(
            expression[open_paren + 1 : close_paren]
        )
        if len(stack_arguments) != 2:
            unresolved.append(expression)
            continue
        material_expression = stack_arguments[0].strip()
        amount_expression = stack_arguments[1].strip().replace("L", "")
        material = resolve_material(material_expression, fields)
        amount = evaluate_java_integer(amount_expression)
        if material is None:
            unresolved.append(material_expression)
        if amount is None:
            unresolved.append(amount_expression)
        components.append(
            {
                "expression": material_expression,
                "material": material or "",
                "amount_expression": amount_expression,
                "amount_raw": amount or 0,
                "amount_units": amount // 648_648_000 if amount is not None else 0,
            }
        )

    if common_divider == 0 and components and all(
        int(component["amount_raw"]) > 0 for component in components
    ):
        common_divider = sum(int(component["amount_raw"]) for component in components) // 648_648_000

    reason = ""
    if common_divider is None or common_divider <= 0:
        reason = "common divider is dynamic or non-positive"
    elif unresolved:
        reason = "unresolved material/amount expressions: " + ", ".join(unresolved)
    elif any(int(component["amount_units"]) <= 0 for component in components):
        reason = "component amount is smaller than one U"
    return common_divider, components, reason


def add_recipe(
    recipes: list[dict[str, object]],
    output_expression: str,
    output: str | None,
    config_arguments: list[str],
    fields: dict[str, str],
    line: int,
    method: str,
    direct_pairs: bool = False,
) -> None:
    common_divider, components, reason = parse_config(
        config_arguments,
        fields,
        direct_pairs,
    )
    if output is None:
        reason = "unresolved output material expression: " + output_expression
    if not components:
        reason = reason or "configuration has no components"
    recipes.append(
        {
            "id": f"alloy-{len(recipes) + 1:04d}",
            "output_expression": output_expression,
            "output_material": output or "",
            "common_divider": common_divider or 0,
            "components": components,
            "source_file": "java/gregapi/data/MT.java",
            "source_line": line,
            "source_method": method,
            "status": "unresolved" if reason else "resolved",
            "unresolved_reason": reason,
        }
    )


def extract_recipes(text: str, fields: dict[str, str]) -> list[dict[str, object]]:
    recipes: list[dict[str, object]] = []

    for call in ALLOY_CALL.finditer(text):
        output_expression = declaration_field(text, call.start())
        output = resolve_material(output_expression, fields)
        open_paren = text.find("(", call.start())
        close_paren = matching_delimiter(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        line = text.count("\n", 0, call.start()) + 1
        add_recipe(
            recipes,
            output_expression,
            output,
            split_top_level_arguments(text[open_paren + 1 : close_paren]),
            fields,
            line,
            call.group(1),
            True,
        )

    for call in ADD_ALLOY_CALL.finditer(text):
        line_start = text.rfind("\n", 0, call.start()) + 1
        if text[line_start:call.start()].lstrip().startswith("//"):
            continue
        output_expression = call.group(1)
        output = resolve_material(output_expression, fields)
        call_open = text.find("(", call.start())
        call_close = matching_delimiter(text, call_open, "(", ")")
        if call_close < 0:
            continue
        call_text = text[call_open + 1 : call_close]
        config = CONFIG_CALL.search(call_text)
        if config is None:
            continue
        config_open = call_text.find("(", config.start())
        config_close = matching_delimiter(call_text, config_open, "(", ")")
        if config_close < 0:
            continue
        line = text.count("\n", 0, call.start()) + 1
        add_recipe(
            recipes,
            output_expression,
            output,
            split_top_level_arguments(call_text[config_open + 1 : config_close]),
            fields,
            line,
            "addAlloyingRecipe",
            False,
        )

    return recipes


def validate(recipes: list[dict[str, object]]) -> None:
    resolved = [row for row in recipes if row["status"] == "resolved"]
    if not any(row["output_material"] == "steel" for row in resolved):
        raise AssertionError("Steel crucible recipe was not extracted")
    if not any(row["output_material"] == "meteoricsteel" for row in resolved):
        raise AssertionError("Meteoric Steel crucible recipe was not extracted")
    steel = next(
        row for row in resolved
        if row["output_material"] == "steel"
        and row["source_method"] == "addAlloyingRecipe"
    )
    components = {row["material"]: row["amount_units"] for row in steel["components"]}
    if components != {"wroughtiron": 1, "air": 1}:
        raise AssertionError(f"Steel recipe changed unexpectedly: {components}")
    if len(resolved) < 80:
        raise AssertionError(f"Too few resolved alloy recipes: {len(resolved)}")


def main() -> None:
    text = MATERIAL_SOURCE.read_text(encoding="utf-8")
    _by_name, fields = material_index(text)
    recipes = extract_recipes(text, fields)
    validate(recipes)
    payload = {
        "format": 1,
        "source_repository": "gregtech6-legacy",
        "source_commit": git_value("rev-parse", "HEAD"),
        "source_file": "java/gregapi/data/MT.java",
        "recipe_count": len(recipes),
        "resolved_count": sum(row["status"] == "resolved" for row in recipes),
        "unresolved_count": sum(row["status"] == "unresolved" for row in recipes),
        "recipes": recipes,
    }
    encoded = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    PORT_DATA.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(encoded, encoding="utf-8")
    PORT_DATA.write_text(encoded, encoding="utf-8")
    print(
        json.dumps(
            {
                "alloy_recipes": payload["recipe_count"],
                "resolved": payload["resolved_count"],
                "unresolved": payload["unresolved_count"],
                "output": str(OUTPUT),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
