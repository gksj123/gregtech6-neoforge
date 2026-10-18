# Asset Sources

The current block textures are imported from the sibling GregTech 6 source
checkout by `tools/import_gt6_assets.py`. The script composes each GT6 colored
base texture with its matching overlay before writing the modern texture.

- `machine_casing.png`: `materialicons/METALLIC/casingMachine.png`
- `basic_crusher_*.png`: `basicmachines/crusher/colored` and `overlay`
- `large_crusher_*.png`: `basicmachines/largecrusher/colored` and `overlay`
- `large_crusher_formed_*.png`: `basicmachines/largecrusher/colored` and `overlay_active`
- `burning_box_*.png`: `generators/burning_solid/colored` and `overlay`
- `burning_box_active_*.png`: `generators/burning_solid/overlay_active`, including animated front metadata
- `lv_generator_*.png`: `energystorages/battery_electric/colored` and `overlay`
- `electric_motor_*.png`: `motors/rotation_electric/colored` and `overlay`
- `electric_engine_*.png`: `engines/kinetic_electric/colored` and `overlay`
- `rotation_engine_*.png`: `engines/kinetic_rotation/colored` and `overlay`
- `manual_crank_front.png`: `tools/crank/colored/front.png` and overlay
- `temperature_sensor_*.png`: `redstone/sensors/thermometer/colored` and overlay
- `large_crucible_*.png`: `multiblockmains/crucible/colored_front` and overlay
- `crucible_wall_*.png`: `multiblockparts/metalwall/0/colored` and overlay
- `tungstensteel_wall_*.png`: the same GT6 metal-wall set tinted as tungstensteel
- `machine_port.png`: `multiblockparts/metalwall/1/colored/side.png`
- `small_crucible_wall.png`: `materialicons/METALLIC/blockSolid.png`, tinted at runtime by the wall material
- `crucible_content.png`: `materialicons/METALLIC/molten.png`, tinted by the displayed content material

GregTech 6 assets are released under CC0 unless the original asset states otherwise. See the parent project's `src/main/resources/LICENSE.assets`.

The tin cable, copper cable, axle, wrench, screwdriver, soft hammer, magnifying
glass, monkey wrench, and electrometer textures remain small generated
placeholders created for this port.
