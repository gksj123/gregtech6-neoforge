package gregtech.neo.block;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class InWorldMessages {
    private InWorldMessages() {
    }

    public static void send(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    public static Component status(String key, Object... args) {
        return Component.translatable("message.gregtech." + key, args);
    }
}
