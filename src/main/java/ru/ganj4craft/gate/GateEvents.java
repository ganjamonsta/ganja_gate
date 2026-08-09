package ru.ganj4craft.gate;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Серверные события для логирования.
 */
public class GateEvents {

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        String name = event.getEntity().getName().getString();
        GanjaGate.LOGGER.info("[GanjaGate] Player {} entered the world (passed all checks)", name);
    }
}
