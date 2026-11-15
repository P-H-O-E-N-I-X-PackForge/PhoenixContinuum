package net.phoenix.continuum.mixin;

import net.minecraft.server.level.ServerLevel;
import net.phoenix.continuum.pdim.PdimWeather;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelWeatherMixin {

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true, remap = false)
    private void continuum$ownWeather(CallbackInfo ci) {
        if (PdimWeather.advance((ServerLevel) (Object) this)) ci.cancel();
    }
}
