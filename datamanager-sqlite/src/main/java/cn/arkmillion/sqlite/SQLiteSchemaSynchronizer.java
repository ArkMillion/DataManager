package cn.arkmillion.sqlite;

import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.metadata.ColumnMetadata;
import cn.arkmillion.core.metadata.IndexMetadata;
import cn.arkmillion.core.metadata.SchemaDefinition;
import cn.arkmillion.core.schema.SchemaSynchronizer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SQLiteSchemaSynchronizer extends SchemaSynchronizer {

    public SQLiteSchemaSynchronizer(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected boolean tableExists(String tableName) {
        try (Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND lower(name) = lower(?)")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataManagerException("Failed to check table existence: " + tableName, e);
        }
    }

    @Override
    protected void createTable(SchemaDefinition definition) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(q(definition.getTableName())).append(" (\n");
        List<ColumnMetadata> columns = definition.getColumns();
        ColumnMetadata idColumn = definition.getIdColumn();
        for (int i = 0; i < columns.size(); i++) {
            sb.append("  ").append(columnDefinition(columns.get(i), idColumn));
            if (i < columns.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append(")");
        executeDdl(sb.toString());
    }

    private String columnDefinition(ColumnMetadata column, ColumnMetadata idColumn) {
        StringBuilder sb = new StringBuilder();
        sb.append(q(column.getColumnName()));
        sb.append(' ').append(sqlType(column));
        if (column == idColumn
                && (column.isAutoIncrement() || column.getGenerationType() == GenerationType.IDENTITY)
                && (column.getJavaType() == Long.class || column.getJavaType() == Integer.class
                    || column.getJavaType() == long.class || column.getJavaType() == int.class)) {
            sb.append(" PRIMARY KEY AUTOINCREMENT");
        } else if (column == idColumn) {
            sb.append(" PRIMARY KEY");
        }
        if (!column.isNullable() && !column.isPrimaryId()) {
            sb.append(" NOT NULL");
        }
        if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(defaultValueLiteral(column.getDefaultValue()));
        }
        return sb.toString();
    }

    private String defaultValueLiteral(String raw) {
        String trimmed = raw.trim();
        String upper = trimmed.toUpperCase();
        if (upper.equals("CURRENT_TIMESTAMP") || upper.equals("NULL") || isNumeric(trimmed)) {
            return trimmed;
        }
        return "'" + trimmed.replace("'", "''") + "'";
    }

    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sqlType(ColumnMetadata column) {
        switch (column.getDataType()) {
            case TINYINT:
            case SMALLINT:
            case INT:
            case INTEGER:
            case BIGINT:
                return "INTEGER";
            case FLOAT:
            case DOUBLE:
                return "REAL";
            case DECIMAL:
                return column.getPrecision() > 0
                        ? "NUMERIC(" + column.getPrecision() + "," + column.getScale() + ")"
                        : "NUMERIC";
            case BOOLEAN:
                return "INTEGER";
            case BLOB:
                return "BLOB";
            case DATE:
            case TIME:
            case DATETIME:
            case TIMESTAMP:
                return "TEXT";
            case TEXT:
            case LONGTEXT:
                return "TEXT";
            case CHAR:
            case VARCHAR:
            default:
                return "TEXT";
        }
    }

    @Override
    protected void alterTable(SchemaDefinition definition) {
        Map<String, Boolean> existing = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + q(definition.getTableName()) + ")")) {
            while (rs.next()) {
                existing.put(rs.getString("name"), Boolean.TRUE);
            }
        } catch (SQLException e) {
            throw new DataManagerException("Failed to inspect columns of table '" + definition.getTableName() + "'", e);
        }
        for (ColumnMetadata column : definition.getColumns()) {
            if (!existing.containsKey(column.getColumnName())) {
                executeDdl("ALTER TABLE " + q(definition.getTableName())
                        + " ADD COLUMN " + columnDefinition(column, null));
            }
        }
    }

    @Override
    protected void dropTable(String tableName) {
        executeDdl("DROP TABLE IF EXISTS " + q(tableName));
    }

    @Override
    protected void createIndexes(SchemaDefinition definition) {
        for (IndexMetadata index : definition.getIndexes()) {
            if (indexExists(index.getName())) {
                continue;
            }
            String unique = index.isUnique() ? "UNIQUE " : "";
            List<String> columns = new ArrayList<>(index.getColumns());
            StringBuilder cols = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    cols.append(", ");
                }
                cols.append(q(columns.get(i)));
            }
            executeDdl("CREATE " + unique + "INDEX IF NOT EXISTS " + q(index.getName())
                    + " ON " + q(definition.getTableName()) + " (" + cols + ")");
        }
    }

    private boolean indexExists(String indexName) {
        try (Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?")) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataManagerException("Failed to check index existence: " + indexName, e);
        }
    }

    @Override
    protected void validateSchema(SchemaDefinition definition) {
        String tableName = definition.getTableName();
        if (!tableExists(tableName)) {
            throw new DataManagerException("Schema validation failed: table '" + tableName + "' does not exist");
        }
        List<String> missing = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + q(tableName) + ")")) {
            Map<String, Boolean> existing = new LinkedHashMap<>();
            while (rs.next()) {
                existing.put(rs.getString("name").toUpperCase(), Boolean.TRUE);
            }
            for (ColumnMetadata c : definition.getColumns()) {
                if (!existing.containsKey(c.getColumnName().toUpperCase())) {
                    missing.add(c.getColumnName());
                }
            }
        } catch (SQLException e) {
            throw new DataManagerException("Schema validation failed for table '" + tableName + "'", e);
        }
        if (!missing.isEmpty()) {
            throw new DataManagerException("Schema validation failed: table '" + tableName + "' misses columns " + missing);
        }
    }

    private static String q(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
