package org.halfheart.dimensiondaddy.mixin;

import net.minecraft.entity.EntityCollisionHandler;
import org.halfheart.dimensiondaddy.DimensionDaddy;
import net.minecraft.block.BlockState;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void onNetherPortalCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean bl, CallbackInfo ci) {
        if (!DimensionDaddy.isNetherEnabled()) {
            ci.cancel();
            if (entity instanceof ServerPlayerEntity player) {
                player.sendMessage(
                        Text.literal("§cThe Nether dimension is currently disabled!"),
                        true
                );
            }
        }
    }
}