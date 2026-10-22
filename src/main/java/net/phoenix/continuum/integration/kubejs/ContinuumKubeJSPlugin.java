package net.phoenix.continuum.integration.kubejs;

import net.neoforged.neoforge.common.NeoForge;
import net.phoenix.continuum.api.ContinuumAPI;
import net.phoenix.continuum.api.ContinuumEvents;
import net.phoenix.continuum.common.visibility.ContinuumVisibilityEvent;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;

public class ContinuumKubeJSPlugin implements KubeJSPlugin {

    private static boolean bridged = false;

    @Override
    public void init() {
        if (bridged) return;
        bridged = true;
        
        NeoForge.EVENT_BUS.addListener(ContinuumVisibilityEvent.class, event -> {
            if (ContinuumKubeEvents.VISIBILITY.hasListeners()) {
                ContinuumKubeEvents.VISIBILITY.post(ScriptType.SERVER,
                        new ContinuumKubeEvents.VisibilityEvent(event));
            }
        });
        NeoForge.EVENT_BUS.addListener(ContinuumEvents.Discovery.class, event -> {
            if (ContinuumKubeEvents.DISCOVERY.hasListeners()) {
                ContinuumKubeEvents.DISCOVERY.post(ScriptType.SERVER, new ContinuumKubeEvents.DiscoveryEvent(event));
            }
        });
        NeoForge.EVENT_BUS.addListener(ContinuumEvents.AnomalyInvestigated.class, event -> {
            if (ContinuumKubeEvents.ANOMALY.hasListeners()) {
                ContinuumKubeEvents.ANOMALY.post(ScriptType.SERVER, new ContinuumKubeEvents.AnomalyEvent(event));
            }
        });
        NeoForge.EVENT_BUS.addListener(ContinuumEvents.MissionChanged.class, event -> {
            if (ContinuumKubeEvents.MISSION.hasListeners()) {
                ContinuumKubeEvents.MISSION.post(ScriptType.SERVER, new ContinuumKubeEvents.MissionEvent(event));
            }
        });
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(ContinuumKubeEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type() == ScriptType.SERVER) bindings.add("Continuum", ContinuumAPI.class);
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow("net.phoenix.continuum.api");
        filter.allow("net.phoenix.continuum.data");
    }
}
