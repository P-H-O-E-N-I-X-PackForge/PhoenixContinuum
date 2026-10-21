package net.phoenix.continuum.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Draws Continuum's quad-based shaders offscreen with the real driver: writes a PNG of each so it can be looked at, and
 * times each one covering the whole target (the worst case) with a GPU timer query.
 * args: shaderDir outDir width height [filter]
 */
public class ShaderBench {

    static final String[] QUAD = {"wolfrayet", "pulsar", "protostar", "planetary", "megastructure", "dyson", "tidal", "blackhole",
            "nebula", "galaxy", "backdrop", "sky", "starfield", "glow"};

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int w = Integer.parseInt(args[2]);
        int h = Integer.parseInt(args[3]);
        String filter = args.length > 4 ? args[4] : "";
        Files.createDirectories(out);

        GLFW.glfwInit();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(64, 64, "bench", 0, 0);
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL " + GL33C.glGetString(GL33C.GL_VERSION) + " / " + GL33C.glGetString(GL33C.GL_RENDERER)
                + "   target " + w + "x" + h);

        int fbo = GL33C.glGenFramebuffers();
        int tex = GL33C.glGenTextures();
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, tex);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, w, h, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, 0L);
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, fbo);
        GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, tex, 0);
        GL33C.glViewport(0, 0, w, h);

        int dummy = GL33C.glGenTextures();
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, dummy);
        ByteBuffer white = BufferUtils.createByteBuffer(4 * 4 * 4);
        for (int i = 0; i < 64; i++) white.put((byte) (i % 4 == 3 ? 255 : 90));
        white.flip();
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, 4, 4, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, white);

        int vao = GL33C.glGenVertexArrays();
        GL33C.glBindVertexArray(vao);
        int vbo = GL33C.glGenBuffers();
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
        FloatBuffer quad = BufferUtils.createFloatBuffer(18);
        quad.put(new float[] {-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 1, 0}).flip();
        GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, quad, GL33C.GL_STATIC_DRAW);
        GL33C.glEnableVertexAttribArray(0);
        GL33C.glVertexAttribPointer(0, 3, GL33C.GL_FLOAT, false, 12, 0L);

        GL33C.glEnable(GL33C.GL_BLEND);
        GL33C.glBlendFunc(GL33C.GL_ONE, GL33C.GL_ONE);

        System.out.printf(Locale.ROOT, "%-16s %10s %10s%n", "shader", "ms/frame", "ms per Mpx");
        for (String name : QUAD) {
            if (!name.contains(filter)) continue;
            String base = "continuum_" + name;
            JsonObject json = JsonParser.parseString(Files.readString(dir.resolve(base + ".json"))).getAsJsonObject();
            int program = build(Files.readString(dir.resolve(base + ".vsh")), Files.readString(dir.resolve(base + ".fsh")));
            if (program == 0) {
                System.out.println("FAIL " + name);
                continue;
            }
            GL33C.glUseProgram(program);
            applyDefaults(program, json);
            overrides(program, name);

            // look at it (the star-wrapping shaders blend over a star)
            boolean premult = name.equals("megastructure") || name.equals("dyson");
            GL33C.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT);
            if (premult) {
                GL33C.glBlendFunc(GL33C.GL_ONE, GL33C.GL_ONE_MINUS_SRC_ALPHA);
                int disc = build(Files.readString(dir.resolve("continuum_megastructure.vsh")),
                        "#version 150\nuniform float Extent;\nin vec2 vLocal;\nout vec4 o;\nvoid main(){ float r = length(vLocal * Extent); o = r < 1.0 ? vec4(1.0, 0.75 - 0.2 * r, 0.35, 1.0) : vec4(0.0); }");
                GL33C.glUseProgram(disc);
                applyDefaults(disc, json);
                f(disc, "Extent", name.equals("dyson") ? 7.0f : 16.0f);
                GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
                save(out.resolve(name + "_disc.png").toFile(), w, h);
                GL33C.glUseProgram(program);
            }
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            save(out.resolve(name + ".png").toFile(), w, h);

            GL33C.glBlendFunc(GL33C.GL_ONE, GL33C.GL_ONE);
            // time it covering the whole target
            for (int i = 0; i < 40; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            GL33C.glFinish();
            int frames = 30;
            int q = GL33C.glGenQueries();
            GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, q);
            for (int i = 0; i < frames; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
            long ns = GL33C.glGetQueryObjectui64(q, GL33C.GL_QUERY_RESULT);
            double ms = ns / 1.0e6 / frames;
            System.out.printf(Locale.ROOT, "%-16s %10.3f %10.3f%n", name, ms, ms / (w * (double) h / 1.0e6));
        }
        if (filter.isEmpty() || "backdrop".contains(filter)) backdrops(dir, out, w, h, fbo, vao);
        if ("planet".contains(filter) || filter.isEmpty()) planets(dir, out, w, h);
        GLFW.glfwTerminate();
    }

    /** The nebula backdrops with and without the baked picture: the same frame both ways, and what each costs. */
    static void backdrops(Path dir, Path out, int w, int h, int mainFbo, int quadVao) throws Exception {
        int bake = build(Files.readString(dir.resolve("continuum_nebula_bake.vsh")), Files.readString(dir.resolve("continuum_nebula_bake.fsh")));
        if (bake == 0) return;
        int bw = 2048, bh = 1024;
        int bakeTex = GL33C.glGenTextures();
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, bakeTex);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, bw, bh, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, 0L);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_REPEAT);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        int bakeFbo = GL33C.glGenFramebuffers();
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, bakeFbo);
        GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, bakeTex, 0);
        GL33C.glViewport(0, 0, bw, bh);
        GL33C.glDisable(GL33C.GL_BLEND);
        JsonObject bj = JsonParser.parseString(Files.readString(dir.resolve("continuum_nebula_bake.json"))).getAsJsonObject();
        GL33C.glUseProgram(bake);
        applyDefaults(bake, bj);
        f(bake, "Octaves", 5.0f);
        f(bake, "PrimaryColor", 0.42f, 0.2f, 0.85f);
        f(bake, "SecondaryColor", 0.1f, 0.35f, 0.9f);
        GL33C.glFinish();
        int q = GL33C.glGenQueries();
        GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, q);
        GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
        GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        System.out.printf(Locale.ROOT, "%n%-24s %10.3f   (one-off, %dx%d)%n", "nebula bake", GL33C.glGetQueryObjectui64(q, GL33C.GL_QUERY_RESULT) / 1.0e6, bw, bh);
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, mainFbo);
        GL33C.glViewport(0, 0, w, h);

        String[][] variants = {{"sky", "0"}, {"sky", "1"}, {"galaxy", "0"}, {"galaxy", "1"}};
        for (String[] v : variants) {
            String name = v[0];
            boolean baked = v[1].equals("1");
            String base = "continuum_" + name;
            JsonObject json = JsonParser.parseString(Files.readString(dir.resolve(base + ".json"))).getAsJsonObject();
            int program = build(Files.readString(dir.resolve(base + ".vsh")), Files.readString(dir.resolve(base + ".fsh")));
            if (program == 0) continue;
            GL33C.glUseProgram(program);
            applyDefaults(program, json);
            f(program, "Time", 3.0f);
            f(program, "Octaves", 5.0f);
            f(program, "PrimaryColor", 0.42f, 0.2f, 0.85f);
            f(program, "SecondaryColor", 0.1f, 0.35f, 0.9f);
            f(program, "Aspect", w / (float) h);
            f(program, "Mode", name.equals("galaxy") ? 1.0f : 0.0f);
            f(program, "UseBake", baked ? 1.0f : 0.0f);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, bakeTex);
            GL33C.glBindVertexArray(quadVao);
            GL33C.glDisable(GL33C.GL_BLEND);
            GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            String label = (name.equals("galaxy") ? "map_backdrop" : "sky") + (baked ? "_baked" : "_live");
            save(out.resolve(label + ".png").toFile(), w, h);
            for (int i = 0; i < 40; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            GL33C.glFinish();
            int tq = GL33C.glGenQueries();
            GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, tq);
            for (int i = 0; i < 30; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
            double ms = GL33C.glGetQueryObjectui64(tq, GL33C.GL_QUERY_RESULT) / 1.0e6 / 30;
            System.out.printf(Locale.ROOT, "%-24s %10.3f %10.3f%n", label, ms, ms / (w * (double) h / 1.0e6));
        }
        GL33C.glEnable(GL33C.GL_BLEND);
        GL33C.glBlendFunc(GL33C.GL_ONE, GL33C.GL_ONE);
    }

    /** The planet shader on a sphere filling the target's height (the worst case), a few looks and octave counts. */
    static void planets(Path dir, Path out, int w, int h) throws Exception {
        int program = build(Files.readString(dir.resolve("continuum_planet.vsh")), Files.readString(dir.resolve("continuum_planet.fsh")));
        int atmo = build(Files.readString(dir.resolve("continuum_planet.vsh")), Files.readString(dir.resolve("continuum_atmosphere.fsh")));
        if (program == 0 || atmo == 0) return;
        int stacks = 96, slices = 192;
        FloatBuffer verts = BufferUtils.createFloatBuffer(stacks * slices * 6 * 3);
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                float[][] c = new float[4][];
                int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int k = 0; k < 4; k++) {
                    double th = Math.PI * ij[k][0] / stacks, ph = 2 * Math.PI * ij[k][1] / slices;
                    c[k] = new float[] {(float) (Math.sin(th) * Math.cos(ph)), (float) Math.cos(th), (float) (Math.sin(th) * Math.sin(ph))};
                }
                for (int k : new int[] {0, 1, 2, 0, 2, 3}) verts.put(c[k]);
            }
        }
        verts.flip();
        int vao = GL33C.glGenVertexArrays();
        GL33C.glBindVertexArray(vao);
        int vbo = GL33C.glGenBuffers();
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
        GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, verts, GL33C.GL_STATIC_DRAW);
        GL33C.glEnableVertexAttribArray(0);
        GL33C.glVertexAttribPointer(0, 3, GL33C.GL_FLOAT, false, 12, 0L);
        int count = stacks * slices * 6;
        GL33C.glDisable(GL33C.GL_BLEND);
        GL33C.glEnable(GL33C.GL_DEPTH_TEST);
        GL33C.glDisable(GL33C.GL_CULL_FACE);

        // sphere of radius 1 at z=-3, 40 degree vertical fov: covers about 0.6 of the height
        float aspect = w / (float) h;
        float f = (float) (1.0 / Math.tan(Math.toRadians(40) / 2));
        float near = 0.1f, far = 20f;
        float[] proj = {f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) / (near - far), -1, 0, 0, 2 * far * near / (near - far), 0};
        float[] mv = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, -1.9f, 1};
        double discPx = Math.PI * Math.pow(h * f / Math.sqrt(1.9 * 1.9 - 1) / 2.0, 2);
        System.out.println();
        System.out.printf(Locale.ROOT, "%-24s %10s %10s   (sphere covers about %.2f Mpx)%n", "planet", "ms/frame", "ms per Mpx", discPx / 1e6);
        Object[][] looks = {
                {"terrain o6", 0f, 6f, 0.4f, 0.5f, 0f},
                {"terrain o4", 0f, 4f, 0.4f, 0.5f, 0f},
                {"terrain o3", 0f, 3f, 0.4f, 0.5f, 0f},
                {"terrain o6 no clouds", 0f, 6f, 0.4f, 0f, 0f},
                {"gas giant", 1f, 6f, 0f, 0f, 0f},
                {"gas storm", 1f, 6f, 0f, 0f, 3f},
                {"lava veins", 0f, 6f, 0.2f, 0f, 2f},
                {"crystal", 0f, 6f, 0.2f, 0f, 1f},
                {"chrome", 0f, 6f, 0.2f, 0f, 4f}
        };
        for (Object[] look : looks) {
            GL33C.glUseProgram(program);
            applyDefaults(program, JsonParser.parseString(Files.readString(dir.resolve("continuum_planet.json"))).getAsJsonObject());
            int loc = GL33C.glGetUniformLocation(program, "ModelViewMat");
            GL33C.glUniformMatrix4fv(loc, false, mv);
            GL33C.glUniformMatrix4fv(GL33C.glGetUniformLocation(program, "ProjMat"), false, proj);
            f(program, "Time", 3f);
            f(program, "SunDir", 0.6f, 0.3f, 0.75f);
            f(program, "Seed", 4.2f);
            f(program, "Scale", 2.5f);
            f(program, "Kind", (Float) look[1]);
            f(program, "Octaves", (Float) look[2]);
            f(program, "OceanLevel", (Float) look[3]);
            f(program, "CloudCover", (Float) look[4]);
            f(program, "Style", (Float) look[5]);
            f(program, "PolarIce", 0.3f);
            f(program, "PalLow", 0.2f, 0.3f, 0.15f);
            f(program, "PalMid", 0.5f, 0.45f, 0.3f);
            f(program, "PalHigh", 0.9f, 0.9f, 0.9f);
            f(program, "EmissiveAmount", (Float) look[5] == 2f ? 1f : 0f);
            GL33C.glClearColor(0f, 0f, 0f, 1f);
            GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_DEPTH_BUFFER_BIT);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, count);
            save(out.resolve("planet_" + look[0].toString().replace(' ', '_') + ".png").toFile(), w, h);
            for (int i = 0; i < 40; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, count);
            GL33C.glFinish();
            int frames = 20;
            int q = GL33C.glGenQueries();
            GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, q);
            for (int i = 0; i < frames; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, count);
            GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
            double ms = GL33C.glGetQueryObjectui64(q, GL33C.GL_QUERY_RESULT) / 1.0e6 / frames;
            System.out.printf(Locale.ROOT, "%-24s %10.3f %10.3f%n", look[0], ms, ms / (discPx / 1e6));
        }
        // atmosphere shell
        GL33C.glUseProgram(atmo);
        GL33C.glUniformMatrix4fv(GL33C.glGetUniformLocation(atmo, "ModelViewMat"), false, mv);
        GL33C.glUniformMatrix4fv(GL33C.glGetUniformLocation(atmo, "ProjMat"), false, proj);
        f(atmo, "SunDir", 0.6f, 0.3f, 0.75f);
        f(atmo, "AtmoColor", 0.4f, 0.6f, 1f);
        f(atmo, "AtmoDensity", 1f);
        GL33C.glEnable(GL33C.GL_BLEND);
        GL33C.glBlendFunc(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA);
        for (int i = 0; i < 40; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, count);
        GL33C.glFinish();
        int q = GL33C.glGenQueries();
        GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, q);
        for (int i = 0; i < 20; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, count);
        GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        double ms = GL33C.glGetQueryObjectui64(q, GL33C.GL_QUERY_RESULT) / 1.0e6 / 20;
        System.out.printf(Locale.ROOT, "%-24s %10.3f %10.3f%n", "atmosphere shell", ms, ms / (discPx / 1e6));

        // a ringed planet: the sun on the same face of the ring as the viewer, then on the other face
        int ringProg = build(Files.readString(dir.resolve("continuum_ring.vsh")), Files.readString(dir.resolve("continuum_ring.fsh")));
        if (ringProg == 0) return;
        int rvao = GL33C.glGenVertexArrays();
        GL33C.glBindVertexArray(rvao);
        int rvbo = GL33C.glGenBuffers();
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, rvbo);
        FloatBuffer rq = BufferUtils.createFloatBuffer(18);
        rq.put(new float[] {-1, 0, -1, -1, 0, 1, 1, 0, 1, -1, 0, -1, 1, 0, 1, 1, 0, -1}).flip();
        GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, rq, GL33C.GL_STATIC_DRAW);
        GL33C.glEnableVertexAttribArray(0);
        GL33C.glVertexAttribPointer(0, 3, GL33C.GL_FLOAT, false, 12, 0L);
        float[][] suns = {{0.55f, 0.55f, 0.65f}, {0.55f, -0.55f, 0.65f}, {0.9f, 0.1f, 0.3f}};
        float[] tilts = {62f, 62f, 20f};
        for (int k = 0; k < suns.length; k++) {
            float[] sun = suns[k];
            float c = (float) Math.cos(Math.toRadians(tilts[k])), sn = (float) Math.sin(Math.toRadians(tilts[k]));
            float R = 2.4f;
            float[] ringMv = {R, 0, 0, 0, 0, R * c, R * sn, 0, 0, -R * sn, R * c, 0, 0, 0, -4.5f, 1};
            float[] planetMv = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, -4.5f, 1};
            GL33C.glClearColor(0f, 0f, 0.01f, 1f);
            GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_DEPTH_BUFFER_BIT);
            GL33C.glBindVertexArray(vao);
            GL33C.glDisable(GL33C.GL_BLEND);
            GL33C.glUseProgram(program);
            GL33C.glUniformMatrix4fv(GL33C.glGetUniformLocation(program, "ModelViewMat"), false, planetMv);
            GL33C.glUniformMatrix4fv(GL33C.glGetUniformLocation(program, "ProjMat"), false, proj);
            f(program, "SunDir", sun[0], sun[1], sun[2]);
            f(program, "Kind", 1f);
            f(program, "Style", 0f);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, count);
            GL33C.glBindVertexArray(rvao);
            GL33C.glUseProgram(ringProg);
            GL33C.glUniformMatrix4fv(GL33C.glGetUniformLocation(ringProg, "ModelViewMat"), false, ringMv);
            GL33C.glUniformMatrix4fv(GL33C.glGetUniformLocation(ringProg, "ProjMat"), false, proj);
            f(ringProg, "SunDir", sun[0], sun[1], sun[2]);
            f(ringProg, "PlanetCenter", 0f, 0f, -4.5f);
            f(ringProg, "PlanetRadius", 1f);
            f(ringProg, "Inner", 1.4f / 2.4f);
            f(ringProg, "Opacity", 0.9f);
            f(ringProg, "Seed", 4.2f);
            f(ringProg, "Color1", 0.85f, 0.78f, 0.62f);
            f(ringProg, "Color2", 0.54f, 0.48f, 0.38f);
            GL33C.glEnable(GL33C.GL_BLEND);
            GL33C.glBlendFunc(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA);
            GL33C.glDepthMask(false);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            GL33C.glDepthMask(true);
            save(out.resolve("ring_" + k + ".png").toFile(), w, h);
            for (int i = 0; i < 40; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            GL33C.glFinish();
            int rq2 = GL33C.glGenQueries();
            GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, rq2);
            for (int i = 0; i < 20; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
            System.out.printf(Locale.ROOT, "%-24s %10.3f%n", "ring " + k, GL33C.glGetQueryObjectui64(rq2, GL33C.GL_QUERY_RESULT) / 1.0e6 / 20);
        }
    }

    static int build(String vsh, String fsh) {
        int v = compile(GL33C.GL_VERTEX_SHADER, vsh);
        int f = compile(GL33C.GL_FRAGMENT_SHADER, fsh);
        if (v == 0 || f == 0) return 0;
        int p = GL33C.glCreateProgram();
        GL33C.glAttachShader(p, v);
        GL33C.glAttachShader(p, f);
        GL33C.glBindAttribLocation(p, 0, "Position");
        GL33C.glLinkProgram(p);
        if (GL33C.glGetProgrami(p, GL33C.GL_LINK_STATUS) != GL33C.GL_TRUE) {
            System.out.println(GL33C.glGetProgramInfoLog(p));
            return 0;
        }
        return p;
    }

    static int compile(int type, String src) {
        int s = GL33C.glCreateShader(type);
        GL33C.glShaderSource(s, src);
        GL33C.glCompileShader(s);
        if (GL33C.glGetShaderi(s, GL33C.GL_COMPILE_STATUS) != GL33C.GL_TRUE) {
            System.out.println(GL33C.glGetShaderInfoLog(s));
            return 0;
        }
        return s;
    }

    static void applyDefaults(int program, JsonObject json) {
        JsonArray uniforms = json.getAsJsonArray("uniforms");
        if (uniforms == null) return;
        for (JsonElement e : uniforms) {
            JsonObject u = e.getAsJsonObject();
            String name = u.get("name").getAsString();
            int loc = GL33C.glGetUniformLocation(program, name);
            if (loc < 0) continue;
            String type = u.get("type").getAsString();
            JsonArray values = u.getAsJsonArray("values");
            float[] v = new float[values.size()];
            for (int i = 0; i < v.length; i++) v[i] = values.get(i).getAsFloat();
            set(loc, type, v);
        }
        JsonArray samplers = json.getAsJsonArray("samplers");
        if (samplers != null) {
            int unit = 0;
            for (JsonElement e : samplers) {
                int loc = GL33C.glGetUniformLocation(program, e.getAsJsonObject().get("name").getAsString());
                if (loc >= 0) GL33C.glUniform1i(loc, unit);
            }
        }
    }

    static void set(int loc, String type, float[] v) {
        switch (type) {
            case "matrix4x4" -> GL33C.glUniformMatrix4fv(loc, false, v);
            case "int" -> GL33C.glUniform1i(loc, (int) v[0]);
            default -> {
                switch (v.length) {
                    case 1 -> GL33C.glUniform1f(loc, v[0]);
                    case 2 -> GL33C.glUniform2f(loc, v[0], v[1]);
                    case 3 -> GL33C.glUniform3f(loc, v[0], v[1], v[2]);
                    case 4 -> GL33C.glUniform4f(loc, v[0], v[1], v[2], v[3]);
                    default -> {}
                }
            }
        }
    }

    static void f(int program, String name, float... v) {
        int loc = GL33C.glGetUniformLocation(program, name);
        if (loc >= 0) set(loc, "float", v);
    }

    /** Picture settings: what the game would pass for a typical view. */
    static void overrides(int program, String name) {
        f(program, "Time", 3.0f);
        f(program, "Detail", 4.0f);
        // a tilted orbital plane, roughly like the map view
        f(program, "DiskNormal", 0.0f, 0.93f, 0.37f);
        switch (name) {
            case "megastructure" -> {
                f(program, "Extent", 16.0f);
                f(program, "Core", 1.0f);
                f(program, "Tint", 1.0f, 0.82f, 0.45f);
            }
            case "dyson" -> {
                f(program, "Extent", 7.0f);
                f(program, "Core", 1.0f);
                f(program, "Tint", 1.0f, 0.82f, 0.45f);
            }
            case "tidal" -> {
                f(program, "Extent", 12.0f);
                f(program, "Core", 1.0f);
                f(program, "Tint", 1.0f, 0.70f, 0.38f);
                f(program, "Progress", 0.5f);
                f(program, "StarPos", 6.5f, 1.2f);
                f(program, "StarNext", 6.3f, 1.9f);
            }
            default -> {}
        }
    }

    static void save(File file, int w, int h) throws Exception {
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        GL33C.glReadPixels(0, 0, w, h, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                int r = pixels.get(i) & 0xFF;
                int g = pixels.get(i + 1) & 0xFF;
                int b = pixels.get(i + 2) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(image, "png", file);
    }
}
