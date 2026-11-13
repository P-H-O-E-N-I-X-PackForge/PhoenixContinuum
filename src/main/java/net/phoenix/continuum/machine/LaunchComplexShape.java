package net.phoenix.continuum.machine;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class LaunchComplexShape {

    private LaunchComplexShape() {}

    public static final String[][] SLICES = buildSlices();

    public static final List<Component> TOOLTIPS = List.of(
            ContinuumLang.tr("phoenix_continuum.launch_complex_shape.where_continuum_missions_are_launched"),
            ContinuumLang.tr("phoenix_continuum.launch_complex_shape.use_the_controller_to_open"),
            ContinuumLang.tr("phoenix_continuum.launch_complex_shape.its_tier_is_the_best"),
            ContinuumLang.tr("phoenix_continuum.launch_complex_shape.tier_and_every_launch_draws"),
            ContinuumLang.tr("phoenix_continuum.launch_complex_shape.rockets_probes_and_repair_kits"),
            ContinuumLang.tr("phoenix_continuum.launch_complex_shape.returned_rockets_and_hauled_resources"),
            ContinuumLang.tr("phoenix_continuum.launch_complex_shape.hatches_and_buses_go_anywhere"),
            ContinuumLang.tr("phoenix_continuum.launch_complex_shape.keep_the_1x1_shaft_in"));

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
