package net.phoenix.continuum.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.data.ContinuumData;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ContinuumNodeNotes {

    private ContinuumNodeNotes() {}

    public static List<String> notes(ResourceLocation nodeId) {
        List<String> out = new ArrayList<>();
        for (var body : ContinuumData.allBodies()) {
            if (nodeId.equals(body.surveyedResearch())) out.add(ContinuumText.str("phoenix_continuum.continuum_node_notes.surveys", body.name()));
            else if (nodeId.equals(body.detectedResearch())) out.add(ContinuumText.str("phoenix_continuum.continuum_node_notes.detects", body.name()));
        }
        for (var system : ContinuumData.systems()) {
            if (nodeId.equals(system.surveyedResearch())) out.add(ContinuumText.str("phoenix_continuum.continuum_node_notes.surveys_the_system", system.name()));
            else if (nodeId.equals(system.detectedResearch())) {
                out.add(ContinuumText.str("phoenix_continuum.continuum_node_notes.detects_the_system", system.name()));
            }
        }
        return out;
    }
}
