package gregtech.neo.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class GT6Tooltip {
    private GT6Tooltip() {
    }

    public static void header(List<Component> tooltip, String title) {
        tooltip.add(Component.literal("[GT6] " + title).withStyle(ChatFormatting.DARK_AQUA));
    }

    public static void line(List<Component> tooltip, ChatFormatting color, String text) {
        tooltip.add(Component.literal(text).withStyle(color));
    }

    public static void nbt(List<Component> tooltip, String text) {
        line(tooltip, ChatFormatting.DARK_GRAY, "NBT: {" + text + "}");
    }

    public static void source(List<Component> tooltip, String category, int line) {
        line(tooltip, ChatFormatting.DARK_GRAY, "SRC: " + category + " // L" + line);
    }
}
