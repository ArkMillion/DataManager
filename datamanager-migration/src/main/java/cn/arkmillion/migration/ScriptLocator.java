package cn.arkmillion.migration;

import cn.arkmillion.core.exception.DataManagerException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class ScriptLocator {

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("^V(\\d+(?:_\\d+)*)__(.+)\\.sql$");

    private ScriptLocator() {
    }

    static boolean isMigrationFile(String name) {
        return SCRIPT_PATTERN.matcher(name).matches();
    }

    static List<MigrationScript> locate(List<String> locations, ClassLoader classLoader) {
        List<MigrationScript> scripts = new ArrayList<>();
        ClassLoader cl = classLoader == null ? ScriptLocator.class.getClassLoader() : classLoader;
        for (String location : locations) {
            boolean classpath = location.startsWith("classpath:");
            String path = classpath ? location.substring("classpath:".length()) : location;
            if (classpath) {
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                scripts.addAll(scanClasspath(cl, path));
            } else {
                scripts.addAll(scanFileSystem(location, path));
            }
        }
        scripts.sort(null);
        return scripts;
    }

    private static List<MigrationScript> scanClasspath(ClassLoader cl, String path) {
        List<MigrationScript> scripts = new ArrayList<>();
        try {
            Enumeration<java.net.URL> resources = cl.getResources(path);
            while (resources.hasMoreElements()) {
                java.net.URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    File dir = new File(url.toURI());
                    if (dir.isDirectory()) {
                        scripts.addAll(scanDirectory(dir.toPath()));
                    }
                } else if ("jar".equals(url.getProtocol())) {
                    scripts.addAll(scanJar(url, path));
                }
            }
        } catch (Exception e) {
            throw new DataManagerException("Failed to scan classpath migration location", e);
        }
        return scripts;
    }

    private static List<MigrationScript> scanDirectory(Path dir) throws IOException {
        List<MigrationScript> scripts = new ArrayList<>();
        if (!Files.exists(dir)) {
            return scripts;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString();
                Matcher matcher = SCRIPT_PATTERN.matcher(name);
                if (matcher.matches()) {
                    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    scripts.add(new MigrationScript(matcher.group(1), matcher.group(2), name,
                            dir.toString(), content));
                }
            }
        }
        return scripts;
    }

    private static List<MigrationScript> scanJar(java.net.URL url, String basePath) throws IOException {
        List<MigrationScript> scripts = new ArrayList<>();
        String jarPath = url.getPath();
        int bang = jarPath.indexOf('!');
        if (bang > 0) {
            jarPath = jarPath.substring(0, bang);
        }
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring("file:".length());
        }
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(basePath) || entry.isDirectory()) {
                    continue;
                }
                String simpleName = name.substring(basePath.length());
                Matcher matcher = SCRIPT_PATTERN.matcher(simpleName);
                if (simpleName.indexOf('/') < 0 && matcher.matches()) {
                    scripts.add(new MigrationScript(matcher.group(1), matcher.group(2), simpleName,
                            "jar:" + jarPath + "!" + basePath, read(jar.getInputStream(entry))));
                }
            }
        }
        return scripts;
    }

    private static List<MigrationScript> scanFileSystem(String originalLocation, String path) {
        List<MigrationScript> scripts = new ArrayList<>();
        if (originalLocation.startsWith("classpath:")) {
            return scripts;
        }
        Path dir = java.nio.file.Paths.get(path);
        if (!Files.isDirectory(dir)) {
            return scripts;
        }
        try {
            scripts.addAll(scanDirectory(dir));
        } catch (IOException e) {
            throw new DataManagerException("Failed to scan migration directory: " + dir, e);
        }
        return scripts;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
