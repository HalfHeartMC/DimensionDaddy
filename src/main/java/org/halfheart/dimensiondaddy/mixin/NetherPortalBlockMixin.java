package org.halfheart.dimensiondaddy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.halfheart.dimensiondaddy.DimensionDaddy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void onNetherPortalCollision(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise, CallbackInfo ci) {
        if (!DimensionDaddy.isNetherEnabled()) {
            ci.cancel();
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(
                        Component.literal("§cThe Nether dimension is currently disabled!"),
                        true
                );
            }
            return;
        }
        if (!DimensionDaddy.isOverworldEnabled() && level.dimension().equals(Level.NETHER)) {
            ci.cancel();
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(
                        Component.literal("§cThe Overworld dimension is currently disabled!"),
                        true
                );
            }
        }
    }
}
