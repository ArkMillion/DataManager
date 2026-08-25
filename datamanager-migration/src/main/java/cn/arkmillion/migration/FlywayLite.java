package cn.arkmillion.migration;

import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.util.SqlScripts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlywayLite implements MigrationEngine {

    private static final Logger LOG = LoggerFactory.getLogger(FlywayLite.class);

    public static final String DEFAULT_HISTORY_TABLE = "dm_schema_history";

    private final RelationalDB db;
    private final List<String> locations;
    private final Map<String, String> placeholders;
    private final String historyTable;
    private final boolean ignoreMissingMigrations;

    private FlywayLite(Builder builder) {
        this.db = builder.db;
        this.locations = new ArrayList<>(builder.locations);
        this.placeholders = new HashMap<>(builder.placeholders);
        this.historyTable = builder.historyTable;
        this.ignoreMissingMigrations = builder.ignoreMissingMigrations;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public synchronized MigrationSummary migrate() {
        ensureHistoryTable();
        Map<String, AppliedMigration> applied = loadApplied();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<MigrationScript> scripts = ScriptLocator.locate(locations, cl);
        if (scripts.isEmpty()) {
            LOG.info("No migration scripts found under {}", locations);
        }
        validateAppliedChecksums(scripts, applied);

        List<String> appliedNow = new ArrayList<>();
        Map<String, Long> timings = new LinkedHashMap<>();
        for (MigrationScript script : scripts) {
            if (applied.containsKey(script.getVersion())) {
                continue;
            }
            long start = System.currentTimeMillis();
            executeScript(script);
            long elapsed = System.currentTimeMillis() - start;
            recordHistory(script, elapsed);
            appliedNow.add(script.getVersion());
            timings.put(script.getFileName(), elapsed);
            LOG.info("Applied migration {} ({}) in {} ms", script.getVersion(), script.getDescription(), elapsed);
        }
        String current = currentVersion(applied, appliedNow);
        return new MigrationSummary(appliedNow, timings, current, scripts.size());
    }

    @Override
    public synchronized String info() {
        ensureHistoryTable();
        Map<String, AppliedMigration> applied = loadApplied();
        StringBuilder sb = new StringBuilder("Applied migrations:\n");
        for (Map.Entry<String, AppliedMigration> e : applied.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" -> ").append(e.getValue().getScript()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public synchronized void validate() {
        ensureHistoryTable();
        Map<String, AppliedMigration> applied = loadApplied();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<MigrationScript> scripts = ScriptLocator.locate(locations, cl);
        validateAppliedChecksums(scripts, applied);
    }

    private String currentVersion(Map<String, AppliedMigration> before, List<String> appliedNow) {
        String highest = null;
        for (String version : before.keySet()) {
            if (highest == null || MigrationScript.compareVersions(version, highest) > 0) {
                highest = version;
            }
        }
        for (String version : appliedNow) {
            if (highest == null || MigrationScript.compareVersions(version, highest) > 0) {
                highest = version;
            }
        }
        return highest == null ? "<none>" : highest;
    }

    private void executeScript(MigrationScript script) {
        String content = SqlScripts.applyPlaceholders(script.content(), placeholders);
        List<String> statements = SqlScripts.splitStatements(content);
        boolean ownTx = !db.isInTransaction();
        try {
            if (ownTx) {
                db.beginTransaction();
            }
            for (String statement : statements) {
                db.execute(statement);
            }
            if (ownTx) {
                db.commit();
            }
        } catch (RuntimeException e) {
            if (ownTx && db.isInTransaction()) {
                try {
                    db.rollback();
                } catch (RuntimeException re) {
                    e.addSuppressed(re);
                }
            }
            throw new DataManagerException("Migration " + script.getFileName() + " failed: " + e.getMessage(), e);
        }
    }

    private void ensureHistoryTable() {
        db.execute("CREATE TABLE IF NOT EXISTS " + historyTable + " ("
                + "version VARCHAR(128) PRIMARY KEY, "
                + "description VARCHAR(200), "
                + "script VARCHAR(200), "
                + "checksum BIGINT, "
                + "installed_on TIMESTAMP, "
                + "execution_ms BIGINT, "
                + "success BOOLEAN)");
    }

    private Map<String, AppliedMigration> loadApplied() {
        List<Map<String, Object>> rows = db.queryMap(
                "SELECT version, description, script, checksum FROM " + historyTable);
        Map<String, AppliedMigration> applied = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String version = String.valueOf(row.get("version"));
            Number checksum = (Number) row.get("checksum");
            applied.put(version, new AppliedMigration(
                    version,
                    row.get("description") == null ? "" : String.valueOf(row.get("description")),
                    row.get("script") == null ? "" : String.valueOf(row.get("script")),
                    checksum == null ? 0L : checksum.longValue()));
        }
        return applied;
    }

    private void validateAppliedChecksums(List<MigrationScript> scripts, Map<String, AppliedMigration> applied) {
        for (MigrationScript script : scripts) {
            AppliedMigration existing = applied.get(script.getVersion());
            if (existing != null && existing.getChecksum() != script.checksum()) {
                throw new DataManagerException("Checksum mismatch for migration V" + script.getVersion()
                        + " (" + script.getFileName() + "). Applied checksum=" + existing.getChecksum()
                        + ", local checksum=" + script.checksum()
                        + ". Modifying applied migrations is not allowed.");
            }
        }
        if (!ignoreMissingMigrations) {
            for (Map.Entry<String, AppliedMigration> e : applied.entrySet()) {
                boolean found = false;
                for (MigrationScript script : scripts) {
                    if (script.getVersion().equals(e.getKey())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    LOG.warn("Applied migration V{} ({}) not found in locations {}", e.getKey(), e.getValue().getScript(), locations);
                }
            }
        }
    }

    private void recordHistory(MigrationScript script, long elapsedMs) {
        db.execute("INSERT INTO " + historyTable
                        + " (version, description, script, checksum, installed_on, execution_ms, success) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                script.getVersion(), script.getDescription(), script.getFileName(),
                script.checksum(), elapsedMs, true);
    }

    public static final class Builder {

        private RelationalDB db;
        private List<String> locations = new ArrayList<>();
        private final Map<String, String> placeholders = new HashMap<>();
        private String historyTable = DEFAULT_HISTORY_TABLE;
        private boolean ignoreMissingMigrations = false;

        public Builder relational(RelationalDB relationalDB) {
            this.db = relationalDB;
            return this;
        }

        public Builder locations(String... locationList) {
            this.locations.clear();
            for (String location : locationList) {
                this.locations.add(location);
            }
            return this;
        }

        public Builder placeholder(String name, String value) {
            this.placeholders.put(name, value);
            return this;
        }

        public Builder placeholders(Map<String, String> values) {
            this.placeholders.putAll(values);
            return this;
        }

        public Builder historyTable(String table) {
            this.historyTable = table;
            return this;
        }

        public Builder ignoreMissingMigrations(boolean value) {
            this.ignoreMissingMigrations = value;
            return this;
        }

        public FlywayLite build() {
            if (db == null) {
                throw new DataManagerException("FlywayLite requires a RelationalDB instance");
            }
            if (locations.isEmpty()) {
                locations.add("classpath:db/migration");
            }
            return new FlywayLite(this);
        }
    }
}
