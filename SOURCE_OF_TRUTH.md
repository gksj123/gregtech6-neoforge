# GT6 Source of Truth

The port must not silently substitute GregTech 5, GTCEu, or another modern GregTech implementation for GregTech 6 behavior.

## Required precedence

1. The checked-out GT6 6.17.06 source and assets in the sibling
   `../gregtech6-legacy` checkout.
2. The official GT6 site, for explanatory material that agrees with the source.
3. FTB Wiki, for navigation and cross-checking.
4. MCMod, only as a content index.

When a secondary source conflicts with the source code, the source code wins.

## Reference links

- Official site: https://gregtech.mechaenetia.com/
- FTB Wiki: https://ftb.fandom.com/wiki/GregTech_6
- MCMod index: https://www.mcmod.cn/class/455.html

MCMod and FTB pages frequently contain mixed information from GT5, GT5 Unofficial, GTCE, GTCEu, and GT6. Their recipes, voltage progression, machine behavior, and item names must be verified against the appropriate GT6 class before porting.

## Key GT6 distinctions already confirmed

- Energy is a `TagData` family plus `size` and `amount`, not a universal voltage.
- Basic Crusher uses KU.
- Large Crusher uses RU.
- LU means Light Energy and belongs to laser machinery.
- Electric Engine converts EU to KU; Electric Motor converts EU to RU.
- RU travels through rotation infrastructure; KU is a push/pull packet.
- Machine input type is declared by `NBT_ENERGY_ACCEPTED` and checked explicitly.

## Generated inventory

Run:

```sh
python3 tools/inventory_gt6.py
```

The default legacy-source path is `../gregtech6-legacy`. Override it with
`GT6_LEGACY_HOME` if the legacy checkout is elsewhere.

Generated files live in `migration/`:

- `content_inventory.json`
- `materials.json`
- `prefixes.json`
- `recipe_handlers.json`
- `recipes.json`
- `materials_gameplay.json`
- `machines_gameplay.json`
- `sound_profiles.json`
- `alloys.json`
- `molds.json`
- `gameplay_properties_summary.json`
- `multitile_registry.tsv`
- `multitile_registry.json`

The generator also synchronizes the runtime data files under
`src/main/resources/data/gregtech/gt6/`. Do not hand-edit those JSON files;
change extraction semantics in `tools/inventory_gt6.py` and regenerate them.

Recipe handlers are enabled only when the current port can reproduce their
same-material result without fluids, extra items, or unsupported material
conditions. The generated `supported` flag documents that boundary. It is a
port capability flag, not a claim about GT6's original recipe validity.

Gameplay properties are extracted by `tools/export_gameplay_properties.py`.
Values carry a confidence field rather than pretending every source expression
is a literal:

- `explicit_chain`: parsed from `heat(...)`, `qual(...)`, or equivalent source.
- `constructor_parameter`: parsed from a helper constructor parameter.
- `stolen_reference`: copied from a resolved `steal(...)` target.
- `default_unverified`: no simple assignment was resolved; review before treating
  it as final. The class default may be correct, or the constructor may compute it.

Density carries a parallel confidence field and feeds GT6's crucible thermal
mass calculation.

Machine sound extraction separates the block step sound from emitted sound
events. GT6 does not store a universal per-machine loudness scalar in the
multitile registration, so fabricating one would be incorrect.

Alloy recipes are extracted by `tools/export_alloys.py`. Both `setAloy` and
`uumAloy` are included because both reach `alloySimple()` and register a
crucible component configuration. Static `addAlloyingRecipe` calls are included
as well. Dynamic expressions are retained with `status: unresolved`; the runtime
does not fabricate their components.

Mold and faucet data are extracted by `tools/export_molds.py`. The script
evaluates the source `TEMP_MOLD_RECIPES` table, reads the ceramic canonical
shape masks, and applies the same rotation and mirror transformations as
`MultiTileEntityMold`. It also exports all resolved mold/faucet registration
materials and source lines.
