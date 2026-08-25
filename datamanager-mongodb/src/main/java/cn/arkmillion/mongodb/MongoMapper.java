package cn.arkmillion.mongodb;

import cn.arkmillion.core.annotation.Document;
import cn.arkmillion.core.enums.IndexDirection;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.metadata.ColumnMetadata;
import cn.arkmillion.core.metadata.EntityParser;
import cn.arkmillion.core.metadata.SchemaDefinition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.types.ObjectId;

public final class MongoMapper {

    private final ObjectMapper mapper;

    public MongoMapper() {
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }

    public SchemaDefinition parse(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Document.class)) {
            throw new DataManagerException("Entity must be annotated with @Document: " + clazz.getName());
        }
        return EntityParser.parse(clazz);
    }

    public <T> org.bson.Document toBson(T entity, SchemaDefinition def) {
        org.bson.Document doc = mapper.convertValue(entity, org.bson.Document.class);
        ColumnMetadata idColumn = findIdColumn(def);
        if (idColumn != null) {
            Object idValue = doc.remove(idColumn.getPropertyName());
            String idFieldName = idColumn.getColumnName();
            if (idValue == null && idColumn.getJavaType() == String.class) {
                idValue = new ObjectId().toHexString();
            }
            doc.put(idFieldName, idValue);
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    public <T> T writeBackId(T entity, SchemaDefinition def, org.bson.Document stored) {
        ColumnMetadata idColumn = findIdColumn(def);
        if (idColumn == null) {
            return entity;
        }
        Object id = stored.get(idColumn.getColumnName());
        if (id != null) {
            Object converted = convertId(id, idColumn.getJavaType());
            idColumn.setValue(entity, converted);
        }
        return entity;
    }

    private Object convertId(Object raw, Class<?> targetType) {
        if (raw instanceof ObjectId) {
            return ((ObjectId) raw).toHexString();
        }
        if (targetType == String.class) {
            return raw.toString();
        }
        return raw;
    }

    public <T> T fromBson(Class<T> clazz, SchemaDefinition def, org.bson.Document doc) {
        org.bson.Document copy = new org.bson.Document(doc);
        ColumnMetadata idColumn = findIdColumn(def);
        if (idColumn != null) {
            Object id = copy.remove(idColumn.getColumnName());
            if (id != null) {
                copy.put(idColumn.getPropertyName(), convertId(id, idColumn.getJavaType()));
            }
        }
        return mapper.convertValue(copy, clazz);
    }

    private static ColumnMetadata findIdColumn(SchemaDefinition def) {
        for (ColumnMetadata c : def.getColumns()) {
            if (c.isDocumentId()) {
                return c;
            }
        }
        return null;
    }

    public static int sortDirection(IndexDirection direction) {
        return direction == IndexDirection.DESC ? -1 : 1;
    }

    public ObjectMapper objectMapper() {
        return mapper;
    }
}
