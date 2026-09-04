package ru.ganj4craft.gate.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.ganj4craft.gate.GanjaGate;

import java.util.Deque;

/**
 * Mixin into LevelRenderer to catch and drain leaked PoseStack matrices,
 * preventing client crashes with "java.lang.IllegalStateException: Pose stack not empty".
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    /**
     * Fallback safeguard: if any render step leaked matrices before checkPoseStack,
     * drain the stack back to the base level (size == 1) instead of crashing the client.
     */
    @Inject(method = "checkPoseStack", at = @At("HEAD"), cancellable = true)
    private void ganjagate$fixLeakedPoseStack(PoseStack poseStack, CallbackInfo ci) {
        Deque<PoseStack.Pose> deque = ((PoseStackAccessor) poseStack).ganjagate$getPoseStack();
        if (deque.size() > 1) {
            int leaked = deque.size() - 1;
            GanjaGate.LOGGER.warn("[GanjaGate] Leaked PoseStack detected in checkPoseStack (depth={}, leaked={})! Draining to base level to prevent crash.", deque.size(), leaked);
            while (deque.size() > 1) {
                try {
                    poseStack.popPose();
                } catch (Exception e) {
                    break;
                }
            }
            ci.cancel();
        } else if (deque.isEmpty()) {
            GanjaGate.LOGGER.warn("[GanjaGate] Empty PoseStack detected in checkPoseStack! Pushing identity matrix to restore base level.");
            poseStack.pushPose();
            ci.cancel();
        }
    }

    /**
     * Per-entity safeguard: drain leaked matrices immediately after the faulty entity renders.
     * This prevents subsequent entities in the frame from being rendered with distorted transforms.
     */
    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void ganjagate$afterRenderEntity(Entity entity, double camX, double camY, double camZ, float partialTick,
                                             PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        Deque<PoseStack.Pose> deque = ((PoseStackAccessor) poseStack).ganjagate$getPoseStack();
        if (deque.size() > 1) {
            int leaked = deque.size() - 1;
            String entityName = entity.getName() != null ? entity.getName().getString() : "unknown";
            String entityType = entity.getType() != null ? String.valueOf(entity.getType().getDescriptionId()) : "unknown";
            GanjaGate.LOGGER.warn("[GanjaGate] Entity '{}' ({}) leaked {} PoseStack matrices! Draining immediately to prevent visual glitches and crash.",
                    entityName, entityType, leaked);
            while (deque.size() > 1) {
                try {
                    poseStack.popPose();
                } catch (Exception e) {
                    break;
                }
            }
        }
    }
}
