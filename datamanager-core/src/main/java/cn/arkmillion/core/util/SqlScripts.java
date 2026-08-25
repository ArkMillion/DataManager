package cn.arkmillion.core.util;

import cn.arkmillion.core.exception.DataManagerException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlScripts {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private SqlScripts() {
    }

    public static String loadContent(String location) {
        return loadContent(location, Thread.currentThread().getContextClassLoader());
    }

    public static String loadContent(String location, ClassLoader classLoader) {
        try {
            if (location.startsWith("classpath:")) {
                String path = location.substring("classpath:".length());
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                ClassLoader cl = classLoader == null ? SqlScripts.class.getClassLoader() : classLoader;
                try (InputStream in = cl.getResourceAsStream(path)) {
                    if (in == null) {
                        throw new DataManagerException("Classpath resource not found: " + location);
                    }
                    return readStream(in);
                }
            }
            Path file = Paths.get(location);
            if (!Files.exists(file)) {
                throw new DataManagerException("SQL file not found: " + location);
            }
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataManagerException("Failed to load SQL file: " + location, e);
        }
    }

    private static String readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                    current.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    continue;
                }
            }
            if (c == '\'' && !inDoubleQuote) {
                if (inSingleQuote && next == '\'') {
                    current.append(c).append(next);
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                addStatement(statements, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addStatement(statements, current.toString());
        return statements;
    }

    private static void addStatement(List<String> statements, String raw) {
        String trimmed = raw.trim();
        if (!trimmed.isEmpty()) {
            statements.add(trimmed);
        }
    }

    public static String applyPlaceholders(String content, Map<String, String> placeholders) {
        if (content == null || !content.contains("${")) {
            return content;
        }
        if (placeholders == null || placeholders.isEmpty()) {
            throw new DataManagerException("SQL script contains ${...} placeholders but none were provided");
        }
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!placeholders.containsKey(key)) {
                throw new DataManagerException("Missing placeholder value for ${" + key + "} in SQL script");
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(placeholders.get(key)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
