package cn.arkmillion.core.metadata;

import cn.arkmillion.core.accessor.PropertyAccessors;
import cn.arkmillion.core.enums.DataType;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.IndexDirection;
import cn.arkmillion.core.enums.IndexType;

import java.lang.reflect.Field;

public final class ColumnMetadata {

    private Field field;
    private String propertyName;
    private String columnName;
    private Class<?> javaType;
    private DataType dataType = DataType.AUTO;
    private int length = 255;
    private int precision;
    private int scale;
    private boolean nullable = true;
    private boolean unique;
    private String defaultValue = "";
    private String comment = "";
    private boolean primaryId;
    private GenerationType generationType = GenerationType.NONE;
    private boolean autoIncrement;
    private boolean documentId;
    private IndexSpec indexSpec;
    private MongoIndexSpec mongoIndexSpec;

    public Object getValue(Object entity) {
        return PropertyAccessors.forField(field).get(entity);
    }

    public void setValue(Object entity, Object value) {
        if (value == null && field.getType().isPrimitive()) {
            return;
        }
        PropertyAccessors.forField(field).set(entity, value);
    }

    public Field getField() {
        return field;
    }

    public void setField(Field field) {
        this.field = field;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public void setJavaType(Class<?> javaType) {
        this.javaType = javaType;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isPrimaryId() {
        return primaryId;
    }

    public void setPrimaryId(boolean primaryId) {
        this.primaryId = primaryId;
    }

    public GenerationType getGenerationType() {
        return generationType;
    }

    public void setGenerationType(GenerationType generationType) {
        this.generationType = generationType;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    public void setAutoIncrement(boolean autoIncrement) {
        this.autoIncrement = autoIncrement;
    }

    public boolean isAutoIncrementOrIdentity() {
        return autoIncrement || generationType == GenerationType.IDENTITY;
    }

    public boolean isDocumentId() {
        return documentId;
    }

    public void setDocumentId(boolean documentId) {
        this.documentId = documentId;
    }

    public IndexSpec getIndexSpec() {
        return indexSpec;
    }

    public void setIndexSpec(IndexSpec indexSpec) {
        this.indexSpec = indexSpec;
    }

    public MongoIndexSpec getMongoIndexSpec() {
        return mongoIndexSpec;
    }

    public void setMongoIndexSpec(MongoIndexSpec mongoIndexSpec) {
        this.mongoIndexSpec = mongoIndexSpec;
    }

    public static final class IndexSpec {

        private final IndexType type;
        private final String name;

        public IndexSpec(IndexType type, String name) {
            this.type = type;
            this.name = name;
        }

        public IndexType getType() {
            return type;
        }

        public String getName() {
            return name;
        }
    }

    public static final class MongoIndexSpec {

        private final IndexDirection direction;
        private final boolean unique;
        private final boolean sparse;

        public MongoIndexSpec(IndexDirection direction, boolean unique, boolean sparse) {
            this.direction = direction;
            this.unique = unique;
            this.sparse = sparse;
        }

        public IndexDirection getDirection() {
            return direction;
        }

        public boolean isUnique() {
            return unique;
        }

        public boolean isSparse() {
            return sparse;
        }
    }
}
