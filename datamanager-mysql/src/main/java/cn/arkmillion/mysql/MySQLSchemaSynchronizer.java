package cn.arkmillion.mysql;

import cn.arkmillion.core.enums.DataType;
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

public class MySQLSchemaSynchronizer extends SchemaSynchronizer {

    public MySQLSchemaSynchronizer(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected boolean tableExists(String tableName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
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
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(MySqlDialect.INSTANCE.quoteIdentifier(definition.getTableName())).append(" (\n");
        List<ColumnMetadata> columns = definition.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            sb.append("  ").append(columnDefinition(columns.get(i)));
            if (!columns.get(i).getComment().isEmpty()) {
                sb.append(" COMMENT '").append(escape(columns.get(i).getComment())).append('\'');
            }
            if (i < columns.size() - 1 || definition.getIdColumn() != null) {
                sb.append(',');
            }
            sb.append('\n');
        }
        ColumnMetadata idColumn = definition.getIdColumn();
        if (idColumn != null) {
            sb.append("  PRIMARY KEY (").append(MySqlDialect.INSTANCE.quoteIdentifier(idColumn.getColumnName())).append(")\n");
        }
        sb.append(")");
        if (definition.getEngine() != null && !definition.getEngine().isEmpty()) {
            sb.append(" ENGINE=").append(definition.getEngine());
        }
        if (definition.getCharset() != null && !definition.getCharset().isEmpty()) {
            sb.append(" DEFAULT CHARSET=").append(definition.getCharset());
        }
        if (definition.getComment() != null && !definition.getComment().isEmpty()) {
            sb.append(" COMMENT='").append(escape(definition.getComment())).append('\'');
        }
        executeDdl(sb.toString());
    }

    private String columnDefinition(ColumnMetadata column) {
        StringBuilder sb = new StringBuilder();
        sb.append(MySqlDialect.INSTANCE.quoteIdentifier(column.getColumnName()));
        sb.append(' ').append(sqlType(column));
        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }
        if (column.isAutoIncrement() || column.getGenerationType() == cn.arkmillion.core.enums.GenerationType.IDENTITY && column.isPrimaryId()) {
            sb.append(" AUTO_INCREMENT");
        }
        if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(defaultValueLiteral(column.getDefaultValue()));
        }
        if (column.isUnique() && !column.isPrimaryId() && column.getIndexSpec() == null) {
            sb.append(" UNIQUE");
        }
        return sb.toString();
    }

    private String defaultValueLiteral(String raw) {
        String upper = raw.trim().toUpperCase();
        if (upper.equals("CURRENT_TIMESTAMP") || upper.equals("NULL") || isNumeric(raw.trim())) {
            return raw.trim();
        }
        return "'" + escape(raw) + "'";
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
                return "TINYINT";
            case SMALLINT:
                return "SMALLINT";
            case INT:
            case INTEGER:
                return "INT";
            case BIGINT:
                return "BIGINT";
            case FLOAT:
                return "FLOAT";
            case DOUBLE:
                return "DOUBLE";
            case DECIMAL:
                return column.getPrecision() > 0
                        ? "DECIMAL(" + column.getPrecision() + "," + column.getScale() + ")"
                        : "DECIMAL(18,2)";
            case BOOLEAN:
                return "TINYINT(1)";
            case CHAR:
                return "CHAR(" + Math.max(column.getLength(), 1) + ")";
            case TEXT:
                return "TEXT";
            case LONGTEXT:
                return "LONGTEXT";
            case BLOB:
                return "BLOB";
            case DATE:
                return "DATE";
            case TIME:
                return "TIME";
            case DATETIME:
                return "DATETIME";
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
        Map<String, Boolean> existing = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            ps.setString(1, definition.getTableName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    existing.put(rs.getString(1), Boolean.TRUE);
                }
            }
        } catch (SQLException e) {
            throw new DataManagerException("Failed to inspect columns of table '" + definition.getTableName() + "'", e);
        }
        for (ColumnMetadata column : definition.getColumns()) {
            if (!existing.containsKey(column.getColumnName())) {
                String ddl = "ALTER TABLE " + MySqlDialect.INSTANCE.quoteIdentifier(definition.getTableName())
                        + " ADD COLUMN " + columnDefinition(column);
                executeDdl(ddl);
            }
        }
    }

    @Override
    protected void dropTable(String tableName) {
        executeDdl("DROP TABLE IF EXISTS " + MySqlDialect.INSTANCE.quoteIdentifier(tableName));
    }

    @Override
    protected void createIndexes(SchemaDefinition definition) {
        for (IndexMetadata index : definition.getIndexes()) {
            if (!indexExists(definition.getTableName(), index.getName())) {
                createIndex(definition.getTableName(), index);
            }
        }
    }

    private boolean indexExists(String tableName, String indexName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?")) {
            ps.setString(1, tableName);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataManagerException("Failed to check index existence: " + indexName, e);
        }
    }

    private void createIndex(String tableName, IndexMetadata index) {
        String unique = index.isUnique() ? "UNIQUE " : "";
        StringBuilder cols = new StringBuilder();
        List<String> columns = new ArrayList<>(index.getColumns());
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                cols.append(", ");
            }
            cols.append(MySqlDialect.INSTANCE.quoteIdentifier(columns.get(i)));
        }
        executeDdl("CREATE " + unique + "INDEX " + MySqlDialect.INSTANCE.quoteIdentifier(index.getName())
                + " ON " + MySqlDialect.INSTANCE.quoteIdentifier(tableName) + " (" + cols + ")");
    }

    @Override
    protected void validateSchema(SchemaDefinition definition) {
        String tableName = definition.getTableName();
        if (!tableExists(tableName)) {
            throw new DataManagerException("Schema validation failed: table '" + tableName + "' does not exist");
        }
        List<String> missing = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Boolean> existing = new LinkedHashMap<>();
                while (rs.next()) {
                    existing.put(rs.getString(1).toUpperCase(), Boolean.TRUE);
                }
                for (ColumnMetadata c : definition.getColumns()) {
                    if (!existing.containsKey(c.getColumnName().toUpperCase())) {
                        missing.add(c.getColumnName());
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataManagerException("Schema validation failed for table '" + tableName + "'", e);
        }
        if (!missing.isEmpty()) {
            throw new DataManagerException("Schema validation failed: table '" + tableName + "' misses columns " + missing);
        }
    }

    private String escape(String value) {
        return value.replace("'", "''");
    }
}
