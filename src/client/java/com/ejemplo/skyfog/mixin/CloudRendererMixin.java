package com.ejemplo.skyfog.mixin;

import com.ejemplo.skyfog.SkyFogConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents clouds from drawing over the uniform sky.
 */
@Mixin(value = CloudRenderer.class, priority = 900)
public abstract class CloudRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyfog$hideClouds(
        int color,
        CloudStatus cloudStatus,
        float bottomY,
        int range,
        Vec3 cameraPosition,
        long gameTime,
        float partialTicks,
        CallbackInfo callback
    ) {
        SkyFogConfig config = SkyFogConfig.get();
        if (config.enabled() && config.appliesToCurrentDimension() && config.general.hideClouds) {
            callback.cancel();
        }
    }
}
