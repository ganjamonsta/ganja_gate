package ru.ganj4craft.gate;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Клиент → Сервер: "Вот мои моды"
 * Содержит nonce (эхо серверного) и список modId.
 */
public record ModListResponsePayload(long nonce, List<String> modIds) implements CustomPacketPayload {
    public static final Type<ModListResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GanjaGate.MOD_ID, "mod_list_response"));

    public static final StreamCodec<FriendlyByteBuf, ModListResponsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeLong(payload.nonce);
                buf.writeVarInt(payload.modIds.size());
                for (String modId : payload.modIds) {
                    buf.writeUtf(modId, 256);
                }
            },
            buf -> {
                long nonce = buf.readLong();
                int count = buf.readVarInt();
                List<String> mods = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    mods.add(buf.readUtf(256));
                }
                return new ModListResponsePayload(nonce, mods);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
