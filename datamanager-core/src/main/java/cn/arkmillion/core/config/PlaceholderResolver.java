package cn.arkmillion.core.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderResolver {

    private static final Pattern PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private static final Map<String, String> OVERRIDES = new ConcurrentHashMap<>();

    private PlaceholderResolver() {
    }

    public static void register(String name, String value) {
        OVERRIDES.put(name, value);
    }

    public static void clear() {
        OVERRIDES.clear();
    }

    public static String resolve(String input) {
        if (input == null || input.indexOf("${") < 0) {
            return input;
        }
        Matcher matcher = PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = lookup(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String lookup(String key) {
        String v = OVERRIDES.get(key);
        if (v != null) {
            return v;
        }
        v = System.getenv(key);
        if (v != null) {
            return v;
        }
        v = System.getProperty(key);
        if (v != null) {
            return v;
        }
        throw new DataManagerPlaceholderException(key);
    }

    public static class DataManagerPlaceholderException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        DataManagerPlaceholderException(String key) {
            super("Unresolvable placeholder ${" + key + "}: not found in overrides, environment variables or system properties");
        }
    }
}
