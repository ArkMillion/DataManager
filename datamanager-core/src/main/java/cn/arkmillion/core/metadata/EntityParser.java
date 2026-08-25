package cn.arkmillion.core.metadata;

import cn.arkmillion.core.annotation.AutoIncrement;
import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Document;
import cn.arkmillion.core.annotation.DocumentId;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Index;
import cn.arkmillion.core.annotation.Indexed;
import cn.arkmillion.core.annotation.Table;
import cn.arkmillion.core.enums.DataType;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.IndexDirection;
import cn.arkmillion.core.enums.IndexType;
import cn.arkmillion.core.exception.DataManagerException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityParser {

    private static final Map<Class<?>, SchemaDefinition> CACHE = new ConcurrentHashMap<>();

    private EntityParser() {
    }

    public static SchemaDefinition parse(Class<?> entityClass) {
        SchemaDefinition cached = CACHE.get(entityClass);
        if (cached != null) {
            return cached;
        }
        SchemaDefinition definition = doParse(entityClass);
        SchemaDefinition existing = CACHE.putIfAbsent(entityClass, definition);
        return existing == null ? definition : existing;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static SchemaDefinition doParse(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        Document document = entityClass.getAnnotation(Document.class);
        if (table != null) {
            return parseRelational(entityClass, table);
        }
        if (document != null) {
            return parseDocumentEntity(entityClass, document);
        }
        throw new DataManagerException("Entity " + entityClass.getName() + " must be annotated with @Table or @Document");
    }

    private static SchemaDefinition parseRelational(Class<?> entityClass, Table table) {
        SchemaDefinition def = new SchemaDefinition(entityClass);
        def.setTableName(table.name());
        def.setEngine(table.engine());
        def.setCharset(table.charset());
        def.setComment(table.comment());

        int idCount = 0;
        for (Field field : collectFields(entityClass)) {
            if (!isPersistent(field)) {
                continue;
            }
            ColumnMetadata cm = buildColumn(field);
            Column ann = field.getAnnotation(Column.class);
            if (ann != null && !ann.name().isEmpty()) {
                cm.setColumnName(ann.name());
            } else {
                cm.setColumnName(camelToSnake(field.getName()));
            }
            DataType type = ann == null ? DataType.AUTO : ann.type();
            cm.setDataType(type == DataType.AUTO ? inferDataType(field.getType()) : type);
            if (ann != null) {
                cm.setLength(ann.length());
                cm.setPrecision(ann.precision());
                cm.setScale(ann.scale());
                cm.setNullable(ann.nullable());
                cm.setUnique(ann.unique());
                cm.setDefaultValue(ann.defaultValue());
                cm.setComment(ann.comment());
            }
            Id id = field.getAnnotation(Id.class);
            if (id != null) {
                idCount++;
                cm.setPrimaryId(true);
                cm.setGenerationType(id.strategy() == GenerationType.AUTO ? GenerationType.IDENTITY : id.strategy());
            }
            if (field.isAnnotationPresent(AutoIncrement.class)) {
                cm.setAutoIncrement(true);
                if (!cm.isPrimaryId()) {
                    cm.setGenerationType(GenerationType.IDENTITY);
                }
            }
            Index index = field.getAnnotation(Index.class);
            if (index != null || cm.isUnique()) {
                IndexType indexType = index != null ? index.type() : IndexType.UNIQUE;
                String indexName = index != null && !index.name().isEmpty()
                        ? index.name()
                        : (cm.isUnique() ? "uk_" : "idx_") + table.name() + "_" + cm.getColumnName();
                cm.setIndexSpec(new ColumnMetadata.IndexSpec(indexType, indexName));
                boolean unique = cm.isUnique() || indexType == IndexType.UNIQUE;
                List<String> cols = new ArrayList<>();
                cols.add(cm.getColumnName());
                def.getIndexes().add(new IndexMetadata(indexName, cols, unique, unique ? IndexType.UNIQUE : indexType));
            }
            def.getColumns().add(cm);
        }
        if (idCount > 1) {
            throw new DataManagerException("Entity " + entityClass.getName() + " declares more than one @Id field");
        }
        return def;
    }

    private static SchemaDefinition parseDocumentEntity(Class<?> entityClass, Document document) {
        SchemaDefinition def = new SchemaDefinition(entityClass);
        def.setDocument(true);
        def.setCollectionName(document.collection());
        def.setDatabaseName(document.database());

        for (Field field : collectFields(entityClass)) {
            if (!isPersistent(field)) {
                continue;
            }
            ColumnMetadata cm = new ColumnMetadata();
            cm.setField(field);
            field.setAccessible(true);
            cm.setPropertyName(field.getName());
            cm.setJavaType(field.getType());
            DocumentId documentId = field.getAnnotation(DocumentId.class);
            if (documentId != null || "id".equals(field.getName())) {
                cm.setDocumentId(true);
                cm.setColumnName(documentId != null && !documentId.fieldName().isEmpty() ? documentId.fieldName() : "_id");
            } else {
                cm.setColumnName(field.getName());
            }
            Indexed indexed = field.getAnnotation(Indexed.class);
            if (indexed != null) {
                IndexDirection dir = indexed.direction();
                cm.setMongoIndexSpec(new ColumnMetadata.MongoIndexSpec(dir, indexed.unique(), indexed.sparse()));
            }
            def.getColumns().add(cm);
        }
        return def;
    }

    private static ColumnMetadata buildColumn(Field field) {
        ColumnMetadata cm = new ColumnMetadata();
        cm.setField(field);
        field.setAccessible(true);
        cm.setPropertyName(field.getName());
        cm.setJavaType(field.getType());
        return cm;
    }

    private static List<Field> collectFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                fields.add(f);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static boolean isPersistent(Field field) {
        int mods = field.getModifiers();
        return !Modifier.isStatic(mods)
                && !Modifier.isTransient(mods)
                && !"this$0".equals(field.getName())
                && !"this$1".equals(field.getName());
    }

    public static DataType inferDataType(Class<?> type) {
        if (type == String.class || type.isEnum()) {
            return DataType.VARCHAR;
        }
        if (type == Integer.class || type == int.class) {
            return DataType.INT;
        }
        if (type == Short.class || type == short.class) {
            return DataType.SMALLINT;
        }
        if (type == Byte.class || type == byte.class) {
            return DataType.TINYINT;
        }
        if (type == Long.class || type == long.class) {
            return DataType.BIGINT;
        }
        if (type == Double.class || type == double.class) {
            return DataType.DOUBLE;
        }
        if (type == Float.class || type == float.class) {
            return DataType.FLOAT;
        }
        if (type == BigDecimal.class) {
            return DataType.DECIMAL;
        }
        if (type == Boolean.class || type == boolean.class) {
            return DataType.BOOLEAN;
        }
        if (type == byte[].class) {
            return DataType.BLOB;
        }
        if (type == LocalDate.class) {
            return DataType.DATE;
        }
        if (type == LocalTime.class) {
            return DataType.TIME;
        }
        if (type == LocalDateTime.class) {
            return DataType.DATETIME;
        }
        if (type == Instant.class || type == Date.class) {
            return DataType.TIMESTAMP;
        }
        return DataType.VARCHAR;
    }

    public static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
