package org.halfheart.dimensiondaddy.mixin;

import net.minecraft.entity.EntityCollisionHandler;
import org.halfheart.dimensiondaddy.DimensionDaddy;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void onEndPortalCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean bl, CallbackInfo ci) {
        if (!DimensionDaddy.isEndEnabled()) {
            ci.cancel();
            if (entity instanceof ServerPlayerEntity player) {
                player.sendMessage(
                        Text.literal("§cThe End dimension is currently disabled!"),
                        true
                );
            }
        }
    }
}