package cn.arkmillion.core.metadata;

import cn.arkmillion.core.enums.GenerationType;

import java.util.ArrayList;
import java.util.List;

public final class SchemaDefinition {

    private final Class<?> entityClass;
    private final List<ColumnMetadata> columns = new ArrayList<>();
    private final List<IndexMetadata> indexes = new ArrayList<>();
    private String tableName;
    private String collectionName;
    private String databaseName;
    private String engine;
    private String charset;
    private String comment;
    private boolean document;

    public SchemaDefinition(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public List<IndexMetadata> getIndexes() {
        return indexes;
    }

    public ColumnMetadata getIdColumn() {
        for (ColumnMetadata c : columns) {
            if (c.isPrimaryId()) {
                return c;
            }
        }
        return null;
    }

    public ColumnMetadata findColumnByProperty(String property) {
        for (ColumnMetadata c : columns) {
            if (c.getPropertyName().equals(property)) {
                return c;
            }
        }
        return null;
    }

    public ColumnMetadata findColumnByName(String columnName) {
        for (ColumnMetadata c : columns) {
            if (c.getColumnName().equalsIgnoreCase(columnName)) {
                return c;
            }
        }
        return null;
    }

    public List<ColumnMetadata> getInsertableColumns() {
        List<ColumnMetadata> result = new ArrayList<>();
        for (ColumnMetadata c : columns) {
            if (c.isPrimaryId() && (c.isAutoIncrement() || c.getGenerationType() == GenerationType.IDENTITY)) {
                continue;
            }
            result.add(c);
        }
        return result;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isDocument() {
        return document;
    }

    public void setDocument(boolean document) {
        this.document = document;
    }
}
