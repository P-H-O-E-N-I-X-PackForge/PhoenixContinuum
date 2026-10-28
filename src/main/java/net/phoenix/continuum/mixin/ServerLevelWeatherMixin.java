package net.phoenix.continuum.mixin;

import net.minecraft.server.level.ServerLevel;
import net.phoenix.continuum.pdim.PdimWeather;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A level other than the overworld takes its weather from the overworld's world data, so it cannot be made different.
 * A personal dimension whose spec has weather of its own runs its own cycle here instead of the vanilla one.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelWeatherMixin {

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true, remap = false)
    private void continuum$ownWeather(CallbackInfo ci) {
        if (PdimWeather.advance((ServerLevel) (Object) this)) ci.cancel();
    }
}
