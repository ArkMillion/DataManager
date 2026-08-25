package cn.arkmillion.postgresql;

import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.metadata.ColumnMetadata;
import cn.arkmillion.core.metadata.IndexMetadata;
import cn.arkmillion.core.metadata.SchemaDefinition;
import cn.arkmillion.core.schema.SchemaSynchronizer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PostgreSQLSchemaSynchronizer extends SchemaSynchronizer {

    public PostgreSQLSchemaSynchronizer(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected boolean tableExists(String tableName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = current_schema() AND lower(table_name) = lower(?)")) {
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
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(PgDialect.INSTANCE.quoteIdentifier(definition.getTableName())).append(" (\n");
        List<ColumnMetadata> columns = definition.getColumns();
        ColumnMetadata idColumn = definition.getIdColumn();
        for (int i = 0; i < columns.size(); i++) {
            sb.append("  ").append(columnDefinition(columns.get(i), idColumn, true));
            if (i < columns.size() - 1 || idColumn != null) {
                sb.append(',');
            }
            sb.append('\n');
        }
        if (idColumn != null) {
            sb.append("  PRIMARY KEY (").append(PgDialect.INSTANCE.quoteIdentifier(idColumn.getColumnName())).append(")\n");
        }
        sb.append(")");
        executeDdl(sb.toString());
        applyComments(definition);
    }

    private void applyComments(SchemaDefinition definition) {
        if (definition.getComment() != null && !definition.getComment().isEmpty()) {
            executeDdl("COMMENT ON TABLE " + PgDialect.INSTANCE.quoteIdentifier(definition.getTableName())
                    + " IS '" + escape(definition.getComment()) + "'");
        }
        for (ColumnMetadata column : definition.getColumns()) {
            if (column.getComment() != null && !column.getComment().isEmpty()) {
                executeDdl("COMMENT ON COLUMN "
                        + PgDialect.INSTANCE.quoteIdentifier(definition.getTableName()) + "."
                        + PgDialect.INSTANCE.quoteIdentifier(column.getColumnName())
                        + " IS '" + escape(column.getComment()) + "'");
            }
        }
    }

    private String columnDefinition(ColumnMetadata column, ColumnMetadata idColumn, boolean allowSerial) {
        StringBuilder sb = new StringBuilder();
        sb.append(PgDialect.INSTANCE.quoteIdentifier(column.getColumnName()));
        boolean serial = allowSerial && column == idColumn && column.isAutoIncrementOrIdentity()
                && (column.getJavaType() == Long.class || column.getJavaType() == Integer.class
                    || column.getJavaType() == long.class || column.getJavaType() == int.class);
        if (serial) {
            sb.append(column.getJavaType() == Long.class || column.getJavaType() == long.class
                    ? " BIGSERIAL" : " SERIAL");
        } else {
            sb.append(' ').append(sqlType(column));
        }
        if (!column.isNullable() && column != idColumn) {
            sb.append(" NOT NULL");
        }
        if (!serial && column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
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
                return "SMALLINT";
            case INT:
            case INTEGER:
                return "INTEGER";
            case BIGINT:
                return "BIGINT";
            case FLOAT:
                return "REAL";
            case DOUBLE:
                return "DOUBLE PRECISION";
            case DECIMAL:
                return column.getPrecision() > 0
                        ? "NUMERIC(" + column.getPrecision() + "," + column.getScale() + ")"
                        : "NUMERIC(18,2)";
            case BOOLEAN:
                return "BOOLEAN";
            case CHAR:
                return "CHAR(" + Math.max(column.getLength(), 1) + ")";
            case TEXT:
            case LONGTEXT:
                return "TEXT";
            case BLOB:
                return "BYTEA";
            case DATE:
                return "DATE";
            case TIME:
                return "TIME";
            case DATETIME:
            case TIMESTAMP:
                return "TIMESTAMP";
            case VARCHAR:
            default:
                int length = column.getLength() > 0 ? column.getLength() : 255;
                return "VARCHAR(" + length + ")";
        }
    }

    @Override
    protected void alterTable(SchemaDefinition definition) {
        Map<String, Boolean> existing = existingColumns(definition.getTableName());
        for (ColumnMetadata column : definition.getColumns()) {
            if (!existing.containsKey(column.getColumnName().toLowerCase())) {
                executeDdl("ALTER TABLE " + PgDialect.INSTANCE.quoteIdentifier(definition.getTableName())
                        + " ADD COLUMN " + columnDefinition(column, null, false));
            }
        }
    }

    private Map<String, Boolean> existingColumns(String tableName) {
        Map<String, Boolean> existing = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name FROM information_schema.columns "
                             + "WHERE table_schema = current_schema() AND lower(table_name) = lower(?)")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    existing.put(rs.getString(1).toLowerCase(), Boolean.TRUE);
                }
            }
        } catch (SQLException e) {
            throw new DataManagerException("Failed to inspect columns of table '" + tableName + "'", e);
        }
        return existing;
    }

    @Override
    protected void dropTable(String tableName) {
        executeDdl("DROP TABLE IF EXISTS " + PgDialect.INSTANCE.quoteIdentifier(tableName));
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
                cols.append(PgDialect.INSTANCE.quoteIdentifier(columns.get(i)));
            }
            executeDdl("CREATE " + unique + "INDEX " + PgDialect.INSTANCE.quoteIdentifier(index.getName())
                    + " ON " + PgDialect.INSTANCE.quoteIdentifier(definition.getTableName())
                    + " (" + cols + ")");
        }
    }

    private boolean indexExists(String indexName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() AND lower(indexname) = lower(?)")) {
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
        Map<String, Boolean> existing = existingColumns(tableName);
        List<String> missing = new ArrayList<>();
        for (ColumnMetadata c : definition.getColumns()) {
            if (!existing.containsKey(c.getColumnName().toLowerCase())) {
                missing.add(c.getColumnName());
            }
        }
        if (!missing.isEmpty()) {
            throw new DataManagerException("Schema validation failed: table '" + tableName + "' misses columns " + missing);
        }
    }

    private String escape(String value) {
        return value.replace("'", "''");
    }
}
