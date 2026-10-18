package gregtech.neo.runtime;

import gregtech.neo.GT6Neo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Opens the prepared runtime-test save after the client has finished loading.
 */
public final class ClientRuntimeSelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("gregtech.runtimeSelfTest");
    private static boolean opened;
    private static int waitTicks;

    private ClientRuntimeSelfTest() {
    }

    public static void install() {
        if (ENABLED) {
            NeoForge.EVENT_BUS.addListener(ClientRuntimeSelfTest::onClientTick);
            GT6Neo.LOGGER.info("GT6 runtime self-test: client automation armed");
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (opened) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (++waitTicks == 1) {
            GT6Neo.LOGGER.info("GT6 runtime self-test: initial screen={}", minecraft.screen);
        }
        if (waitTicks < 60) {
            return;
        }
        if (minecraft.screen instanceof TitleScreen) {
            opened = true;
            GT6Neo.LOGGER.info("GT6 runtime self-test: opening save 'world'");
            minecraft.createWorldOpenFlows().openWorld("world", () -> {
                GT6Neo.LOGGER.error("GT6 runtime self-test: failed to open save 'world'");
            });
        }
    }
}
