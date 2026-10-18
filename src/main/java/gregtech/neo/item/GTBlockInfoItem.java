package gregtech.neo.item;

import gregtech.neo.block.AxleBlock;
import gregtech.neo.block.BasicCrusherBlock;
import gregtech.neo.block.CableBlock;
import gregtech.neo.block.CrucibleBlock;
import gregtech.neo.block.GeneratorBlock;
import gregtech.neo.block.KineticConverterBlock;
import gregtech.neo.block.LargeCrusherBlock;
import gregtech.neo.block.MachinePortBlock;
import gregtech.neo.block.ManualCrankBlock;
import gregtech.neo.block.TemperatureSensorBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class GTBlockInfoItem extends BlockItem {
    public GTBlockInfoItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (getBlock() instanceof LargeCrusherBlock) {
            GT6Tooltip.header(tooltip, "MULTIBLOCK CONTROLLER");
            GT6Tooltip.line(tooltip, ChatFormatting.AQUA, "STRUCT: 5x5x3 hollow basin");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "ENERGY: RU 512..4096 // EFF 50%");
            GT6Tooltip.line(tooltip, ChatFormatting.GRAY, "IN: crusher wheels // OUT: bottom ports");
            GT6Tooltip.nbt(tooltip, "kind:\"RU\", formed:false, facing:\"north\"");
        } else if (getBlock() instanceof BasicCrusherBlock) {
            GT6Tooltip.header(tooltip, "KINETIC CRUSHER");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "ENERGY: KU 16..64 // REC 32 KU");
            GT6Tooltip.line(tooltip, ChatFormatting.GRAY, "CTRL: insert item // empty hand: output");
            GT6Tooltip.nbt(tooltip, "kind:\"KU\", input:9, output:9");
        } else if (getBlock() instanceof CrucibleBlock) {
            GT6Tooltip.header(tooltip, "LARGE CRUCIBLE");
            GT6Tooltip.line(tooltip, ChatFormatting.AQUA, "STRUCT: 3x3x3 hollow wall ring");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "ENERGY: HU IN // KU > AIR");
            GT6Tooltip.line(tooltip, ChatFormatting.RED, "CAP: 432U // WALL LIMIT: MP x1.10");
            GT6Tooltip.line(tooltip, ChatFormatting.GRAY, "DROP: material entities into center");
            GT6Tooltip.nbt(tooltip, "kind:\"HEAT\", capacity:\"432U\", steelmaking:true");
        } else if (getBlock() instanceof TemperatureSensorBlock) {
            GT6Tooltip.header(tooltip, "THERMOMETER SENSOR");
            GT6Tooltip.line(tooltip, ChatFormatting.AQUA, "SHAPE: 2px wall panel // 6-way facing");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "OUT: analog redstone 0..15");
            GT6Tooltip.nbt(tooltip, "poll:10t, source:\"adjacent temperature provider\"");
        } else if (getBlock() instanceof GeneratorBlock) {
            GT6Tooltip.header(tooltip, "LV GENERATOR");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "ENERGY: EU 32 x 2 A");
            GT6Tooltip.line(tooltip, ChatFormatting.GRAY, "FUEL: solid furnace fuels");
            GT6Tooltip.nbt(tooltip, "kind:\"EU\", voltage:32, amperage:2");
        } else if (getBlock() instanceof ManualCrankBlock) {
            GT6Tooltip.header(tooltip, "MANUAL CRANK");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "OUTPUT: RU 16 x 1");
            GT6Tooltip.line(tooltip, ChatFormatting.GRAY, "CTRL: right-click to rotate");
            GT6Tooltip.nbt(tooltip, "kind:\"RU\", size:16, amount:1");
        } else if (getBlock() instanceof KineticConverterBlock) {
            GT6Tooltip.header(tooltip, "KINETIC CONVERTER");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "ENERGY: EU/RU IN -> RU/KU OUT");
            GT6Tooltip.line(tooltip, ChatFormatting.GRAY, "LOSS: 50% // wrench: rotate");
        } else if (getBlock() instanceof MachinePortBlock) {
            GT6Tooltip.header(tooltip, "MULTIBLOCK PORT");
            GT6Tooltip.line(tooltip, ChatFormatting.AQUA, "MODES: OFF // ENERGY IN // ITEM I/O // FLUID I/O");
            GT6Tooltip.line(tooltip, ChatFormatting.GRAY, "SCREWDRIVER: cycle mode");
        } else if (getBlock() instanceof AxleBlock) {
            GT6Tooltip.header(tooltip, "RU AXLE");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "ENERGY: RU pass-through");
        } else if (getBlock() instanceof CableBlock cable) {
            GT6Tooltip.header(tooltip, "EU CABLE");
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD,
                "RATING: " + cable.fallbackSpec().maximumVoltage() + "V x "
                    + cable.fallbackSpec().maximumAmperage() + "A");
            GT6Tooltip.line(tooltip, ChatFormatting.RED,
                "LOSS: " + cable.fallbackSpec().voltageLossPerBlock() + "V/block");
        } else {
            GT6Tooltip.nbt(tooltip, "block:\"" + BuiltInRegistries.BLOCK.getKey(getBlock()) + "\"");
        }
    }
}
