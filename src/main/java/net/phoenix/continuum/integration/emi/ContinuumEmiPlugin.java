package net.phoenix.continuum.integration.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
public class ContinuumEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        ContinuumEmi.register(registry);
    }
}
