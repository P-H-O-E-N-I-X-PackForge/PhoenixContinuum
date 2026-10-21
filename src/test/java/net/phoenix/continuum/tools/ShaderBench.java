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

    static final String[] QUAD = {"wolfrayet", "pulsar", "protostar", "planetary", "megastructure", "tidal", "blackhole",
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

            // look at it
            GL33C.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
            save(out.resolve(name + ".png").toFile(), w, h);

            // time it covering the whole target
            for (int i = 0; i < 5; i++) GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 6);
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
        GLFW.glfwTerminate();
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
