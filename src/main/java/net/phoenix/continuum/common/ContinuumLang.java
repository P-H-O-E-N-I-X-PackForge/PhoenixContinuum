package net.phoenix.continuum.common;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public final class ContinuumLang {

    /** A string that starts with this is a translation key with its arguments, not text. See {@link #enc}. */
    private static final char MARK = '\u0001';

    private ContinuumLang() {}

    public static MutableComponent tr(String key, Object... args) {
        Object[] safe = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof String s && isEncoded(s)) {
                safe[i] = component(s);
                continue;
            }
            boolean allowed = arg instanceof Component || arg instanceof Number || arg instanceof Boolean ||
                    arg instanceof String;
            safe[i] = allowed ? arg : String.valueOf(arg);
        }
        return Component.translatable(key, safe);
    }

    /**
     * A message the server hands on as a plain string (a reason a launch was refused, why a schedule is waiting) that
     * still reaches each player in their own language. The string holds the translation key and its arguments;
     * {@link #component} turns it back into a translatable component wherever it is shown. A string that came from
     * somewhere else, or an older saved one, is not encoded and is shown as it is. An argument can itself be an
     * encoded string.
     */
    public static String enc(String key, Object... args) {
        JsonArray array = new JsonArray();
        array.add(key);
        for (Object arg : args) {
            if (arg instanceof Number n) array.add(new JsonPrimitive(n));
            else if (arg instanceof Boolean b) array.add(new JsonPrimitive(b));
            else array.add(String.valueOf(arg));
        }
        return MARK + array.toString();
    }

    public static boolean isEncoded(String text) {
        return text != null && !text.isEmpty() && text.charAt(0) == MARK;
    }

    public static MutableComponent component(String text) {
        if (!isEncoded(text)) return Component.literal(text == null ? "" : text);
        try {
            JsonArray array = JsonParser.parseString(text.substring(1)).getAsJsonArray();
            String key = array.get(0).getAsString();
            Object[] args = new Object[array.size() - 1];
            for (int i = 1; i < array.size(); i++) {
                JsonElement e = array.get(i);
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isNumber()) args[i - 1] = p.getAsNumber();
                else if (p.isBoolean()) args[i - 1] = p.getAsBoolean();
                else args[i - 1] = p.getAsString();
            }
            return tr(key, args);
        } catch (RuntimeException e) {
            return Component.literal(text.substring(1));
        }
    }

    /**
     * A component as an encoded string, so it can be stored or sent and shown in the reader's language later. A
     * component that is not a translation is kept as its text.
     */
    public static String fromComponent(Component component) {
        if (!component.getSiblings().isEmpty()) {
            java.util.List<Object> parts = new java.util.ArrayList<>();
            parts.add(fromComponent(component.plainCopy()));
            for (Component sibling : component.getSiblings()) parts.add(fromComponent(sibling));
            if (parts.size() <= 12) return enc("phoenix_continuum.join." + parts.size(), parts.toArray());
            return component.getString();
        }
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            Object[] args = new Object[tc.getArgs().length];
            for (int i = 0; i < args.length; i++) {
                Object arg = tc.getArgs()[i];
                args[i] = arg instanceof Component c ? fromComponent(c) : arg;
            }
            return enc(tc.getKey(), args);
        }
        return component.getString();
    }

    /** A translation looked up where this runs: the player's language on a client, the server's on a server. */
    public static String text(String key, Object... args) {
        String pattern = net.minecraft.locale.Language.getInstance().getOrDefault(key);
        if (args.length == 0) return pattern;
        try {
            return String.format(pattern, args);
        } catch (java.util.IllegalFormatException e) {
            return pattern;
        }
    }

    /** The text of an encoded string as the side that reads it sees it. */
    public static String plain(String text) {
        return component(text).getString();
    }
}
