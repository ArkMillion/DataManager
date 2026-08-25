package cn.arkmillion.migration;

import java.util.Arrays;
import java.util.List;

final class MigrationScript implements Comparable<MigrationScript> {

    private final String version;
    private final String description;
    private final String fileName;
    private final String location;
    private final String content;

    MigrationScript(String version, String description, String fileName, String location, String content) {
        this.version = version;
        this.description = description;
        this.fileName = fileName;
        this.location = location;
        this.content = content;
    }

    String getVersion() {
        return version;
    }

    String getDescription() {
        return description;
    }

    String getFileName() {
        return fileName;
    }

    @SuppressWarnings("unused")
    String getLocation() {
        return location;
    }

    List<String> statements() {
        return cn.arkmillion.core.util.SqlScripts.splitStatements(content);
    }

    String content() {
        return content;
    }

    long checksum() {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        crc.update(bytes);
        return crc.getValue();
    }

    @Override
    public int compareTo(MigrationScript other) {
        int byVersion = compareVersions(this.version, other.version);
        if (byVersion != 0) {
            return byVersion;
        }
        return this.fileName.compareTo(other.fileName);
    }

    static int compareVersions(String a, String b) {
        long[] pa = parseVersion(a);
        long[] pb = parseVersion(b);
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            long va = i < pa.length ? pa[i] : 0L;
            long vb = i < pb.length ? pb[i] : 0L;
            if (va != vb) {
                return Long.compare(va, vb);
            }
        }
        return 0;
    }

    private static long[] parseVersion(String version) {
        String[] parts = version.split("_");
        long[] numbers = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Long.parseLong(parts[i]);
            } catch (NumberFormatException e) {
                numbers[i] = Long.MAX_VALUE;
            }
        }
        return numbers;
    }

    @Override
    public String toString() {
        return Arrays.toString(parseVersion(version)) + " " + fileName;
    }
}
