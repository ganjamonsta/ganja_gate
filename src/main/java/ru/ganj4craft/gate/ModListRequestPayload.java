package ru.ganj4craft.gate;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер → Клиент: "Пришли мне список своих модов"
 * Содержит nonce для защиты от replay-атак.
 */
public record ModListRequestPayload(long nonce) implements CustomPacketPayload {
    public static final Type<ModListRequestPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(GanjaGate.MOD_ID, "mod_list_request"));

    public static final StreamCodec<FriendlyByteBuf, ModListRequestPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeLong(payload.nonce),
            buf -> new ModListRequestPayload(buf.readLong())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
