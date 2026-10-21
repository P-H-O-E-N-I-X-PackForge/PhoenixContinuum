package net.phoenix.continuum.tools;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Compiles and links every continuum_* shader pair against the real driver, with a hidden window. */
public final class GlslCheck {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        if (!GLFW.glfwInit()) throw new IllegalStateException("no GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(64, 64, "check", 0, 0);
        if (window == 0) throw new IllegalStateException("no window");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL " + GL20C.glGetString(GL20C.GL_VERSION) + " / " + GL20C.glGetString(GL20C.GL_RENDERER));

        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".fsh")).forEach(p -> {
                String n = p.getFileName().toString();
                names.add(n.substring(0, n.length() - 4));
            });
        }
        names.sort(String::compareTo);
        int bad = 0;
        for (String name : names) {
            String filter = args.length > 1 ? args[1] : "";
            if (!name.contains(filter)) continue;
            Path vsh = dir.resolve(name + ".vsh");
            if (!Files.exists(vsh)) {
                System.out.println("SKIP " + name + " (no vsh)");
                continue;
            }
            int v = compile(GL20C.GL_VERTEX_SHADER, Files.readString(vsh));
            int f = compile(GL20C.GL_FRAGMENT_SHADER, Files.readString(dir.resolve(name + ".fsh")));
            String log = "";
            boolean ok = v != 0 && f != 0;
            if (ok) {
                int program = GL20C.glCreateProgram();
                GL20C.glAttachShader(program, v);
                GL20C.glAttachShader(program, f);
                GL20C.glLinkProgram(program);
                ok = GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL20C.GL_TRUE;
                if (!ok) log = GL20C.glGetProgramInfoLog(program);
            }
            if (!ok) bad++;
            System.out.println((ok ? "OK   " : "FAIL ") + name + (log.isEmpty() ? "" : "\n" + log));
        }
        System.out.println(bad == 0 ? "ALL OK" : bad + " FAILED");
        GLFW.glfwTerminate();
    }

    private static int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) != GL20C.GL_TRUE) {
            System.out.println((type == GL20C.GL_VERTEX_SHADER ? "vertex" : "fragment") + " compile error:\n" +
                    GL20C.glGetShaderInfoLog(shader));
            return 0;
        }
        return shader;
    }
}
