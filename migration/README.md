# Migration Inventory

The files in this directory are generated from the legacy GT6 source by `tools/inventory_gt6.py`.

They are migration inputs and tracking artifacts. They are not hand-authored gameplay data. Any modification to extraction semantics belongs in the inventory script.

Key files:

- `materials.json`: material declarations and extracted colors.
- `prefixes.json`: material forms, including source material-unit amounts.
- `multitile_registry.json`: all 1366 legacy machine registrations.
- `recipe_handlers.json`: all extracted recipe-map handlers and their port-support flag.
- `recipes.json`: explicit source-backed item recipes currently safe to migrate.
- `recipe_index.tsv`: index of legacy recipe call sites.
- `materials_gameplay.json`: normalized heat, density/thermal-mass, and tool statistics.
- `machines_gameplay.json`: hardness, resistance, block sound, and machine sound events.
- `sound_profiles.json`: source classes that emit GT sound events.
- `gameplay_properties_summary.json`: extraction coverage and confidence counts.

Regenerate structural inventory and gameplay properties in order:

```sh
python3 tools/inventory_gt6.py
python3 tools/export_gameplay_properties.py
```

The second script also validates known anchors such as Iron, Steel, Water, Stone,
and the Large Crusher before writing output.
