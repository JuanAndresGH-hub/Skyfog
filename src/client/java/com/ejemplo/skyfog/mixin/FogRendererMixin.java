package com.ejemplo.skyfog.mixin;

import com.ejemplo.skyfog.SkyFogConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ajusta el FogData que FogRenderer entrega antes de copiarlo al UBO de la GPU.
 */
@Mixin(value = FogRenderer.class, priority = 900)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void skyfog$apply(
        Camera camera,
        int renderDistanceInChunks,
        DeltaTracker deltaTracker,
        float darkenWorldAmount,
        ClientLevel level,
        CallbackInfoReturnable<FogData> callback
    ) {
        SkyFogConfig config = SkyFogConfig.get();
        if (!config.enabled() || !config.appliesTo(level)
            || camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) {
            return;
        }

        FogData fog = callback.getReturnValue();
        fog.environmentalStart = config.start();
        fog.environmentalEnd = config.end();
        fog.renderDistanceStart = config.start();
        fog.renderDistanceEnd = config.end();
        fog.skyEnd = config.end();
        fog.cloudEnd = config.end();
        fog.color = new Vector4f(config.effectiveRed(), config.effectiveGreen(), config.effectiveBlue(), 1.0F);
    }
}
