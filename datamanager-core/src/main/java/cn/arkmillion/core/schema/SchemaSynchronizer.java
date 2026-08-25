package cn.arkmillion.core.schema;

import cn.arkmillion.core.annotation.Document;
import cn.arkmillion.core.annotation.Table;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.metadata.EntityParser;
import cn.arkmillion.core.metadata.SchemaDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class SchemaSynchronizer {

    protected final DataSource dataSource;

    protected SchemaSynchronizer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public final void sync(Class<?> entityClass, SyncMode mode) {
        validateEntity(entityClass);
        SchemaDefinition definition = EntityParser.parse(entityClass);
        SchemaSyncPolicy.checkAllowed(mode);

        switch (mode) {
            case CREATE:
                if (!tableExists(definition.getTableName())) {
                    createTable(definition);
                    createIndexes(definition);
                }
                break;
            case UPDATE:
                if (!tableExists(definition.getTableName())) {
                    createTable(definition);
                } else {
                    alterTable(definition);
                }
                createIndexes(definition);
                break;
            case VALIDATE:
                validateSchema(definition);
                break;
            case DROP_CREATE:
                dropTable(definition.getTableName());
                createTable(definition);
                createIndexes(definition);
                break;
            default:
                throw new DataManagerException("Unsupported sync mode: " + mode);
        }
    }

    protected abstract boolean tableExists(String tableName);

    protected abstract void createTable(SchemaDefinition definition);

    protected abstract void alterTable(SchemaDefinition definition);

    protected abstract void dropTable(String tableName);

    protected abstract void createIndexes(SchemaDefinition definition);

    protected abstract void validateSchema(SchemaDefinition definition);

    protected void executeDdl(String sql) {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new DataManagerException("DDL execution failed: " + sql, e);
        }
    }

    private void validateEntity(Class<?> entityClass) {
        if (!entityClass.isAnnotationPresent(Table.class)
                && !entityClass.isAnnotationPresent(Document.class)) {
            throw new DataManagerException("Entity must be annotated with @Table or @Document: " + entityClass.getName());
        }
    }
}
