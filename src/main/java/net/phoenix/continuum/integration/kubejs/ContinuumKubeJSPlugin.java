package net.phoenix.continuum.integration.kubejs;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.phoenix.continuum.api.ContinuumAPI;
import net.phoenix.continuum.api.ContinuumEvents;
import net.phoenix.continuum.common.visibility.ContinuumVisibilityEvent;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public class ContinuumKubeJSPlugin extends KubeJSPlugin {

    private static boolean bridged = false;

    @Override
    public void init() {
        if (bridged) return;
        bridged = true;
        net.phoenix.continuum.pdim.PdimWorldgenPack.scriptHook = ignored -> {
            if (ContinuumKubeEvents.PDIM_WORLDGEN.hasListeners()) {
                ContinuumKubeEvents.PDIM_WORLDGEN.post(ScriptType.STARTUP, new ContinuumKubeEvents.PdimWorldgenEvent());
            }
        };
        
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ContinuumVisibilityEvent.class, event -> {
            if (ContinuumKubeEvents.VISIBILITY.hasListeners()) {
                ContinuumKubeEvents.VISIBILITY.post(ScriptType.SERVER,
                        new ContinuumKubeEvents.VisibilityEvent(event));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ContinuumEvents.Discovery.class, event -> {
            if (ContinuumKubeEvents.DISCOVERY.hasListeners()) {
                ContinuumKubeEvents.DISCOVERY.post(ScriptType.SERVER, new ContinuumKubeEvents.DiscoveryEvent(event));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ContinuumEvents.AnomalyInvestigated.class, event -> {
            if (ContinuumKubeEvents.ANOMALY.hasListeners()) {
                ContinuumKubeEvents.ANOMALY.post(ScriptType.SERVER, new ContinuumKubeEvents.AnomalyEvent(event));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ContinuumEvents.PdimEntered.class, event -> {
            if (ContinuumKubeEvents.PDIM_ENTER.hasListeners()) {
                ContinuumKubeEvents.PDIM_ENTER.post(ScriptType.SERVER, new ContinuumKubeEvents.PdimEnterEvent(event));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ContinuumEvents.PdimLeft.class, event -> {
            if (ContinuumKubeEvents.PDIM_LEAVE.hasListeners()) {
                ContinuumKubeEvents.PDIM_LEAVE.post(ScriptType.SERVER, new ContinuumKubeEvents.PdimLeaveEvent(event));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ContinuumEvents.MissionChanged.class, event -> {
            if (ContinuumKubeEvents.MISSION.hasListeners()) {
                ContinuumKubeEvents.MISSION.post(ScriptType.SERVER, new ContinuumKubeEvents.MissionEvent(event));
            }
        });
    }

    @Override
    public void registerEvents() {
        ContinuumKubeEvents.GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType() == ScriptType.SERVER) event.add("Continuum", ContinuumAPI.class);
    }

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        filter.allow("net.phoenix.continuum.api");
        filter.allow("net.phoenix.continuum.data");
    }
}
