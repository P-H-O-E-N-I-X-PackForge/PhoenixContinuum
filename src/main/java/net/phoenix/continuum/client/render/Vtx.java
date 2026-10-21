package net.phoenix.continuum.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/** The shared tessellator, already begun: the one place the 1.20.1 buffer-builder idiom lives. */
public final class Vtx {

    private Vtx() {}

    public static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(mode, format);
        return builder;
    }
}
