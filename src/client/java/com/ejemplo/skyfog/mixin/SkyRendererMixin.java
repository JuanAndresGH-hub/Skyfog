package com.ejemplo.skyfog.mixin;

import com.ejemplo.skyfog.SkyFogConfig;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the sky disc use the fog color and removes sky elements.
 */
@Mixin(value = SkyRenderer.class, priority = 900)
public abstract class SkyRendererMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void skyfog$matchSkyColor(
        net.minecraft.client.multiplayer.ClientLevel level,
        float partialTicks,
        net.minecraft.client.Camera camera,
        SkyRenderState state,
        CallbackInfo callback
    ) {
        SkyFogConfig config = SkyFogConfig.get();
        if (config.enabled() && config.appliesTo(level) && config.general.skyMatchesFog) {
            state.skyColor = config.effectiveColorArgb();
        }
        if (config.enabled() && config.appliesTo(level)) {
            state.sunriseAndSunsetColor = 0;
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyfog$hideSun(float rainBrightness, PoseStack poseStack, CallbackInfo callback) {
        SkyFogConfig config = SkyFogConfig.get();
        if (config.enabled() && config.appliesToCurrentDimension() && config.general.hideSun) {
            callback.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyfog$hideMoon(net.minecraft.world.level.MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo callback) {
        SkyFogConfig config = SkyFogConfig.get();
        if (config.enabled() && config.appliesToCurrentDimension() && config.general.hideMoon) {
            callback.cancel();
        }
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyfog$hideStars(float starBrightness, PoseStack poseStack, CallbackInfo callback) {
        SkyFogConfig config = SkyFogConfig.get();
        if (config.enabled() && config.appliesToCurrentDimension() && config.general.hideStars) {
            callback.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyfog$hideSunrise(PoseStack poseStack, float sunAngle, int color, CallbackInfo callback) {
        SkyFogConfig config = SkyFogConfig.get();
        if (config.enabled() && config.appliesToCurrentDimension()) {
            callback.cancel();
        }
    }
}
