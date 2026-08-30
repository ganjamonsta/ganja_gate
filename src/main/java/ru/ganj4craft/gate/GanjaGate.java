package ru.ganj4craft.gate;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GanjaGate — защитный и клиентский мод для GanjaCraft.
 *
 * 1. Античит-верификация модов во время Configuration phase
 * 2. Кастомизация заголовка и иконки окна клиента
 */
@Mod(GanjaGate.MOD_ID)
public class GanjaGate {
    public static final String MOD_ID = "ganjagate";
    public static final Logger LOGGER = LoggerFactory.getLogger("GanjaGate");

    public GanjaGate(IEventBus modBus, ModContainer modContainer) {
        LOGGER.info("[GanjaGate] Initializing GanjaGate mod...");
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(GateNetwork::onRegisterPayloads);
        modBus.addListener(GateNetwork::onRegisterConfigurationTasks);
        NeoForge.EVENT_BUS.addListener(GateEvents::onPlayerLoggedIn);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientGateCustomizer.init(modBus);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        GateConfig.load();
        LOGGER.info("[GanjaGate] Whitelist loaded: {} mods allowed, strict={}", 
            GateConfig.getWhitelist().size(), GateConfig.isStrictMode());
    }
}
