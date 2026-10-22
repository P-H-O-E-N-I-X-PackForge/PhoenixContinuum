package net.phoenix.continuum.machine;

import net.minecraft.network.chat.Component;

import java.util.List;

/** The launch complex's footprint and tooltips, the same on every GregTech version. */
public final class LaunchComplexShape {

    private LaunchComplexShape() {}

    public static final String[][] SLICES = buildSlices();

    public static final List<Component> TOOLTIPS = List.of(
            Component.literal("§6Where Continuum missions are launched from."),
            Component.literal("§7Use the controller to open the map and launch."),
            Component.literal("§7Its tier is the best energy input hatch it has; farther bodies need a higher"),
            Component.literal("§7tier, and every launch draws power from the hatches."),
            Component.literal("§7Rockets, probes and repair kits are taken from its item input buses;"),
            Component.literal("§7returned rockets and hauled resources are delivered to its item output buses."),
            Component.literal("§7Hatches and buses go anywhere on the slab's edge (not the corners)."),
            Component.literal("§7Keep the 1x1 shaft in the middle of the gantry clear for the rocket."));

    private static String[][] buildSlices() {
        int depth = 7;
        int width = 7;
        int height = 6;
        String[][] slices = new String[depth][height];

        for (int s = 0; s < depth; s++) {
            for (int y = 0; y < height; y++) {
                StringBuilder row = new StringBuilder();
                for (int c = 0; c < width; c++) row.append(cell(s, c, y));
                slices[s][y] = row.toString();
            }
        }
        return slices;
    }

    private static char cell(int s, int c, int y) {
        boolean outerCorner = (s == 0 || s == 6) && (c == 0 || c == 6);
        boolean innerRing = s >= 2 && s <= 4 && c >= 2 && c <= 4 && !(s == 3 && c == 3);
        boolean innerCorner = (s == 2 || s == 4) && (c == 2 || c == 4);
        boolean shaft = s == 3 && c == 3;

        if (y == 0) {
            if (s == 6 && c == 3) return 'S';
            boolean edge = s == 0 || s == 6 || c == 0 || c == 6;
            boolean corner = (s == 0 || s == 6) && (c == 0 || c == 6);
            return edge && !corner ? 'H' : 'B';
        }
        if (shaft && y <= 5) return 'A';
        if (y >= 1 && y <= 4 && outerCorner) return 'F';
        if (y == 5 && (s == 0 || s == 6 || c == 0 || c == 6)) return 'F';
        if (innerRing && y >= 1 && y <= 3 && innerCorner) return 'F';
        if (innerRing && y == 4) return 'F';
        return ' ';
    }

}
