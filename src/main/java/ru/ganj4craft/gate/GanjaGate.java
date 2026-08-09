package ru.ganj4craft.gate;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GanjaGate — серверный античит-мод для GanjaCraft.
 *
 * При подключении игрока:
 * 1. Отправляет клиенту запрос на список модов (configuration phase)
 * 2. Клиент отвечает полным списком modId
 * 3. Сервер сверяет со своим whitelist
 * 4. Если есть лишние моды — кик до входа в мир
 */
@Mod(GanjaGate.MOD_ID)
public class GanjaGate {
    public static final String MOD_ID = "ganjagate";
    public static final Logger LOGGER = LoggerFactory.getLogger("GanjaGate");

    public GanjaGate(IEventBus modBus, ModContainer modContainer) {
        LOGGER.info("[GanjaGate] Initializing server-side mod guard...");
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(GateNetwork::onRegisterPayloads);
        modBus.addListener(GateNetwork::onRegisterConfigurationTasks);
        NeoForge.EVENT_BUS.addListener(GateEvents::onPlayerLoggedIn);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        GateConfig.load();
        LOGGER.info("[GanjaGate] Whitelist loaded: {} mods allowed, strict={}", 
            GateConfig.getWhitelist().size(), GateConfig.isStrictMode());
    }
}
