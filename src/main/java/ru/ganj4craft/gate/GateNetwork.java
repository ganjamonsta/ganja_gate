package ru.ganj4craft.gate;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.fml.ModList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Сетевой протокол GanjaGate.
 *
 * Работает в CONFIGURATION фазе (до входа игрока в мир):
 * 1. Сервер отправляет ModListRequestPayload с nonce
 * 2. Клиент отвечает ModListResponsePayload со списком modId
 * 3. Сервер проверяет и кикает если есть лишние моды
 */
public class GateNetwork {

    /** Хранилище активных nonce для верификации (защита от подделки) */
    private static final Map<Long, String> activeNonces = new ConcurrentHashMap<>();

    // ── Регистрация пакетов ─────────────────────────────────────────────

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(GanjaGate.MOD_ID)
            .optional(); // Делаем OPTIONAL чтобы клиент без мода мог подключиться
                         // (будем обрабатывать отсутствие ответа отдельно)

        // Configuration phase payloads
        registrar.configurationToClient(
            ModListRequestPayload.TYPE,
            ModListRequestPayload.STREAM_CODEC,
            GateNetwork::handleRequestOnClient
        );

        registrar.configurationToServer(
            ModListResponsePayload.TYPE,
            ModListResponsePayload.STREAM_CODEC,
            GateNetwork::handleResponseOnServer
        );

        GanjaGate.LOGGER.info("[GanjaGate] Network payloads registered");
    }

    // ── Configuration Task ──────────────────────────────────────────────

    public static void onRegisterConfigurationTasks(RegisterConfigurationTasksEvent event) {
        event.register(new ModListCheckTask());
        GanjaGate.LOGGER.debug("[GanjaGate] Configuration task registered");
    }

    /**
     * Configuration task: отправляет запрос на список модов при подключении.
     */
    public record ModListCheckTask() implements ICustomConfigurationTask {
        public static final Type TYPE = new Type(GanjaGate.MOD_ID + ":mod_list_check");

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            long nonce = ThreadLocalRandom.current().nextLong();
            activeNonces.put(nonce, "pending");
            sender.accept(new ModListRequestPayload(nonce));
            GanjaGate.LOGGER.debug("[GanjaGate] Sent mod list request with nonce {}", nonce);
        }

        @Override
        public Type type() {
            return TYPE;
        }
    }

    // ── Обработчики пакетов ─────────────────────────────────────────────

    /**
     * Клиентская сторона: получили запрос — отправляем свой список модов.
     */
    private static void handleRequestOnClient(ModListRequestPayload payload, IPayloadContext context) {
        List<String> myMods = new ArrayList<>();
        ModList.get().getMods().forEach(modInfo -> {
            myMods.add(modInfo.getModId());
        });

        GanjaGate.LOGGER.debug("[GanjaGate] Server requested mod list, sending {} mods", myMods.size());
        context.reply(new ModListResponsePayload(payload.nonce(), myMods));
    }

    private static String extractPlayerName(IPayloadContext context) {
        try {
            Object listener = context.listener();
            if (listener == null) return "Unknown";

            Class<?> clazz = listener.getClass();
            for (String methodName : List.of("owner", "getOwner", "gameProfile", "getGameProfile")) {
                try {
                    var method = clazz.getMethod(methodName);
                    Object profile = method.invoke(listener);
                    if (profile != null) {
                        var getNameMethod = profile.getClass().getMethod("getName");
                        Object name = getNameMethod.invoke(profile);
                        if (name != null) return name.toString();
                    }
                } catch (Exception ignored) {}
            }
            for (String fieldName : List.of("owner", "gameProfile", "profile")) {
                try {
                    var field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object profile = field.get(listener);
                    if (profile != null) {
                        var getNameMethod = profile.getClass().getMethod("getName");
                        Object name = getNameMethod.invoke(profile);
                        if (name != null) return name.toString();
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    /**
     * Серверная сторона: получили ответ — проверяем моды.
     */
    private static void handleResponseOnServer(ModListResponsePayload payload, IPayloadContext context) {
        String playerName = extractPlayerName(context);

        // Верификация nonce
        String removed = activeNonces.remove(payload.nonce());
        if (removed == null) {
            GanjaGate.LOGGER.warn("[GanjaGate] Received response with unknown nonce {} from {}", 
                payload.nonce(), playerName);
            context.disconnect(Component.literal("§c[GanjaGate] Invalid verification nonce."));
            return;
        }

        List<String> clientMods = payload.modIds();
        List<String> forbidden = GateConfig.checkClientMods(clientMods);

        if (forbidden.isEmpty()) {
            GanjaGate.LOGGER.info("[GanjaGate] ✓ {} passed mod check ({} mods)", playerName, clientMods.size());
        } else {
            GanjaGate.LOGGER.warn("[GanjaGate] ✗ {} has {} forbidden mods: {}", 
                playerName, forbidden.size(), forbidden);

            if (GateConfig.isStrictMode()) {
                String modsStr = String.join(", ", forbidden);
                String msg = GateConfig.getKickMessage().replace("{mods}", modsStr);
                // Заменяем \n на реальные переводы строк
                msg = msg.replace("\\n", "\n");
                context.disconnect(Component.literal(msg));
                GanjaGate.LOGGER.info("[GanjaGate] Kicked {} for forbidden mods: {}", playerName, forbidden);
                return;
            }
        }

        // Завершаем configuration task
        context.finishCurrentTask(ModListCheckTask.TYPE);
    }
}
