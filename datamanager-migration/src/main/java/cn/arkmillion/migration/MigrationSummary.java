package cn.arkmillion.migration;

import java.util.List;
import java.util.Map;

public final class MigrationSummary {

    private final List<String> appliedVersions;
    private final Map<String, Long> appliedAtMs;
    private final String currentVersion;
    private final int totalScripts;

    MigrationSummary(List<String> appliedVersions, Map<String, Long> appliedAtMs, String currentVersion, int totalScripts) {
        this.appliedVersions = appliedVersions;
        this.appliedAtMs = appliedAtMs;
        this.currentVersion = currentVersion;
        this.totalScripts = totalScripts;
    }

    public static MigrationSummary empty(String currentVersion) {
        return new MigrationSummary(java.util.Collections.emptyList(), java.util.Collections.emptyMap(), currentVersion, 0);
    }

    public List<String> getAppliedVersions() {
        return appliedVersions;
    }

    public Map<String, Long> getAppliedAtMs() {
        return appliedAtMs;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public int getTotalScripts() {
        return totalScripts;
    }

    public boolean isEmpty() {
        return appliedVersions.isEmpty();
    }
}
