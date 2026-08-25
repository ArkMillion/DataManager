package cn.arkmillion.migration;

final class AppliedMigration {

    private final String version;
    private final String description;
    private final String script;
    private final long checksum;

    AppliedMigration(String version, String description, String script, long checksum) {
        this.version = version;
        this.description = description;
        this.script = script;
        this.checksum = checksum;
    }

    String getVersion() {
        return version;
    }

    @SuppressWarnings("unused")
    String getDescription() {
        return description;
    }

    @SuppressWarnings("unused")
    String getScript() {
        return script;
    }

    long getChecksum() {
        return checksum;
    }
}
