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
            if (nodeId.equals(body.surveyedResearch())) out.add("\u00a7b\u25ce Surveys " + body.name());
            else if (nodeId.equals(body.detectedResearch())) out.add("\u00a7e\u25ce Detects " + body.name());
        }
        for (var system : ContinuumData.systems()) {
            if (nodeId.equals(system.surveyedResearch())) out.add("\u00a7b\u25ce Surveys the " + system.name() + " system");
            else if (nodeId.equals(system.detectedResearch())) {
                out.add("\u00a7e\u25ce Detects the " + system.name() + " system");
            }
        }
        return out;
    }
}
